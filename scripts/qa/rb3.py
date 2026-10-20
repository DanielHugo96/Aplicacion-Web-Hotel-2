"""Simulacro RB3: operación de habitación PREPARANDO, alerta, recuperación y rechazo durable."""

from __future__ import annotations

import json
import time
import uuid
from typing import Any

from . import common as c
from . import fixtures as f
from . import alerts


def _operacion(block_id: str) -> dict[str, Any]:
    rows = c.psql(
        "hotel_db",
        "select block_id::text, id_habitacion, tipo, estado, attempts, next_attempt_at::text, "
        "request->>'requestedAt' "
        f"from operacion_habitacion where block_id='{block_id}'",
    )
    if not rows:
        return {}
    r = rows[0]
    return {
        "blockId": r[0],
        "idHabitacion": int(r[1]),
        "tipo": r[2],
        "estado": r[3],
        "attempts": int(r[4]),
        "nextAttemptAt": r[5],
        "requestedAt": r[6],
    }


def _bloqueo(block_id: str) -> dict[str, Any]:
    rows = c.psql(
        "reception_db",
        "select block_id::text, id_habitacion, id_hotel, estado, COALESCE(error_code,''), "
        "created_at::text, COALESCE(released_at::text,'') "
        f"from bloqueo_habitacion where block_id='{block_id}'",
    )
    if not rows:
        return {}
    r = rows[0]
    return {
        "blockId": r[0],
        "idHabitacion": int(r[1]),
        "idHotel": int(r[2]),
        "estado": r[3],
        "errorCode": r[4] or None,
        "createdAt": r[5],
        "releasedAt": r[6] or None,
    }


def _gate(room_id: int) -> dict[str, Any]:
    r = c.psql(
        "reception_db",
        "select estado, COALESCE(block_id::text,''), room_version from room_gate "
        f"where id_habitacion={room_id}",
    )[0]
    return {"estado": r[0], "blockId": r[1] or None, "roomVersion": int(r[2])}


def _fisico(room_id: int) -> dict[str, Any]:
    data = f.room(room_id)
    return {
        "idHabitacion": data.get("idHabitacion"),
        "version": data.get("version"),
        "estadoFisico": (data.get("estadoFisico") or {}).get("codigo"),
        "estado": data.get("estado"),
    }


def _create_sin_bloqueo() -> dict[str, Any]:
    sim = "rb3-operacion"
    token = f.admin_token()
    f.free_room(201, 2)
    before = {"operacion": [], "bloqueo": [], "gate": _gate(201), "fisico": _fisico(201)}
    c.stop_service("reception-service")
    time.sleep(2)
    out = f.maintenance(201, "Simulacro RB3", token=token)
    if out.status != 202:
        c.start_service("reception-service")
        raise c.RunbookError(f"mantenimiento sin reception devolvió {out.status}: {out.body[:500]}")
    block_id = str(out.field("data", "blockId") or "")
    if not block_id:
        c.start_service("reception-service")
        raise c.RunbookError(f"202 sin blockId: {out.body[:500]}")
    operacion = _operacion(block_id)
    bloqueo = _bloqueo(block_id)
    endpoint = c.http("GET", f"/api/habitacion/operaciones/{block_id}", token=token)
    mutation = f.maintenance(201, "Mutación concurrente", token=token)
    c.write_json(
        sim,
        "01-preparando-sin-bloqueo.json",
        {
            "status": out.status,
            "headers": c.redact_headers(out.headers),
            "body": out.json,
            "blockId": block_id,
            "operacion": operacion,
            "bloqueo": bloqueo,
            "sinFilaBloqueo": not bloqueo,
            "consulta": {"status": endpoint.status, "body": endpoint.json},
            "mutacionMismaHabitacion": {"status": mutation.status, "body": mutation.json},
            "antes": before,
        },
    )
    if operacion.get("estado") != "PREPARANDO" or bloqueo:
        c.start_service("reception-service")
        raise c.RunbookError(f"estado inesperado: {operacion} / {bloqueo}")
    if mutation.status != 409:
        c.start_service("reception-service")
        raise c.RunbookError(f"la mutación concurrente no devolvió 409: {mutation.status}")
    print(f"  operación {block_id} PREPARANDO sin fila de bloqueo; mutaciones 409")
    return {"blockId": block_id, "token": token}


def _persist_block(block_id: str) -> None:
    """Reproduce el commit de reception que hotel no llegó a ver: su misma llamada interna."""
    c.start_service("reception-service")
    blocked = c.http(
        "POST",
        "http://localhost:8083/internal/habitaciones/201/bloqueos",
        token=c.machine_token("hotel", "reception"),
        body={"blockId": block_id, "idHotel": 2, "motivo": "Simulacro RB3"},
    )
    if blocked.status != 200:
        raise c.RunbookError(f"bloqueo interno devolvió {blocked.status}: {blocked.body[:400]}")
    c.stop_service("reception-service")
    bloqueo = _bloqueo(block_id)
    c.write_json("rb3-operacion", "02-bloqueo-persistido.json", {"respuesta": blocked.summary(), "bloqueo": bloqueo})
    if bloqueo.get("estado") != "ACTIVO":
        raise c.RunbookError(f"el bloqueo no quedó ACTIVO: {bloqueo}")


def _hold_and_alert(ctx: dict[str, Any]) -> None:
    sim = "rb3-operacion"
    block_id = ctx["blockId"]
    operacion = _operacion(block_id)
    baseline_gate = _gate(201)
    baseline_fisico = _fisico(201)
    firing: list[dict[str, Any]] = []
    started = time.time()
    while time.time() - started < 660:
        found = next(a for a in alerts.collect() if a["alerta"] == "Operación de habitación")
        firing.append({"ts": c.iso(), "valor": found["valor"], "dispara": found["dispara"]})
        if found["dispara"]:
            break
        time.sleep(15)
    c.write_json(
        sim,
        "03-alerta-preparando.json",
        {
            "linea": firing,
            "alerta": found,
            "operacion": _operacion(block_id),
            "gateSinCambios": _gate(201) == baseline_gate,
            "fisicoSinCambios": _fisico(201) == baseline_fisico,
            "operacionInicial": operacion,
        },
    )
    if not found["dispara"]:
        raise c.RunbookError("la alerta de operación no disparó en 10min")


def _recover(ctx: dict[str, Any]) -> None:
    sim = "rb3-operacion"
    block_id = ctx["blockId"]
    c.start_service("reception-service")
    c.wait_until(
        lambda: _operacion(block_id).get("estado") == "APLICADA",
        timeout=360,
        interval=5,
        description="operación PREPARANDO -> APLICADA",
    )
    operacion = _operacion(block_id)
    gate = _gate(201)
    fisico = _fisico(201)
    c.write_json(
        sim,
        "04-recuperacion.json",
        {
            "operacion": operacion,
            "bloqueo": _bloqueo(block_id),
            "gate": gate,
            "fisico": fisico,
            "alerta": next(a for a in alerts.collect() if a["alerta"] == "Operación de habitación"),
        },
    )
    if gate.get("estado") != "BLOQUEADA" or fisico.get("estadoFisico") != "MANTENIMIENTO":
        raise c.RunbookError(f"recuperación incompleta: {gate} / {fisico}")
    print("  operación APLICADA, gate BLOQUEADA y habitación MANTENIMIENTO")


def _rejection() -> None:
    sim = "rb3-operacion"
    token = f.admin_token()
    f.free_room(201, 2)
    check, stay = f.checkin(room_id=201, hotel_id=2, cliente=101, adelanto=0.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"F6 check-in devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    key = str(uuid.uuid4())
    blocked = f.maintenance(201, "F6 rechazo durable", key=key, token=token)
    block_id = ""
    rows = c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion where id_habitacion=201 order by block_id desc limit 1")
    if rows:
        block_id = rows[0][0]
    rechazo = {
        "status": blocked.status,
        "body": blocked.json,
        "operacion": _operacion(block_id) if block_id else {},
        "bloqueo": _bloqueo(block_id) if block_id else {},
    }

    out = f.checkout(id_recepcion=id_recepcion, room_id=201, monto=80.0, token=token)
    if out.status != 200:
        raise c.RunbookError(f"cierre F6 devolvió {out.status}: {out.body[:400]}")
    f.free_room(201, 2)
    same = f.maintenance(201, "F6 rechazo durable", key=key, token=token)
    ops = c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion where id_habitacion=201")
    new_key = str(uuid.uuid4())
    nueva = f.maintenance(201, "Nueva intención F6", key=new_key, token=token)
    new_ops = c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion where id_habitacion=201")
    c.write_json(
        sim,
        "05-f6-rechazo-durable.json",
        {
            "idRecepcion": id_recepcion,
            "primerIntento": rechazo,
            "cierre": out.summary(),
            "reintentoMismaClave": {"status": same.status, "body": same.json},
            "operacionesTrasReintento": ops,
            "nuevaIntencion": {"status": nueva.status, "body": nueva.json},
            "operacionesTrasNueva": new_ops,
            "gate": _gate(201),
            "fisico": _fisico(201),
        },
    )
    if rechazo["status"] != 409 or same.status != 409 or nueva.status != 200:
        raise c.RunbookError(f"F6 incumplido: {rechazo['status']}/{same.status}/{nueva.status}")
    print("  F6: rechazo durable por blockId y nueva intención admitida")


def run() -> None:
    c.runbook_step("RB3: crear y diagnosticar")
    ctx = _create_sin_bloqueo()
    try:
        _persist_block(ctx["blockId"])
        c.runbook_step("RB3: alerta y recuperación")
        _hold_and_alert(ctx)
        _recover(ctx)
        c.runbook_step("RB3: rechazo durable F6")
        _rejection()
    finally:
        c.start_service("reception-service") if not c.port_open(8083) else None
