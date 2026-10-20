#!/usr/bin/env python3
"""Runner de los simulacros de F5 (docs/plans/qa-runbooks.md).

Uso:
  python scripts/qa/runbook.py --help
  python scripts/qa/runbook.py preflight
  python scripts/qa/runbook.py reset [--build]
  python scripts/qa/runbook.py alerts [--clean]
  python scripts/qa/runbook.py rb1-kafka | rb1-rabbit | rb1-notification
  python scripts/qa/runbook.py rb2 | rb3 | rb4 | ventas | readiness
  python scripts/qa/runbook.py backup | restore
  python scripts/qa/runbook.py evidencia

Los simulacros hablan con el Compose y los servicios reales; no hay mocks ni
respuestas de memoria.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from scripts.qa import alerts as alerts_mod  # noqa: E402
from scripts.qa import backup as backup_mod  # noqa: E402
from scripts.qa import common as c  # noqa: E402
from scripts.qa import fixtures as f  # noqa: E402
from scripts.qa import rb1, rb2, rb3, rb4, readiness, ventas  # noqa: E402


def preflight() -> int:
    failures: list[str] = []
    checks: dict[str, object] = {}

    ps = c.compose_ps()
    expected = {"postgres", "kafka", "rabbitmq", "mailpit"}
    for service in sorted(expected):
        state = ps.get(service, "ausente")
        checks[f"compose:{service}"] = state
        if not state.startswith("running/healthy"):
            failures.append(f"Compose: {service} está {state}")
    checks["compose"] = ps

    for name in c.START_ORDER:
        health = c.service_health(name)
        checks[f"servicio:{name}"] = health
        if name in ("identity-service", "hotel-service", "reception-service", "inventory-service", "sales-service", "reporting-service", "notification-service"):
            if health["status"] != 200 or '"UP"' not in health["body"]:
                failures.append(f"servicio: {name} readiness {health['status']} {health['body'][:120]}")
        elif name == "discovery-server":
            if health["status"] != 200:
                failures.append(f"servicio: discovery-server liveness {health['status']}")
        else:
            if health["status"] == 0:
                failures.append("servicio: api-gateway no responde")

    seeds = {
        "hoteles": c.psql_one("hotel_db", "select count(*) from hotel"),
        "habitaciones": c.psql_one("hotel_db", "select count(*) from habitacion"),
        "personas": c.psql_one("identity_db", "select count(*) from persona"),
        "gates": c.psql_one("reception_db", "select count(*) from room_gate"),
        "productosSede": c.psql_one("inventory_db", "select count(*) from producto_sede"),
    }
    checks["seeds"] = seeds
    for key, minimum in {"hoteles": 3, "habitaciones": 6, "personas": 6, "gates": 6, "productosSede": 9}.items():
        try:
            if int(seeds[key]) < minimum:
                failures.append(f"seeds: {key}={seeds[key]} (mínimo {minimum})")
        except Exception:
            failures.append(f"seeds: no se pudo contar {key}")

    bootstrap = c.psql_one("reporting_db", "select estado from projection_bootstrap where id=1")
    checks["reportingBootstrap"] = bootstrap
    if bootstrap != "LISTO":
        failures.append(f"reporting: bootstrap {bootstrap or 'ausente'} (se esperaba LISTO)")

    temas = c.kafka_topics()
    checks["kafkaTopics"] = temas
    for topic in ["hotel.habitacion.v1", "reception.recepcion.v1", "sales.venta.v1"]:
        if topic not in temas:
            failures.append(f"Kafka: falta el topic {topic}")
    queues = c.rabbit_queues()
    checks["rabbitQueues"] = sorted(queues)
    for queue in ["limpieza.solicitada", "notificacion.checkout", "notificacion.checkin"]:
        if queue not in queues:
            failures.append(f"Rabbit: falta la cola {queue}")

    validator = c.run(["python", "scripts/validar-planes.py"], check=False, timeout=120)
    checks["validarPlanes"] = {"exit": validator.returncode, "stdout": validator.stdout[-2000:], "stderr": validator.stderr[-1000:]}
    if validator.returncode != 0:
        failures.append(f"validar-planes.py devolvió {validator.returncode}")

    summary = {"checks": checks, "fallos": failures, "ok": not failures}
    c.write_json("preflight", "preflight.json", summary)
    print(json.dumps({"ok": not failures, "fallos": failures}, indent=2, ensure_ascii=False))
    if failures:
        print("Requisitos incumplidos:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("Preflight OK: Compose sano, seeds/gates, reporting LISTO y validador sin errores.")
    return 0


def evidence_index() -> int:
    base = c.EVIDENCE
    rows = []
    for folder in sorted(p for p in base.iterdir() if p.is_dir() and not p.name.startswith("_") and p.name != "backups"):
        files = sorted(p.name for p in folder.iterdir() if p.is_file())
        rows.append((folder.name, len(files), files))
    lines = [
        "# Evidencia de qa-runbooks",
        "",
        "Generado por `python scripts/qa/runbook.py evidencia`. Cada carpeta corresponde a un",
        "simulacro de la sección 6 del plan. Los dumps de `backups/` y los logs de `_runtime/`",
        "están excluidos por el `.gitignore` anidado y nunca se commitean.",
        "",
        "| Simulacro | Archivos | Comando | Resultado |",
        "|---|---|---|---|",
    ]
    command = {
        "rb1-kafka": "python scripts/qa/runbook.py rb1-kafka",
        "rb1-rabbit": "python scripts/qa/runbook.py rb1-rabbit",
        "rb1-notificacion": "python scripts/qa/runbook.py rb1-notification",
        "rb2-cierre": "python scripts/qa/runbook.py rb2",
        "rb3-operacion": "python scripts/qa/runbook.py rb3",
        "rb4-outbox": "python scripts/qa/runbook.py rb4",
        "alertas": "python scripts/qa/runbook.py alerts --clean / readiness",
        "backup": "python scripts/qa/runbook.py backup",
        "restauracion": "python scripts/qa/runbook.py restore",
        "ventas-recuperacion": "python scripts/qa/runbook.py ventas",
        "preflight": "python scripts/qa/runbook.py preflight",
        "reset": "python scripts/qa/runbook.py reset",
    }
    for name, count, files in rows:
        result = "OK" if (Path(base) / name).exists() else "sin evidencia"
        lines.append(f"| `{name}` | {count} | `{command.get(name, '—')}` | {result} |")
    lines += ["", "## Casos de la matriz", "", "Este plan no reclama ningún caso de `06` §5; los ejecuta `qa-matriz-aceptacion`. Los fixtures compartidos se usan solo como precondición.", ""]
    lines += ["## Archivos por simulacro", ""]
    for name, _, files in rows:
        lines.append(f"### {name}")
        for filename in files:
            lines.append(f"- `{name}/{filename}`")
        lines.append("")
    text = "\n".join(lines) + "\n"
    (base / "README.md").write_text(text, encoding="utf-8")
    status = c.run(["git", "status", "--porcelain"], check=True).stdout
    (base / "git-status.txt").write_text(status, encoding="utf-8")
    if any("backups/" in line for line in status.splitlines()):
        raise c.RunbookError("git status muestra dumps de backups/")
    print(f"índice escrito en {base / 'README.md'}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Runner de los simulacros de F5: RB1-RB4, alertas, backup y restauración.",
        epilog="Los simulacros se ejecutan contra el Compose real y dejan evidencia en tests/evidence/qa-runbooks/.",
    )
    sub = parser.add_subparsers(dest="command", metavar="<simulacro>")
    sub.add_parser("preflight", help="valida Compose, seeds/gates, reporting LISTO y validar-planes")
    reset = sub.add_parser("reset", help="resetea la demo sobre volúmenes nuevos del Compose")
    reset.add_argument("--build", action="store_true", help="compila los servicios antes de arrancar")
    alerts_parser = sub.add_parser("alerts", help="evalúa las nueve alertas de runbooks.md")
    alerts_parser.add_argument("--clean", action="store_true", help="falla si alguna alerta dispara")
    sub.add_parser("rb1-kafka", help="RB1 Kafka: mensaje inválido hasta DLT y reenvío idempotente")
    sub.add_parser("rb1-rabbit", help="RB1 Rabbit: contradicción y schema inválido hasta DLQ")
    sub.add_parser("rb1-notification", help="RB1 Notification: jobs ERROR, retry, discard y purge")
    sub.add_parser("rb2", help="RB2: cierre atascado, alerta, invariantes y recuperación")
    sub.add_parser("rb3", help="RB3: operación PREPARANDO, alerta, recuperación y F6")
    sub.add_parser("rb4", help="RB4: outbox con Kafka caído, alerta, schema y reanudación")
    sub.add_parser("ventas", help="venta en CANCELACION_PENDIENTE > 30min y compensación")
    sub.add_parser("readiness", help="readiness caída, limpieza de DLT/DLQ y revalidación")
    sub.add_parser("backup", help="pg_dump -Fc de las siete bases con retención de 7 días")
    sub.add_parser("restore", help="restaura cada dump en una base nueva y compara")
    sub.add_parser("evidencia", help="consolida el índice de evidencia")
    args = parser.parse_args(argv)

    if not args.command:
        parser.print_help()
        return 2
    try:
        if args.command == "preflight":
            return preflight()
        if args.command == "reset":
            result = c.reset_demo(build=args.build)
            c.write_json("reset", "reset.json", result)
            print(json.dumps({"compose": result["compose_ps"], "servicios": {k: v["status"] for k, v in result["servicios"].items()}}, indent=2))
            return 0
        if args.command == "alerts":
            return alerts_mod.run(expect_clean=args.clean)
        if args.command == "rb1-kafka":
            rb1.kafka()
        elif args.command == "rb1-rabbit":
            rb1.rabbit()
        elif args.command == "rb1-notification":
            rb1.notification()
        elif args.command == "rb2":
            rb2.run()
        elif args.command == "rb3":
            rb3.run()
        elif args.command == "rb4":
            rb4.run()
        elif args.command == "ventas":
            ventas.run()
        elif args.command == "readiness":
            readiness.run()
        elif args.command == "backup":
            backup_mod.backup()
        elif args.command == "restore":
            backup_mod.restore()
        elif args.command == "evidencia":
            return evidence_index()
        else:
            parser.print_help()
            return 2
        return 0
    except c.RunbookError as err:
        print(f"\nERROR: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
