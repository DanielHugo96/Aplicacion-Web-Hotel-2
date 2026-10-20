"""Simulacros RB1: DLT Kafka, DLQ Rabbit y jobs de notification."""

from __future__ import annotations

import json
import time
import uuid
from datetime import date, timedelta
from typing import Any

from . import common as c
from . import fixtures as f

DEST = "hotel.habitacion.v1"
GROUP = "reception-gates-v1"
DLT = f"{DEST}.{GROUP}.dlt"


def _invalid_lista(event_id: str) -> dict[str, Any]:
    """Envelope válido, payload que no cumple HabitacionLista (faltan claves obligatorias)."""
    return {
        "eventId": event_id,
        "type": "HabitacionLista",
        "schemaVersion": 1,
        "aggregateType": "habitacion",
        "aggregateId": "101",
        "aggregateVersion": 7,
        "idHotel": 1,
        "occurredAt": c.iso(),
        "correlationId": str(uuid.uuid4()),
        "payload": {
            "idHabitacion": 101,
            "idHotel": 1,
            "numero": "101",
            "idCategoria": 1,
            "categoriaNombre": "Individual",
            "pisoNombre": "Primer piso",
            "precio": 70.00,
            "estado": True,
            "estadoFisico": "LISTA",
            "version": 7,
            "blockId": None,
        },
    }


def _valid_lista(event_id: str, cycle: str) -> dict[str, Any]:
    return {
        "eventId": event_id,
        "type": "HabitacionLista",
        "schemaVersion": 1,
        "aggregateType": "habitacion",
        "aggregateId": "101",
        "aggregateVersion": 8,
        "idHotel": 1,
        "occurredAt": c.iso(),
        "correlationId": str(uuid.uuid4()),
        "payload": {
            "idHabitacion": 101,
            "idHotel": 1,
            "numero": "101",
            "idCategoria": 1,
            "categoriaNombre": "Individual",
            "pisoNombre": "Primer piso",
            "precio": 70.00,
            "estado": True,
            "estadoFisico": "LISTA",
            "version": 8,
            "cleaningCycleId": cycle,
            "cleaningRoomVersion": 1,
            "blockId": None,
        },
    }


def kafka() -> None:
    sim = "rb1-kafka"
    c.runbook_step("RB1-Kafka: mensaje inválido hasta DLT")
    before_dlt, before_detail = c.kafka_dlt_counts()
    gate_before = c.psql("reception_db", "select id_habitacion, estado, room_version, COALESCE(cleaning_cycle_id::text,'') from room_gate where id_habitacion=101")
    event_id = str(uuid.uuid4())
    message = _invalid_lista(event_id)
    c.write_json(sim, "01-antes.json", {"dltTotal": before_dlt, "dlt": before_detail, "roomGate101": gate_before})
    c.write_json(sim, "02-mensaje-invalido.json", {"key": "101", "topic": DEST, "mensaje": message})

    c.kafka_publish(DEST, "101", message)
    started = time.time()
    c.wait_until(lambda: c.kafka_end_offset(DLT) >= before_dlt + 1, timeout=660, interval=10, description="mensaje en el .dlt")
    elapsed = int(time.time() - started)
    after_dlt, after_detail = c.kafka_dlt_counts()
    consumed = c.kafka_consume(DLT, max_messages=10, timeout_ms=4000)
    gate_after = c.psql("reception_db", "select id_habitacion, estado, room_version, COALESCE(cleaning_cycle_id::text,'') from room_gate where id_habitacion=101")
    c.write_json(
        sim,
        "03-dlt-despues.json",
        {
            "esperaSegundos": elapsed,
            "dltTotal": after_dlt,
            "dlt": after_detail,
            "mensajes": consumed,
            "envelopeConservado": any(event_id in item for item in consumed),
            "roomGateDespues": gate_after,
            "sinEfecto": gate_before == gate_after,
        },
    )
    log_path = c.RUNTIME / "reception-service.log"
    if log_path.exists():
        lines = [
            line
            for line in log_path.read_text(errors="replace").splitlines()
            if event_id in line or "gate_consumer_error" in line
        ][-40:]
        c.write_evidence(sim, "04-log-consumidor.txt", "\n".join(lines) + "\n")

    # Reenvío con el mismo eventId y key de un evento ya procesado: el inbox no duplica.
    valid_cycle = str(uuid.uuid4())
    valid = _valid_lista(str(uuid.uuid4()), valid_cycle)
    inbox_before = f.inbox_rows("reception_db", GROUP, valid["eventId"])
    c.write_json(sim, "05-inbox-antes.json", {"eventId": valid["eventId"], "filas": inbox_before})
    c.kafka_publish(DEST, "101", valid)
    c.wait_until(
        lambda: len(f.inbox_rows("reception_db", GROUP, valid["eventId"])) == 1,
        timeout=90,
        interval=3,
        description="inbox del evento válido",
    )
    first = f.inbox_rows("reception_db", GROUP, valid["eventId"])
    c.kafka_publish(DEST, "101", valid)
    time.sleep(8)
    second = f.inbox_rows("reception_db", GROUP, valid["eventId"])
    c.write_json(
        sim,
        "06-reenvio-mismo-eventid.json",
        {"primeraVez": first, "trasReenvio": second, "filas": len(second), "sinDuplicar": len(second) == 1},
    )

    # Descarte del mensaje de prueba: se registra el contenido y se borran los topics DLT.
    dlt_topics = [topic for topic in c.kafka_topics() if topic.endswith(".dlt")]
    c.write_json(
        sim,
        "07-descarte-dlt.json",
        {
            "topics": dlt_topics,
            "mensajesDescartados": consumed,
            "accion": "kafka-topics.sh --delete",
        },
    )
    for topic in dlt_topics:
        c.kafka_delete_topic(topic)
    c.wait_until(lambda: not any(t.endswith(".dlt") for t in c.kafka_topics()), timeout=60, interval=3, description="topics DLT eliminados")
    print(f"  DLT alcanzada en {elapsed}s; mensaje conservado y topic descartado")


def rabbit() -> None:
    sim = "rb1-rabbit"
    c.runbook_step("RB1-Rabbit: F8 y comandos a la DLQ")
    # F8: estadía cerrada con cleaningCycleId C1. La habitación 101 debe estar libre.
    token = f.admin_token()
    f.free_room(101, 1)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=20.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"check-in F8 devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    c.write_json(sim, "01-fixture-f8.json", {"idRecepcion": id_recepcion, "checkin": check.summary()})
    out = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=50.0, token=token)
    if out.status != 200:
        raise c.RunbookError(f"cierre F8 devolvió {out.status}: {out.body[:400]}")
    cycle = c.psql_one("reception_db", f"select cleaning_cycle_id::text from cierre where id_recepcion={id_recepcion} and estado='COMPLETADO'")
    c.wait_until(
        lambda: c.psql_one("hotel_db", f"select count(*) from limpieza where cleaning_cycle_id='{cycle}'") == "1",
        timeout=60,
        interval=3,
        description="limpieza C1 en hotel",
    )
    task_before = c.psql("hotel_db", "select cleaning_cycle_id::text, id_habitacion, id_recepcion, estado from limpieza where cleaning_cycle_id='" + cycle + "'")
    c.write_json(sim, "02-f8-cerrada.json", {"idRecepcion": id_recepcion, "cleaningCycleId": cycle, "checkout": out.summary(), "limpieza": task_before})

    limpieza_before = c.psql("hotel_db", "select id, id_estado_habitacion, version from habitacion where id=102")
    room102_before = f.room(102, token)
    dlq_before, _ = c.rabbit_dlq_counts()

    # Contradicción permanente: C1 es de la habitación 101 y el comando dice 102.
    command_id = str(uuid.uuid4())
    contradiction = {
        "commandId": command_id,
        "type": "LimpiezaSolicitada",
        "schemaVersion": 1,
        "idHotel": 1,
        "correlationId": str(uuid.uuid4()),
        "issuedAt": c.iso(),
        "payload": {
            "idHabitacion": 102,
            "idRecepcion": id_recepcion,
            "cleaningCycleId": cycle,
            "roomVersion": 1,
        },
    }
    publish = c.rabbit_publish("hotel.commands.v1", "limpieza.solicitada", contradiction, command_id=command_id)
    c.write_json(sim, "03-comando-contradiccion.json", {"publish": publish.summary(), "comando": contradiction})
    c.wait_until(lambda: c.rabbit_queue("limpieza.solicitada.dlq")["messages"] >= dlq_before + 1, timeout=60, interval=3, description="contradicción en DLQ")

    invalid_id = str(uuid.uuid4())
    invalid = {
        "commandId": invalid_id,
        "type": "LimpiezaSolicitada",
        "schemaVersion": 1,
        "idHotel": 1,
        "correlationId": str(uuid.uuid4()),
        "issuedAt": c.iso(),
        "payload": {
            "idHabitacion": 102,
            "idRecepcion": id_recepcion,
            "cleaningCycleId": "no-es-uuid",
        },
    }
    publish_invalid = c.rabbit_publish("hotel.commands.v1", "limpieza.solicitada", invalid, command_id=invalid_id)
    c.write_json(sim, "04-comando-schema-invalido.json", {"publish": publish_invalid.summary(), "comando": invalid})
    c.wait_until(lambda: c.rabbit_queue("limpieza.solicitada.dlq")["messages"] >= dlq_before + 2, timeout=60, interval=3, description="segundo mensaje en DLQ")

    queues = c.rabbit_queues()
    dlq_after, _ = c.rabbit_dlq_counts()
    retries = {name: q["messages"] for name, q in queues.items() if name.startswith("limpieza.solicitada.retry.")}
    limpieza_after = c.psql("hotel_db", "select cleaning_cycle_id::text, id_habitacion, id_recepcion, estado from limpieza where id_habitacion=102")
    room102_after = f.room(102, token)
    c.write_json(
        sim,
        "05-dlq-y-sin-efecto.json",
        {
            "dlqAntes": dlq_before,
            "dlqDespues": dlq_after,
            "retryQueues": retries,
            "limpieza102": limpieza_after,
            "limpieza102Antes": [],
            "habitacion102Antes": {"idEstadoHabitacion": limpieza_before, "version": room102_before.get("version"), "estadoFisico": room102_before.get("estadoFisico")},
            "habitacion102Despues": {"version": room102_after.get("version"), "estadoFisico": room102_after.get("estadoFisico")},
            "sinTareaNueva": not limpieza_after,
            "sinCambioFisico": room102_before.get("estadoFisico") == room102_after.get("estadoFisico"),
        },
    )

    # Lectura y descarte de los mensajes de prueba de la DLQ.
    messages = c.rabbit_get("limpieza.solicitada.dlq", dlq_after)
    c.write_json(sim, "06-dlq-mensajes.json", {"mensajes": messages})
    c.rabbit_purge("limpieza.solicitada.dlq")
    c.write_json(
        sim,
        "07-descarte-dlq.json",
        {
            "descarte": "management API: ack_requeue_false + purge",
            "commandIds": [command_id, invalid_id],
            "dlqRestante": c.rabbit_queue("limpieza.solicitada.dlq")["messages"],
        },
    )
    print("  contradicción y schema inválido en la DLQ; sin tarea ni cambio físico")


def _notification_cli(*args: str, timeout: int = 240) -> tuple[int, str]:
    """Ejecuta la CLI `--admin` con el mismo arranque Maven que el servicio.

    Los jars del proyecto no son ejecutables (`java -jar` no encuentra Main-Class);
    el arranque canónico es `spring-boot:run`, igual que para los servicios.
    """
    argumentos = "--admin " + " ".join(args)
    proc = c.run(
        [
            c.bash_executable(),
            "scripts/mvn.sh",
            "-f",
            "services/pom.xml",
            "-pl",
            "notification-service",
            "spring-boot:run",
            "-q",
            f"-Dspring-boot.run.arguments={argumentos}",
        ],
        env_extra=c.service_env("notification-service"),
        timeout=timeout,
        check=False,
    )
    lineas = [line for line in proc.stdout.splitlines() if line and not line.startswith("{")]
    return proc.returncode, "\n".join(lineas)


def notification() -> None:
    sim = "rb1-notificacion"
    c.runbook_step("RB1-Notification: jobs ERROR y CLI del operador")
    token = f.admin_token()
    c.docker("stop", c.MAILPIT, timeout=60, check=False)
    time.sleep(3)
    stays: list[dict[str, Any]] = []
    for room_id, hotel_id, cliente, adelanto in [(202, 2, 100, 0.0), (301, 3, 101, 0.0)]:
        f.free_room(room_id, hotel_id)
        check, stay = f.checkin(room_id=room_id, hotel_id=hotel_id, cliente=cliente, adelanto=adelanto, token=token)
        if check.status != 201:
            raise c.RunbookError(f"check-in {room_id} devolvió {check.status}: {check.body[:400]}")
        id_recepcion = int(stay["idRecepcion"])
        amount = float(f.room(room_id, token).get("precio", 0)) - adelanto
        out = f.checkout(id_recepcion=id_recepcion, room_id=room_id, monto=amount, token=token)
        gate = c.psql("reception_db", f"select estado, COALESCE(cleaning_cycle_id::text,'') from room_gate where id_habitacion={room_id}")
        stays.append(
            {
                "room": room_id,
                "idRecepcion": id_recepcion,
                "checkoutStatus": out.status,
                "gate": gate,
                "checkout": out.summary(),
            }
        )
    c.write_json(sim, "01-dos-estadias-cerradas.json", {"mailpit": "detenido", "estadias": stays})
    if any(s["checkoutStatus"] != 200 for s in stays):
        raise c.RunbookError(f"un cierre no devolvió 200: {stays}")

    c.wait_until(lambda: len([j for j in f.notification_jobs() if j["tipo"] == "CHECKOUT"]) >= 2, timeout=90, interval=3, description="dos jobs de checkout")
    jobs = [j for j in f.notification_jobs() if j["tipo"] == "CHECKOUT"]
    c.write_json(sim, "02-jobs-creados.json", {"jobs": jobs})

    timeline: list[dict[str, Any]] = []
    started = time.time()
    inicio = c.iso()
    deadline = started + 60 * 45
    while time.time() < deadline:
        current = [j for j in f.notification_jobs() if j["tipo"] == "CHECKOUT"]
        timeline.append({"ts": c.iso(), "jobs": current})
        if current and all(j["estado"] == "ERROR" for j in current):
            break
        time.sleep(20)
    c.write_json(sim, "03-timeline-reintentos.json", {"inicio": inicio, "linea": timeline})
    errors = [j for j in f.notification_jobs() if j["estado"] == "ERROR" and j["tipo"] == "CHECKOUT"]
    if len(errors) < 2:
        raise c.RunbookError(f"los jobs no llegaron a ERROR: {f.notification_jobs()}")
    c.write_json(sim, "04-jobs-error.json", {"jobs": errors, "esperaSegundos": int(time.time() - started)})
    alert_before = c.psql_one("notification_db", "select count(*) from notification_job where estado='ERROR'")

    # Purga con los dos jobs en ERROR: no debe tocar ERROR ni PENDIENTE.
    purge_antes_exit, purge_antes_out = _notification_cli("purge", "--before", (date.today() + timedelta(days=1)).isoformat())
    jobs_tras_purga = f.notification_jobs()
    c.write_evidence(sim, "05-cli-purge-con-error.txt", f"exit={purge_antes_exit}\n{purge_antes_out}\n")
    c.write_json(sim, "06-estado-tras-purga.json", {"jobs": jobs_tras_purga, "errorAntes": alert_before})

    c.docker("start", c.MAILPIT, timeout=60, check=True)
    c.wait_until(lambda: c.http("GET", "http://localhost:8025/api/v1/messages", base="").status == 200, timeout=60, interval=3, description="Mailpit arriba")

    list_exit, list_out = _notification_cli("list", "--estado", "ERROR")
    c.write_evidence(sim, "07-cli-list.txt", f"exit={list_exit}\n{list_out}\n")

    retry_job, discard_job = errors[0], errors[1]
    retry_exit, retry_out = _notification_cli("retry", retry_job["commandId"])
    c.write_evidence(sim, "08-cli-retry.txt", f"exit={retry_exit}\n{retry_out}\n")
    f.wait_notification_job(retry_job["commandId"], "ENVIADA", timeout=180)
    enviada = [j for j in f.notification_jobs() if j["commandId"] == retry_job["commandId"]][0]
    # Los CHECKIN del mismo simulacro también están en ERROR: se reintentan para que la
    # alerta de jobs ERROR quede apagada al terminar (no forman parte del caso NOT01).
    for extra in [j for j in f.notification_jobs() if j["estado"] == "ERROR" and j["tipo"] == "CHECKIN"]:
        extra_exit, extra_out = _notification_cli("retry", extra["commandId"])
        c.write_evidence(sim, f"08b-cli-retry-{extra['commandId'][:8]}.txt", f"exit={extra_exit}\n{extra_out}\n")
        f.wait_notification_job(extra["commandId"], "ENVIADA", timeout=180)
    mailbox = c.http("GET", "http://localhost:8025/api/v1/messages?limit=20", base="").json

    discard_exit, discard_out = _notification_cli("discard", discard_job["commandId"], "--motivo=descarte del simulacro RB1-Notification")
    c.write_evidence(sim, "09-cli-discard.txt", f"exit={discard_exit}\n{discard_out}\n")
    descartado = [j for j in f.notification_jobs() if j["commandId"] == discard_job["commandId"]][0]

    purge_exit, purge_out = _notification_cli("purge", "--before", (date.today() + timedelta(days=1)).isoformat())
    c.write_evidence(sim, "10-cli-purge.txt", f"exit={purge_exit}\n{purge_out}\n")
    audit = c.psql(
        "notification_db",
        "select action, resource_id, actor, occurred_at::text from audit_log "
        "where action in ('NOTIFICATION_RETRY','NOTIFICATION_DISCARD','NOTIFICATION_PURGE') order by occurred_at",
    )
    c.write_json(
        sim,
        "11-recuperacion.json",
        {
            "reintentado": enviada,
            "descartado": descartado,
            "mailpit": mailbox,
            "auditLog": [{"accion": r[0], "entidad": r[1], "actor": r[2], "createdAt": r[3]} for r in audit],            "errorRestante": c.psql_one("notification_db", "select count(*) from notification_job where estado='ERROR'"),
            "pendienteRestante": c.psql_one("notification_db", "select count(*) from notification_job where estado='PENDIENTE'"),
        },
    )
    print("  dos jobs ERROR, retry ENVIADA, discard DESCARTADO y purga auditada")
