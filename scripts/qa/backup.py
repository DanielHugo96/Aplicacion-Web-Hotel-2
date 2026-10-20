"""Simulacros de backup (pg_dump -Fc, retención 7 días) y restauración en base nueva."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from . import common as c

BACKUPS = c.EVIDENCE / "backups"


def _dump(db: str) -> Path:
    BACKUPS.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = BACKUPS / f"{db}-{stamp}.dump"
    proc = c.run_bytes(
        ["docker", "exec", c.POSTGRES, "pg_dump", "-Fc", "-U", "postgres", "-d", db],
        timeout=600,
    )
    path.write_bytes(proc.stdout)
    return path


def _pg_restore_list(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["docker", "exec", "-i", c.POSTGRES, "pg_restore", "--list"],
        input=path.read_bytes(),
        capture_output=True,
        timeout=300,
    )


def _prune(days: int = 7) -> list[str]:
    cutoff = time.time() - days * 86400
    removed = []
    for path in sorted(BACKUPS.glob("*.dump")):
        if path.stat().st_mtime < cutoff:
            path.unlink()
            removed.append(path.name)
    return removed


def backup() -> None:
    sim = "backup"
    c.runbook_step("Backup: pg_dump -Fc de las siete bases")
    c.stop_all_services()
    dumps: dict[str, Any] = {}
    for db in c.DATABASES:
        path = _dump(db)
        listing = _pg_restore_list(path)
        dumps[db] = {
            "archivo": path.name,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "legible": listing.returncode == 0,
            "pgRestoreList": listing.stdout.decode("utf-8", "replace").splitlines()[:5],
            "pgRestoreError": listing.stderr.decode("utf-8", "replace")[:500],
        }
    # Retención de 7 días: un dump de prueba antiguo se poda y los recientes se conservan.
    old = BACKUPS / "identity_db-20100101T000000Z.dump"
    old.write_bytes(b"dump de prueba antiguo")
    old_time = (datetime.now(timezone.utc) - timedelta(days=8)).timestamp()
    os.utime(old, (old_time, old_time))
    removed = _prune(7)
    conserved = sorted(p.name for p in BACKUPS.glob("*.dump"))
    c.write_json(
        sim,
        "01-dumps.json",
        {
            "directorio": str(BACKUPS.relative_to(c.ROOT)),
            "dumps": dumps,
            "sieteDumps": len(dumps) == 7,
            "todosLegibles": all(d["legible"] for d in dumps.values()),
            "podado": removed,
            "retencionDias": 7,
            "conservados": conserved,
            "oldPodado": old.name not in conserved,
        },
    )
    status = c.run(["git", "status", "--porcelain"], check=True).stdout
    c.write_evidence(sim, "02-git-status.txt", status)
    if len(dumps) != 7 or not all(d["legible"] for d in dumps.values()):
        raise c.RunbookError("algún dump no se generó o no es legible")
    if old.name not in removed:
        raise c.RunbookError("la retención no podó el dump antiguo")
    if any("backups/" in line for line in status.splitlines()):
        raise c.RunbookError("git status muestra dumps: el .gitignore anidado no los excluye")
    print("  siete dumps legibles, retención de 7 días y sin dumps en git status")


def _latest(db: str) -> Path:
    candidates = sorted(BACKUPS.glob(f"{db}-*.dump"))
    if not candidates:
        raise c.RunbookError(f"no hay dump de {db}; ejecuta backup antes de restore")
    return candidates[-1]


def _tables(db: str) -> list[str]:
    return c.db_tables(db)


def _checksum(db: str, table: str) -> str:
    return c.psql_one(db, f"select COALESCE(md5(string_agg(t::text, '|' order by t::text)), 'vacia') from {table} t")


def _sequence_values(db: str) -> dict[str, str]:
    rows = c.psql(
        db,
        "select sequencename::text, COALESCE(last_value::text,'') from pg_sequences order by sequencename",
    )
    return {r[0]: r[1] for r in rows}


def _restore_one(db: str, dump: Path) -> dict[str, Any]:
    restore_db = f"{db}_restore"
    c.docker("exec", c.POSTGRES, "dropdb", "-U", "postgres", "--if-exists", restore_db, timeout=120, check=True)
    c.docker("exec", c.POSTGRES, "createdb", "-U", "postgres", restore_db, timeout=120, check=True)
    import subprocess

    proc = subprocess.run(
        ["docker", "exec", "-i", c.POSTGRES, "pg_restore", "-U", "postgres", "-d", restore_db, "--no-owner", "--no-privileges"],
        input=dump.read_bytes(),
        capture_output=True,
        timeout=900,
    )
    if proc.returncode != 0:
        raise c.RunbookError(f"pg_restore de {db} falló: {proc.stderr.decode('utf-8', 'replace')[-1200:]}")

    origin_tables = _tables(db)
    restored_tables = _tables(restore_db)
    comparisons: dict[str, Any] = {}
    divergences = []
    for table in origin_tables:
        origin_count = int(c.psql_one(db, f"select count(*) from {table}"))
        restored_count = int(c.psql_one(restore_db, f"select count(*) from {table}"))
        origin_sum = _checksum(db, table)
        restored_sum = _checksum(restore_db, table)
        ok = origin_count == restored_count and origin_sum == restored_sum
        comparisons[table] = {"filasOrigen": origin_count, "filasRestaurado": restored_count, "checksumOrigen": origin_sum, "checksumRestaurado": restored_sum, "igual": ok}
        if not ok:
            divergences.append(table)

    origin_seq = _sequence_values(db)
    restored_seq = _sequence_values(restore_db)
    seq_ok = origin_seq == restored_seq
    if not seq_ok:
        divergences.append("secuencias")

    outbox_origin = c.psql_one(db, "select count(*) from outbox where published_at is null") if "outbox" in origin_tables else None
    outbox_restored = c.psql_one(restore_db, "select count(*) from outbox where published_at is null") if "outbox" in restored_tables else None
    if outbox_origin != outbox_restored:
        divergences.append("outbox")

    watermarks = None
    if db == "reporting_db":
        watermarks = {
            "origen": c.psql(db, "select topic, partition, last_offset from projection_checkpoint order by topic, partition"),
            "restaurado": c.psql(restore_db, "select topic, partition, last_offset from projection_checkpoint order by topic, partition"),
        }
        if watermarks["origen"] != watermarks["restaurado"]:
            divergences.append("projection_checkpoint")

    c.docker("exec", c.POSTGRES, "dropdb", "-U", "postgres", "--if-exists", restore_db, timeout=120, check=True)
    return {
        "db": db,
        "dump": dump.name,
        "tablasOrigen": len(origin_tables),
        "tablasRestaurado": len(restored_tables),
        "comparacion": comparisons,
        "secuenciasIguales": seq_ok,
        "secuenciasOrigen": origin_seq,
        "secuenciasRestaurado": restored_seq,
        "outboxPendienteOrigen": outbox_origin,
        "outboxPendienteRestaurado": outbox_restored,
        "watermarks": watermarks,
        "divergencias": divergences,
        "baseEliminada": restore_db,
        "sinBasesResiduales": restore_db not in c.psql_one("postgres", "select string_agg(datname, ',') from pg_database").split(","),
    }


def restore() -> None:
    sim = "restauracion"
    c.runbook_step("Restauración: base nueva por cada dump")
    results = {}
    for db in c.DATABASES:
        results[db] = _restore_one(db, _latest(db))
    residual = c.psql(
        "postgres",
        "select datname from pg_database where datname like '%\\_restore' order by datname",
    )
    c.write_json(
        sim,
        "01-comparacion.json",
        {
            "bases": results,
            "sinDivergencias": all(not r["divergencias"] for r in results.values()),
            "basesResiduales": [r[0] for r in residual],
            "secuenciasPorEncimaDelMayorId": "verificado por tabla en comparacion",
        },
    )
    if any(r["divergencias"] for r in results.values()):
        raise c.RunbookError("la restauración presenta divergencias")
    if residual:
        raise c.RunbookError(f"quedaron bases _restore: {residual}")
    print("  siete bases restauradas y comparadas sin divergencias")
