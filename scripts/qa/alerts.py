"""Las nueve alertas de docs/especificacion/infraestructura/runbooks.md § Alertas y umbrales.

La «detección» es la consulta/sonda reproducible que el operador ejecutaría; el runner
solo la automatiza. No inventa umbrales: los lee de la tabla de la especificación.
"""

from __future__ import annotations

import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import common as c

READINESS_STATE = c.RUNTIME / "readiness-alert.json"
READINESS_SERVICES = [
    "identity-service",
    "hotel-service",
    "reception-service",
    "inventory-service",
    "sales-service",
    "reporting-service",
    "notification-service",
]


def _readiness_probe() -> dict[str, Any]:
    previous: dict[str, str] = {}
    if READINESS_STATE.exists():
        try:
            previous = json.loads(READINESS_STATE.read_text())
        except Exception:
            previous = {}
    current: dict[str, Any] = {}
    firing = []
    now = time.time()
    for name in READINESS_SERVICES:
        port = c.SERVICES[name]["port"]
        resp = c.http("GET", f"http://localhost:{port}/actuator/health/readiness", base="", timeout=5)
        up = resp.status == 200 and '"UP"' in resp.body
        current[name] = {"up": up, "status": resp.status, "body": resp.body[:300]}
        if up:
            continue
        first = previous.get(name)
        if not first:
            first = c.iso()
        current[name]["first_failure"] = first
        try:
            started = datetime.fromisoformat(first.replace("Z", "+00:00"))
            elapsed = (datetime.now(timezone.utc) - started).total_seconds()
        except Exception:
            elapsed = 0
        current[name]["failure_seconds"] = int(elapsed)
        if elapsed > 60:
            firing.append(name)
    state = {name: data["first_failure"] for name, data in current.items() if not data["up"] and "first_failure" in data}
    READINESS_STATE.parent.mkdir(parents=True, exist_ok=True)
    READINESS_STATE.write_text(json.dumps(state, indent=2))
    return {"services": current, "firing": firing}


def _db_rows(db: str, sql: str) -> list[dict[str, Any]]:
    rows = c.psql(db, sql)
    return [{"columnas": r} for r in rows]


def collect() -> list[dict[str, Any]]:
    alerts: list[dict[str, Any]] = []

    # 1. Mensajes en DLT Kafka (Alta, RB1)
    dlt_total, dlt_detail = c.kafka_dlt_counts()
    alerts.append(
        {
            "alerta": "Mensajes en DLT Kafka",
            "umbral": "> 0",
            "deteccion": "conteo de offsets de los topics *.dlt",
            "severidad": "Alta",
            "runbook": "RB1",
            "valor": dlt_total,
            "dispara": dlt_total > 0,
            "detalle": dlt_detail,
        }
    )

    # 2. Mensajes en DLQ Rabbit (Alta, RB1)
    dlq_total, dlq_detail = c.rabbit_dlq_counts()
    alerts.append(
        {
            "alerta": "Mensajes en DLQ Rabbit",
            "umbral": "> 0",
            "deteccion": "management /api/queues, suma de q.dlq",
            "severidad": "Alta",
            "runbook": "RB1",
            "valor": dlq_total,
            "dispara": dlq_total > 0,
            "detalle": dlq_detail,
        }
    )

    # 3. Outbox pendiente (Alta, RB4)
    outbox_rows: dict[str, list[dict[str, Any]]] = {}
    for db in c.DATABASES:
        if not c.table_exists(db, "outbox"):
            continue
        rows = c.psql(
            db,
            "select id::text, type, attempts, created_at::text, next_attempt_at::text "
            "from outbox where published_at is null "
            "and (attempts > 10 or created_at < now() - interval '15 minutes') "
            "order by created_at",
        )
        if rows:
            outbox_rows[db] = [
                {"id": r[0], "type": r[1], "attempts": int(r[2]), "created_at": r[3], "next_attempt_at": r[4]}
                for r in rows
            ]
    alerts.append(
        {
            "alerta": "Outbox pendiente",
            "umbral": "> 15min o attempts > 10",
            "deteccion": "consulta a outbox por base",
            "severidad": "Alta",
            "runbook": "RB4",
            "valor": sum(len(v) for v in outbox_rows.values()),
            "dispara": bool(outbox_rows),
            "detalle": outbox_rows,
        }
    )

    # 4. Cierre atascado (Alta, RB2)
    cierre_rows = c.psql(
        "reception_db",
        "select closure_id::text, id_recepcion, request->>'requestedAt' "
        "from cierre where estado = 'CONFIRMANDO' "
        "and (request->>'requestedAt')::timestamptz < now() - interval '5 minutes'",
    )
    alerts.append(
        {
            "alerta": "Cierre atascado",
            "umbral": "CONFIRMANDO > 5min",
            "deteccion": "consulta a cierre (request->>'requestedAt')",
            "severidad": "Alta",
            "runbook": "RB2",
            "valor": len(cierre_rows),
            "dispara": bool(cierre_rows),
            "detalle": [{"closureId": r[0], "idRecepcion": r[1], "requestedAt": r[2]} for r in cierre_rows],
        }
    )

    # 5. Operación de habitación (Media, RB3)
    operacion_rows = c.psql(
        "hotel_db",
        "select block_id::text, id_habitacion, request->>'requestedAt' "
        "from operacion_habitacion where estado = 'PREPARANDO' "
        "and (request->>'requestedAt')::timestamptz < now() - interval '10 minutes'",
    )
    alerts.append(
        {
            "alerta": "Operación de habitación",
            "umbral": "PREPARANDO > 10min",
            "deteccion": "consulta a operacion_habitacion (request->>'requestedAt')",
            "severidad": "Media",
            "runbook": "RB3",
            "valor": len(operacion_rows),
            "dispara": bool(operacion_rows),
            "detalle": [
                {"blockId": r[0], "idHabitacion": r[1], "requestedAt": r[2]} for r in operacion_rows
            ],
        }
    )

    # 6. Readiness caída (Media)
    readiness = _readiness_probe()
    alerts.append(
        {
            "alerta": "Readiness caída",
            "umbral": "> 1min",
            "deteccion": "GET /actuator/health/readiness por servicio",
            "severidad": "Media",
            "runbook": "Revisar dependencia",
            "valor": readiness["firing"],
            "dispara": bool(readiness["firing"]),
            "detalle": readiness["services"],
        }
    )

    # 7. Jobs de notification (Media, RB1)
    jobs_rows = c.psql(
        "notification_db",
        "select command_id::text, tipo, attempts, last_error_code "
        "from notification_job where estado = 'ERROR' order by next_attempt_at",
    )
    alerts.append(
        {
            "alerta": "Jobs de notification",
            "umbral": "estado ERROR > 0",
            "deteccion": "consulta a notification_job",
            "severidad": "Media",
            "runbook": "RB1 (CLI)",
            "valor": len(jobs_rows),
            "dispara": bool(jobs_rows),
            "detalle": [
                {"commandId": r[0], "tipo": r[1], "attempts": int(r[2]), "lastErrorCode": r[3]}
                for r in jobs_rows
            ],
        }
    )

    # 8. Reporting degradado (Media, RB1)
    degraded_rows = c.psql(
        "reporting_db",
        "select topic, last_offset, last_event_at::text, updated_at::text "
        "from projection_checkpoint where degraded = true order by topic",
    )
    alerts.append(
        {
            "alerta": "Reporting degradado",
            "umbral": "degraded=true o sin conexión Kafka > 5min",
            "deteccion": "projection_checkpoint / meta de la API",
            "severidad": "Media",
            "runbook": "RB1",
            "valor": len(degraded_rows),
            "dispara": bool(degraded_rows),
            "detalle": [
                {"topic": r[0], "lastOffset": r[1], "lastEventAt": r[2], "updatedAt": r[3]}
                for r in degraded_rows
            ],
        }
    )

    # 9. Ventas en recuperación (Media, RB1/RB4)
    ventas_rows = c.psql(
        "sales_db",
        "select id, id_recepcion, total::text, updated_at::text from venta "
        "where estado_operacion = 'CANCELACION_PENDIENTE' "
        "and updated_at < now() - interval '30 minutes' order by id",
    )
    alerts.append(
        {
            "alerta": "Ventas en recuperación",
            "umbral": "CANCELACION_PENDIENTE > 30min",
            "deteccion": "consulta a venta (updated_at)",
            "severidad": "Media",
            "runbook": "RB1/RB4",
            "valor": len(ventas_rows),
            "dispara": bool(ventas_rows),
            "detalle": [
                {"idVenta": r[0], "idRecepcion": r[1], "total": r[2], "updatedAt": r[3]}
                for r in ventas_rows
            ],
        }
    )

    return alerts


def render(alerts: list[dict[str, Any]]) -> str:
    lines = [
        "alerta | umbral | severidad | runbook | valor | dispara",
        "---|---|---|---|---|---",
    ]
    for a in alerts:
        value = a["valor"]
        if isinstance(value, list):
            value = ",".join(value) if value else "0"
        lines.append(
            f"{a['alerta']} | {a['umbral']} | {a['severidad']} | {a['runbook']} | {value} | "
            f"{'SI' if a['dispara'] else 'no'}"
        )
    return "\n".join(lines)


def run(*, expect_clean: bool = False, sim: str = "alertas", label: str = "alertas") -> int:
    alerts = collect()
    payload = {"capturadoEn": c.iso(), "alertas": alerts}
    c.write_json(sim, f"{label}.json", payload)
    text = render(alerts) + "\n"
    c.write_evidence(sim, f"{label}.txt", text)
    print(text)
    firing = [a["alerta"] for a in alerts if a["dispara"]]
    if expect_clean and firing:
        print(f"FALLO: alertas disparadas en limpio: {', '.join(firing)}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
