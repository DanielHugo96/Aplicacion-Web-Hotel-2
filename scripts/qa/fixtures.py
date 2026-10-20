"""Fixtures compartidos de 06 §5 creados por las rutas de la spec (nunca por INSERT)."""

from __future__ import annotations

import json
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Any

from . import common as c


def admin_token() -> str:
    return c.login("admin@hotel.test", c.env("DEMO_PASSWORD"))


def personal_token(sede: str = "lima") -> str:
    correo = {
        "lima": "recepcion.lima@hotel.test",
        "miraflores": "recepcion.miraflores@hotel.test",
        "arequipa": "recepcion.arequipa@hotel.test",
    }[sede]
    return c.login(correo, c.env("DEMO_PASSWORD"))


def tomorrow() -> str:
    return (date.today() + timedelta(days=1)).isoformat()


def room(room_id: int, token: str | None = None) -> dict[str, Any]:
    token = token or admin_token()
    resp = c.http("GET", f"/api/habitacion/buscar/{room_id}", token=token)
    if resp.status != 200:
        raise c.RunbookError(f"habitación {room_id} devolvió {resp.status}: {resp.body[:300]}")
    return resp.field("data") or {}


def checkin(
    *,
    room_id: int,
    hotel_id: int,
    cliente: int,
    adelanto: float = 0.0,
    salida: str | None = None,
    key: str | None = None,
    token: str | None = None,
    medio_pago: str = "EFECTIVO",
) -> tuple[c.Resp, dict[str, Any]]:
    token = token or admin_token()
    key = key or str(uuid.uuid4())
    body = {
        "idHotel": hotel_id,
        "idCliente": cliente,
        "idHabitacion": room_id,
        "fechaSalida": salida or tomorrow(),
        "adelanto": round(adelanto, 2),
        "medioPago": medio_pago,
        "observacion": "Fixture qa-runbooks",
    }
    resp = c.http(
        "POST",
        "/api/recepcion/registrar",
        token=token,
        body=body,
        headers={"Idempotency-Key": key},
    )
    return resp, (resp.field("data") or {})


def create_sale(
    *,
    id_recepcion: int,
    items: list[dict[str, int]],
    estado: str = "PENDIENTE",
    medio_pago: str | None = None,
    key: str | None = None,
    token: str | None = None,
) -> tuple[c.Resp, dict[str, Any]]:
    token = token or admin_token()
    key = key or str(uuid.uuid4())
    body: dict[str, Any] = {"idRecepcion": id_recepcion, "estado": estado, "detalles": items}
    if medio_pago:
        body["medioPago"] = medio_pago
    resp = c.http(
        "POST",
        "/api/venta",
        token=token,
        body=body,
        headers={"Idempotency-Key": key},
    )
    return resp, (resp.field("data") or {})


def checkout(
    *,
    id_recepcion: int,
    room_id: int,
    monto: float,
    key: str | None = None,
    token: str | None = None,
    costo_penalidad: float = 0.0,
) -> c.Resp:
    token = token or admin_token()
    key = key or str(uuid.uuid4())
    body = {
        "idRecepcion": id_recepcion,
        "idHabitacion": room_id,
        "costoPenalidad": round(costo_penalidad, 2),
        "montoRecibido": round(monto, 2),
        "medioPago": "EFECTIVO",
    }
    return c.http(
        "POST",
        "/api/recepcion/registrar-salida",
        token=token,
        body=body,
        headers={"Idempotency-Key": key},
    )


def maintenance(room_id: int, motivo: str = "Mantenimiento de simulacro", key: str | None = None, token: str | None = None) -> c.Resp:
    token = token or admin_token()
    key = key or str(uuid.uuid4())
    version = room(room_id, token).get("version")
    return c.http(
        "POST",
        f"/api/habitacion/{room_id}/mantenimiento",
        token=token,
        body={"motivo": motivo},
        headers={"Idempotency-Key": key, "If-Match": str(version)},
        timeout=30,
    )


def pending_cleaning(room_id: int, hotel_id: int, token: str | None = None) -> dict[str, Any] | None:
    token = token or admin_token()
    resp = c.http("GET", f"/api/habitacion/limpiezas?idHotel={hotel_id}&estado=PENDIENTE", token=token)
    for item in resp.field("data") or []:
        if int(item.get("idHabitacion", 0)) == room_id:
            return item
    return None


def complete_cleaning(room_id: int, hotel_id: int, token: str | None = None) -> c.Resp:
    token = token or admin_token()
    task = pending_cleaning(room_id, hotel_id, token)
    if not task:
        raise c.RunbookError(f"no hay limpieza PENDIENTE para la habitación {room_id}")
    return c.http(
        "PUT",
        f"/api/habitacion/{room_id}/limpieza-completada",
        token=token,
        body={"cleaningCycleId": task["cleaningCycleId"]},
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": str(task.get("version", task.get("roomVersion", 1))),
        },
    )


def wait_gate_free(room_id: int, timeout: int = 90) -> dict[str, Any]:
    def probe() -> dict[str, Any] | None:
        row = c.psql(
            "reception_db",
            f"select estado, COALESCE(block_id::text,''), COALESCE(cleaning_cycle_id::text,'') "
            f"from room_gate where id_habitacion={room_id}",
        )
        if row and row[0][0] == "LIBRE":
            return {"estado": row[0][0], "blockId": row[0][1], "cleaningCycleId": row[0][2]}
        return None

    return c.wait_until(probe, timeout=timeout, interval=3, description=f"gate LIBRE de {room_id}")


def wait_room_physical(room_id: int, code: str, timeout: int = 60) -> dict[str, Any]:
    def probe() -> dict[str, Any] | None:
        data = room(room_id)
        estado = (data.get("estadoFisico") or {}).get("codigo")
        return data if estado == code else None

    return c.wait_until(probe, timeout=timeout, interval=2, description=f"habitación {room_id} {code}")


def wait_cierre_state(closure_id: str, state: str, timeout: int = 120) -> dict[str, Any]:
    def probe() -> dict[str, Any] | None:
        rows = c.psql(
            "reception_db",
            "select estado, COALESCE(receipt_sales::text,''), "
            "COALESCE(calculo::text,''), COALESCE(last_error_code,'') "
            f"from cierre where closure_id='{closure_id}'",
        )
        if rows and rows[0][0] == state:
            return {
                "estado": rows[0][0],
                "receiptSales": rows[0][1],
                "calculo": rows[0][2],
                "lastErrorCode": rows[0][3],
            }
        return None

    return c.wait_until(probe, timeout=timeout, interval=2, description=f"cierre {closure_id} {state}")


def wait_notification_job(command_id: str, state: str, timeout: int = 120) -> dict[str, Any]:
    def probe() -> dict[str, Any] | None:
        rows = c.psql(
            "notification_db",
            "select command_id::text, estado, attempts, COALESCE(last_error_code,''), "
            "next_attempt_at::text, created_at::text "
            f"from notification_job where command_id='{command_id}'",
        )
        if rows and rows[0][1] == state:
            return {
                "commandId": rows[0][0],
                "estado": rows[0][1],
                "attempts": int(rows[0][2]),
                "lastErrorCode": rows[0][3],
                "nextAttemptAt": rows[0][4],
                "createdAt": rows[0][5],
            }
        return None

    return c.wait_until(probe, timeout=timeout, interval=3, description=f"job {command_id} {state}")


def notification_jobs() -> list[dict[str, Any]]:
    rows = c.psql(
        "notification_db",
        "select command_id::text, tipo, estado, attempts, COALESCE(last_error_code,''), "
        "next_attempt_at::text, created_at::text, id_recepcion "
        "from notification_job order by created_at, command_id",
    )
    return [
        {
            "commandId": r[0],
            "tipo": r[1],
            "estado": r[2],
            "attempts": int(r[3]),
            "lastErrorCode": r[4],
            "nextAttemptAt": r[5],
            "createdAt": r[6],
            "idRecepcion": int(r[7]) if r[7] else None,
        }
        for r in rows
    ]


def inbox_rows(db: str, group: str, message_id: str | None = None) -> list[dict[str, Any]]:
    if not c.table_exists(db, "inbox"):
        return []
    where = f"where consumer_group='{group}'"
    if message_id:
        where += f" and message_id='{message_id}'"
    rows = c.psql(
        db,
        "select message_id::text, consumer_group, processed_at::text from inbox "
        + where
        + " order by processed_at",
    )
    return [{"messageId": r[0], "consumerGroup": r[1], "processedAt": r[2]} for r in rows]


def free_room(room_id: int, hotel_id: int) -> None:
    """Devuelve una habitación a LISTA/LIBRE por las rutas del operador (no por SQL)."""
    gate = c.psql(
        "reception_db",
        f"select estado, COALESCE(cleaning_cycle_id::text,'') from room_gate where id_habitacion={room_id}",
    )
    estado = gate[0][0] if gate else ""
    if estado == "LIMPIEZA_PENDIENTE":
        deadline = time.time() + 45
        while time.time() < deadline and not pending_cleaning(room_id, hotel_id, token):
            time.sleep(3)
        resp = complete_cleaning(room_id, hotel_id)
        if resp.status not in (200, 409):
            raise c.RunbookError(
                f"completar limpieza de {room_id} devolvió {resp.status}: {resp.body[:300]}"
            )
        wait_gate_free(room_id)
    elif estado == "BLOQUEADA":
        # Una operación aplicada se libera con mantenimiento-completado por blockId.
        row = c.psql(
            "reception_db",
            f"select COALESCE(block_id::text,'') from room_gate where id_habitacion={room_id} and estado='BLOQUEADA'",
        )
        block = row[0][0] if row else ""
        version = room(room_id).get("version")
        c.http(
            "PUT",
            f"/api/habitacion/{room_id}/mantenimiento-completado",
            token=admin_token(),
            body={"blockId": block},
            headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(version)},
        )
        wait_gate_free(room_id)


def db_snapshot(db: str, tables: list[str] | None = None) -> dict[str, Any]:
    tables = tables or c.db_tables(db)
    data: dict[str, Any] = {}
    for table in tables:
        try:
            count = c.psql_one(db, f"select count(*) from {table}")
            data[table] = int(count)
        except c.RunbookError:
            data[table] = None
    return data


def outbox_rows(db: str) -> list[dict[str, Any]]:
    if not c.table_exists(db, "outbox"):
        return []
    rows = c.psql(
        db,
        "select id::text, destination, channel, type, aggregate_id, aggregate_version, "
        "payload::text, created_at::text, COALESCE(published_at::text,''), attempts, "
        "next_attempt_at::text from outbox order by created_at",
    )
    return [
        {
            "id": r[0],
            "destination": r[1],
            "channel": r[2],
            "type": r[3],
            "aggregateId": r[4],
            "aggregateVersion": int(r[5]),
            "payload": json.loads(r[6]) if r[6] else None,
            "createdAt": r[7],
            "publishedAt": r[8] or None,
            "attempts": int(r[9]),
            "nextAttemptAt": r[10],
        }
        for r in rows
    ]
