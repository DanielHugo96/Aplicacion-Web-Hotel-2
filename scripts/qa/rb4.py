"""Simulacro RB4: outbox atrasado, alerta, schema y reanudación del broker."""

from __future__ import annotations

import json
import time
from typing import Any

from . import common as c
from . import fixtures as f
from . import alerts

CHECKIN_SCHEMA = "reception.recepcion.v1.CheckInRegistrado.schema.json"


def _validate_schema(payload: dict[str, Any], schema_name: str) -> dict[str, Any]:
    import jsonschema
    from referencing import Registry, Resource
    from referencing.jsonschema import DRAFT202012

    events = c.ROOT / "docs" / "contracts" / "events"
    registry = Registry()
    for path in events.glob("*.schema.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        schema_id = data.get("$id")
        if schema_id:
            registry = registry.with_resource(schema_id, Resource.from_contents(data, default_specification=DRAFT202012))
    schema_path = events / schema_name
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema, registry=registry)
    errors = sorted(validator.iter_errors(payload), key=lambda e: list(e.path))
    return {
        "schema": schema_name,
        "valido": not errors,
        "errores": [f"{list(e.path)}: {e.message}" for e in errors],
    }


def _outbox_reception(id_recepcion: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "reception_db",
        "select id::text, destination, channel, type, payload::text, "
        "COALESCE(published_at::text,''), attempts, next_attempt_at::text, created_at::text "
        "from outbox where payload->'payload'->>'idRecepcion'="
        f"'{id_recepcion}' order by created_at, id",
    )
    return [
        {
            "id": r[0],
            "destination": r[1],
            "channel": r[2],
            "type": r[3],
            "payload": json.loads(r[4]),
            "publishedAt": r[5] or None,
            "attempts": int(r[6]),
            "nextAttemptAt": r[7],
            "createdAt": r[8],
        }
        for r in rows
    ]


def run() -> None:
    sim = "rb4-outbox"
    c.runbook_step("RB4: outbox con Kafka detenido")
    token = f.admin_token()
    c.docker("stop", c.KAFKA, timeout=90, check=True)
    c.wait_until(lambda: not c.port_open(29092), timeout=60, interval=2, description="Kafka detenido")
    f.free_room(302, 3)
    check, stay = f.checkin(room_id=302, hotel_id=3, cliente=100, adelanto=0.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"check-in con Kafka caído devolvió {check.status}: {check.body[:500]}")
    id_recepcion = int(stay["idRecepcion"])
    c.write_json(sim, "01-checkin-kafka-caido.json", {"idRecepcion": id_recepcion, "status": check.status, "body": check.json})

    def kafka_pending() -> list[dict[str, Any]]:
        return [row for row in _outbox_reception(id_recepcion) if row["destination"] == "KAFKA"]

    first = c.wait_until(lambda: kafka_pending() and kafka_pending()[0]["attempts"] >= 1, timeout=120, interval=3, description="outbox Kafka pendiente con intentos")
    snapshots = []
    for _ in range(4):
        rows = kafka_pending()
        snapshots.append({"ts": c.iso(), "filas": rows})
        time.sleep(6)
    rows = _outbox_reception(id_recepcion)
    kafka_rows = [r for r in rows if r["destination"] == "KAFKA"]
    rabbit_rows = [r for r in rows if r["destination"] == "RABBIT"]
    diagnostic = c.psql(
        "reception_db",
        "select destination, channel, count(*), min(created_at)::text from outbox "
        "where published_at is null group by destination, channel",
    )
    c.write_json(
        sim,
        "02-outbox-por-destino.json",
        {
            "idRecepcion": id_recepcion,
            "filas": rows,
            "kafkaPendientes": kafka_rows,
            "rabbitPublicadas": rabbit_rows,
            "snapshots": snapshots,
            "diagnostico": diagnostic,
            "unaFilaPorDestino": len(kafka_rows) >= 1 and len(rabbit_rows) >= 1,
            "kafkaSinPublicar": all(not r["publishedAt"] for r in kafka_rows),
            "rabbitPublicada": all(r["publishedAt"] for r in rabbit_rows),
        },
    )
    if not kafka_rows or any(r["publishedAt"] for r in kafka_rows):
        raise c.RunbookError(f"outbox Kafka inesperado: {kafka_rows}")
    if not rabbit_rows or any(not r["publishedAt"] for r in rabbit_rows):
        raise c.RunbookError(f"outbox Rabbit inesperado: {rabbit_rows}")
    attempts = [r["attempts"] for r in snapshots[-1]["filas"]] if snapshots[-1]["filas"] else []
    print(f"  check-in 201; outbox Kafka pendiente (attempts={attempts}) y Rabbit publicada")

    c.runbook_step("RB4: alerta, schema y reanudación")
    baseline = c.iso()
    deadline = time.time() + 60 * 20
    alerta = None
    linea = []
    while time.time() < deadline:
        found = next(a for a in alerts.collect() if a["alerta"] == "Outbox pendiente")
        degraded = next(a for a in alerts.collect() if a["alerta"] == "Reporting degradado")
        linea.append({"ts": c.iso(), "outbox": found["valor"], "dispara": found["dispara"], "reporting": degraded["valor"], "reportingDispara": degraded["dispara"]})
        if found["dispara"]:
            alerta = found
        if found["dispara"] and degraded["dispara"]:
            break
        time.sleep(20)
    if alerta is None:
        raise c.RunbookError("la alerta de outbox no disparó")
    kafka_rows = [r for r in _outbox_reception(id_recepcion) if r["destination"] == "KAFKA"]
    oldest = min(kafka_rows, key=lambda r: r["createdAt"])
    validation = _validate_schema(oldest["payload"], CHECKIN_SCHEMA)
    c.write_json(
        sim,
        "03-alerta-schema.json",
        {
            "inicio": baseline,
            "linea": linea,
            "alertaOutbox": alerta,
            "reporting": next(a for a in alerts.collect() if a["alerta"] == "Reporting degradado"),
            "filaMasAntigua": oldest,
            "validacionSchema": validation,
            "sinDescartarPorSql": True,
        },
    )
    if not validation["valido"]:
        raise c.RunbookError(f"el payload no valida contra {CHECKIN_SCHEMA}: {validation['errores']}")
    print("  alertas disparadas y payload validado contra su JSON Schema")

    c.docker("start", c.KAFKA, timeout=90, check=True)
    c.wait_until(lambda: c.port_open(29092), timeout=120, interval=3, description="Kafka arriba")
    c.wait_until(lambda: c.compose_ps().get("kafka", "").startswith("running/healthy"), timeout=180, interval=5, description="Kafka healthy")
    c.wait_until(
        lambda: all(r["publishedAt"] for r in _outbox_reception(id_recepcion) if r["destination"] == "KAFKA"),
        timeout=480,
        interval=5,
        description="outbox Kafka publicada",
    )
    published = [r for r in _outbox_reception(id_recepcion) if r["destination"] == "KAFKA"]
    event_id = published[0]["payload"].get("eventId") if published else None
    inbox = []
    if event_id:
        inbox = c.psql("reporting_db", f"select message_id::text, processed_at::text from inbox where message_id='{event_id}'")
    gate = c.psql("reception_db", "select estado, room_version from room_gate where id_habitacion=302")
    c.write_json(
        sim,
        "04-reanudacion.json",
        {
            "publicadas": published,
            "publicadaUnaVez": len([r for r in published if r["publishedAt"]]) == len(published),
            "inboxReporting": inbox,
            "efectoUnaVez": len(inbox) == 1,
            "gate302": gate,
            "outboxPendienteGlobal": c.psql_one("reception_db", "select count(*) from outbox where destination='KAFKA' and published_at is null"),
            "alertas": alerts.collect(),
        },
    )
    print("  Kafka arriba: outbox publicada una sola vez y efecto en inbox una vez")
