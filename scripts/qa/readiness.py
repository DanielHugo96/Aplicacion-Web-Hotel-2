"""Readiness caída, limpieza de DLT/DLQ y revalidación de las nueve alertas."""

from __future__ import annotations

import time
from typing import Any

from . import common as c
from . import alerts


def _readiness(service: str) -> dict[str, Any]:
    port = c.SERVICES[service]["port"]
    resp = c.http("GET", f"http://localhost:{port}/actuator/health/readiness", base="", timeout=10)
    return {"status": resp.status, "body": resp.body[:800]}


def run() -> None:
    sim = "alertas"
    c.runbook_step("Readiness caída: Rabbit detenido para notification")
    baseline = _readiness("notification-service")
    c.docker("stop", c.RABBIT, timeout=90, check=True)
    c.wait_until(lambda: not c.port_open(5673), timeout=60, interval=2, description="Rabbit detenido")
    # Primera sonda: registra el inicio del fallo para el umbral de 1min.
    alerts.collect()
    time.sleep(5)
    during: dict[str, Any] = {}
    started = time.time()
    firing = None
    linea = []
    while time.time() - started < 150:
        result = alerts.collect()
        firing = next(a for a in result if a["alerta"] == "Readiness caída")
        candidate = firing["detalle"].get("notification-service", {})
        linea.append({"ts": c.iso(), "notification": candidate, "dispara": firing["dispara"]})
        if firing["dispara"]:
            during = _readiness("notification-service")
            break
        time.sleep(10)
    c.write_json(sim, "readiness-caida.json", {"antes": baseline, "durante": during, "linea": linea, "alerta": firing})
    if not firing or not firing["dispara"]:
        c.docker("start", c.RABBIT, timeout=90, check=True)
        raise c.RunbookError("la alerta de readiness no disparó tras 1min")

    c.docker("start", c.RABBIT, timeout=90, check=True)
    c.wait_until(lambda: c.port_open(5673), timeout=120, interval=3, description="Rabbit arriba")
    c.wait_until(
        lambda: _readiness("notification-service")["status"] == 200,
        timeout=180,
        interval=5,
        description="readiness de notification recuperada",
    )
    despues = _readiness("notification-service")
    c.write_json(sim, "readiness-recuperada.json", {"despues": despues, "alerta": next(a for a in alerts.collect() if a["alerta"] == "Readiness caída")})
    print("  sonda fallida más de 1min y recuperada")

    c.runbook_step("Limpieza de DLT/DLQ y revalidación de las nueve alertas")
    dlt_before, dlt_detail = c.kafka_dlt_counts()
    dlq_before, dlq_detail = c.rabbit_dlq_counts()
    dlt_discard = {}
    for topic in list(dlt_detail):
        messages = c.kafka_consume(topic, max_messages=50, timeout_ms=4000)
        dlt_discard[topic] = {"mensajes": messages, "accion": "kafka-topics.sh --delete"}
        c.kafka_delete_topic(topic)
    dlq_discard = c.rabbit_purge_dlq()
    c.write_json(
        sim,
        "15-limpieza-dlt-dlq.json",
        {
            "dltAntes": dlt_detail,
            "dlqAntes": dlq_detail,
            "dltDescartado": dlt_discard,
            "dlqDescartado": dlq_discard,
            "dltDespues": c.kafka_dlt_counts(),
            "dlqDespues": c.rabbit_dlq_counts(),
        },
    )
    result = alerts.collect()
    c.write_json(sim, "16-alertas-en-limpio.json", {"alertas": result})
    text = alerts.render(result) + "\n"
    c.write_evidence(sim, "16-alertas-en-limpio.txt", text)
    print(text)
    firing = [a for a in result if a["dispara"]]
    if firing:
        c.write_evidence(
            sim,
            "17-hallazgo.txt",
            "Alertas que siguen disparando tras la limpieza (se registran como hallazgo):\n"
            + "\n".join(f"- {a['alerta']}: {a['detalle']}" for a in firing)
            + "\n",
        )
        print("  hallazgo: " + ", ".join(a["alerta"] for a in firing))
    print("  nueve alertas revalidadas tras el descarte")
