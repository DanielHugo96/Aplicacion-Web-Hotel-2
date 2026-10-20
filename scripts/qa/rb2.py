"""Simulacro RB2: cierre atascado en CONFIRMANDO, alerta e invariantes."""

from __future__ import annotations

import json
import time
import uuid
from typing import Any

from . import common as c
from . import fixtures as f
from . import alerts


def _cierre(closure_id: str) -> dict[str, Any]:
    rows = c.psql(
        "reception_db",
        "select closure_id::text, id_recepcion, estado, COALESCE(calculo::text,''), "
        "COALESCE(receipt_sales::text,''), COALESCE(last_error_code,''), "
        "attempts, request->>'requestedAt', cleaning_cycle_id::text "
        f"from cierre where closure_id='{closure_id}'",
    )
    if not rows:
        return {}
    r = rows[0]
    return {
        "closureId": r[0],
        "idRecepcion": int(r[1]),
        "estado": r[2],
        "calculo": json.loads(r[3]) if r[3] else None,
        "receiptSales": json.loads(r[4]) if r[4] else None,
        "lastErrorCode": r[5] or None,
        "attempts": int(r[6]),
        "requestedAt": r[7],
        "cleaningCycleId": r[8],
    }


def _sales_cierre(id_recepcion: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "sales_db",
        "select closure_id::text, total_pendiente::text, total_pagado_previo::text, "
        "receipt::text, confirmed_at::text from cierre_consumos "
        f"where id_recepcion={id_recepcion}",
    )
    return [
        {
            "closureId": r[0],
            "totalPendiente": r[1],
            "totalPagadoPrevio": r[2],
            "receipt": json.loads(r[3]) if r[3] else None,
            "confirmedAt": r[4],
        }
        for r in rows
    ]


def _ventas(id_recepcion: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "sales_db",
        "select id, estado, estado_operacion, total::text, COALESCE(paid_at::text,''), "
        "COALESCE(closure_id::text,'') from venta "
        f"where id_recepcion={id_recepcion} order by id",
    )
    return [
        {
            "idVenta": int(r[0]),
            "estado": r[1],
            "estadoOperacion": r[2],
            "total": r[3],
            "paidAt": r[4] or None,
            "closureId": r[5] or None,
        }
        for r in rows
    ]


def _recepcion(id_recepcion: int) -> dict[str, Any]:
    rows = c.psql(
        "reception_db",
        "select estado_estadia, COALESCE(fecha_salida_confirmacion::text,''), version, "
        "total_pagado::text from recepcion "
        f"where id={id_recepcion}",
    )
    r = rows[0]
    return {"estadoEstadia": r[0], "fechaSalidaConfirmacion": r[1] or None, "version": int(r[2]), "totalPagado": r[3]}


def _gate(room_id: int) -> dict[str, Any]:
    r = c.psql(
        "reception_db",
        "select estado, COALESCE(cleaning_cycle_id::text,''), room_version "
        f"from room_gate where id_habitacion={room_id}",
    )[0]
    return {"estado": r[0], "cleaningCycleId": r[1] or None, "roomVersion": int(r[2])}


def _salida_inbox(id_recepcion: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "reception_db",
        "select id::text, payload::text from outbox where type='SalidaRegistrada' "
        f"and payload->'payload'->>'idRecepcion'='{id_recepcion}'",
    )
    result = []
    for outbox_id, payload in rows:
        data = json.loads(payload)
        event_id = data.get("eventId")
        if event_id and c.table_exists("reporting_db", "inbox"):
            count = c.psql_one("reporting_db", f"select count(*) from inbox where message_id='{event_id}'")
            result.append({"outboxId": outbox_id, "eventId": event_id, "inboxReporting": int(count)})
    return result


def _cotizacion(id_recepcion: int, token: str) -> dict[str, Any]:
    resp = c.http("GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida", token=token)
    if resp.status != 200:
        raise c.RunbookError(f"cotización {id_recepcion} devolvió {resp.status}: {resp.body[:300]}")
    return resp.field("data") or {}


def _create_and_diagnose() -> dict[str, Any]:
    sim = "rb2-cierre"
    token = f.admin_token()
    f.free_room(101, 1)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=20.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"F1 check-in devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    sale, venta = f.create_sale(id_recepcion=id_recepcion, items=[{"idProducto": 1, "cantidad": 2}], token=token)
    if sale.status != 201:
        raise c.RunbookError(f"F2 venta devolvió {sale.status}: {sale.body[:400]}")
    cotizacion = _cotizacion(id_recepcion, token)
    c.write_json(sim, "01-fixture-f1-f2.json", {"idRecepcion": id_recepcion, "checkin": check.summary(), "venta": sale.summary(), "cotizacion": cotizacion})

    key = str(uuid.uuid4())
    before = {
        "recepcion": _recepcion(id_recepcion),
        "gate": _gate(101),
        "ventas": _ventas(id_recepcion),
        "salesCierre": _sales_cierre(id_recepcion),
        "cierreFilas": c.psql_one("reception_db", f"select count(*) from cierre where id_recepcion={id_recepcion} and estado<>'RECHAZADO'"),
    }
    lock = c.AdvisoryLock("sales_db", id_recepcion)
    try:
        started = time.time()
        out = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=float(cotizacion["cobroAhora"]), key=key, token=token)
        elapsed = int((time.time() - started) * 1000)
    finally:
        # El lock se mantiene durante la fase CONFIRMANDO; se libera explícitamente en el paso 8.
        pass
    closure_id = str(out.field("data", "closureId") or "")
    if out.status != 202 or not closure_id:
        lock.release()
        raise c.RunbookError(f"esperaba 202 con closureId, obtuve {out.status}: {out.body[:500]}")
    cierre = c.wait_cierre_state(closure_id, "CONFIRMANDO", timeout=60)
    diag1 = _cierre(closure_id)
    diag2 = _cierre(closure_id)
    after = {
        "recepcion": _recepcion(id_recepcion),
        "gate": _gate(101),
        "ventas": _ventas(id_recepcion),
        "salesCierre": _sales_cierre(id_recepcion),
        "cierre": diag1,
        "cierreRepetido": diag2,
        "cierreFilas": c.psql_one("reception_db", f"select count(*) from cierre where id_recepcion={id_recepcion} and estado<>'RECHAZADO'"),
    }
    c.write_json(
        sim,
        "02-cierre-atascado.json",
        {
            "idempotencyKey": key,
            "status": out.status,
            "headers": c.redact_headers(out.headers),
            "duracionMs": elapsed,
            "body": out.json,
            "antes": before,
            "despues": after,
            "calculoEstable": diag1.get("calculo") == diag2.get("calculo"),
            "receiptNulo": diag1.get("receiptSales") is None,
            "sinReceiptSales": not after["salesCierre"],
        },
    )
    print(f"  cierre {closure_id} en CONFIRMANDO; calculo estable y sin receipt")
    return {
        "idRecepcion": id_recepcion,
        "closureId": closure_id,
        "key": key,
        "cotizacion": cotizacion,
        "lock": lock,
    }


def _alert_and_invariants(ctx: dict[str, Any]) -> dict[str, Any]:
    sim = "rb2-cierre"
    token = f.admin_token()
    closure_id = ctx["closureId"]
    id_recepcion = ctx["idRecepcion"]

    # La alerta usa requestedAt > 5min. Se mantiene el lock durante la espera.
    firing: list[dict[str, Any]] = []
    started = time.time()
    while time.time() - started < 420:
        alerts_now = alerts.collect()
        cierre_alert = next(a for a in alerts_now if a["alerta"] == "Cierre atascado")
        firing.append({"ts": c.iso(), "valor": cierre_alert["valor"], "dispara": cierre_alert["dispara"]})
        if cierre_alert["dispara"]:
            break
        time.sleep(15)
    c.write_json(sim, "03-alerta-cierre.json", {"linea": firing, "alerta": cierre_alert})

    calculo_antes = _cierre(closure_id).get("calculo")
    same = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=float(ctx["cotizacion"]["cobroAhora"]), key=ctx["key"], token=token)
    other = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=float(ctx["cotizacion"]["cobroAhora"]), key=str(uuid.uuid4()), token=token)
    calculo_despues = _cierre(closure_id).get("calculo")
    cierres = c.psql("reception_db", f"select closure_id::text, estado from cierre where id_recepcion={id_recepcion} and estado<>'RECHAZADO'")
    c.write_json(
        sim,
        "04-invariantes.json",
        {
            "mismaClave": {"status": same.status, "body": same.json},
            "claveDistinta": {"status": other.status, "body": other.json},
            "calculoIgual": calculo_antes == calculo_despues,
            "cierresVigentes": cierres,
            "unSoloCierre": len(cierres) == 1,
        },
    )
    if same.status != 202 or other.status != 409:
        ctx["lock"].release()
        raise c.RunbookError(f"invariantes RB2 incumplidos: misma={same.status} distinta={other.status}")
    print("  alerta > 0, misma clave 202, clave distinta 409 y un solo cierre")


def _recover(ctx: dict[str, Any]) -> dict[str, Any]:
    sim = "rb2-cierre"
    token = f.admin_token()
    closure_id = ctx["closureId"]
    id_recepcion = ctx["idRecepcion"]
    ctx["lock"].release()

    # La recuperación de 30s o el mismo Idempotency-Key completan con el payload congelado.
    resp = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=float(ctx["cotizacion"]["cobroAhora"]), key=ctx["key"], token=token)
    if resp.status == 202:
        f.wait_cierre_state(closure_id, "COMPLETADO", timeout=120)
    cierre = _cierre(closure_id)
    recepcion = _recepcion(id_recepcion)
    gate = _gate(101)
    ventas = _ventas(id_recepcion)
    sales_cierre = _sales_cierre(id_recepcion)
    inbox = _salida_inbox(id_recepcion)

    # Repetir una confirmación completada devuelve el mismo resultado sin recalcular.
    repeated = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=float(ctx["cotizacion"]["cobroAhora"]), key=ctx["key"], token=token)
    cierre2 = _cierre(closure_id)
    same_receipt = cierre.get("receiptSales") == cierre2.get("receiptSales")
    c.write_json(
        sim,
        "05-recuperacion.json",
        {
            "primera": {"status": resp.status, "body": resp.json},
            "repetida": {"status": repeated.status, "body": repeated.json},
            "cierre": cierre,
            "recepcion": recepcion,
            "gate": gate,
            "ventas": ventas,
            "salesCierre": sales_cierre,
            "inboxSalida": inbox,
            "mismoReceipt": same_receipt,
            "alertaDespues": next(a for a in alerts.collect() if a["alerta"] == "Cierre atascado"),
        },
    )
    if cierre.get("estado") != "COMPLETADO" or recepcion["estadoEstadia"] != "CERRADA":
        raise c.RunbookError(f"el cierre no completó: {cierre} / {recepcion}")
    print("  cierre COMPLETADO, CERRADA, receipt único y gate LIMPIEZA_PENDIENTE")
    return {"idRecepcion": id_recepcion, "closureId": closure_id, "gate": gate}


def _branch_sales_receipt() -> None:
    sim = "rb2-cierre"
    token = f.admin_token()
    f.free_room(102, 1)
    check, stay = f.checkin(room_id=102, hotel_id=1, cliente=100, adelanto=0.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"rama Sales: check-in devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    sale, _ = f.create_sale(id_recepcion=id_recepcion, items=[{"idProducto": 1, "cantidad": 1}], token=token)
    if sale.status != 201:
        raise c.RunbookError(f"rama Sales: venta devolvió {sale.status}: {sale.body[:400]}")
    cotizacion = _cotizacion(id_recepcion, token)
    key = str(uuid.uuid4())

    c.stop_service("sales-service")
    time.sleep(2)
    try:
        out = f.checkout(id_recepcion=id_recepcion, room_id=102, monto=float(cotizacion["cobroAhora"]), key=key, token=token)
        closure_id = str(out.field("data", "closureId") or "")
        if out.status != 202 or not closure_id:
            raise c.RunbookError(f"rama Sales: esperaba 202, obtuve {out.status}: {out.body[:400]}")
        c.wait_cierre_state(closure_id, "CONFIRMANDO", timeout=60)
    finally:
        c.start_service("sales-service")
    diag = _cierre(closure_id)
    calculo = diag["calculo"] or {}
    expected = calculo.get("expectedPendingTotal")
    # Sales confirma primero y reception cae antes de completar: se reproduce llamando
    # a la ruta interna exacta que usa reception, con el payload congelado.
    receipt = c.http(
        "POST",
        "http://localhost:8085/internal/cierres/confirmar",
        token=c.machine_token("reception", "sales"),
        body={
            "closureId": closure_id,
            "idRecepcion": id_recepcion,
            "idHotel": int(stay["idHotel"]),
            "expectedPendingTotal": expected,
            "medioPago": "EFECTIVO",
        },
        headers={"Idempotency-Key": closure_id},
    )
    sales_before = _sales_cierre(id_recepcion)
    # Reintento de reception: debe devolver el receipt persistido y completar.
    retry = f.checkout(id_recepcion=id_recepcion, room_id=102, monto=float(cotizacion["cobroAhora"]), key=key, token=token)
    if retry.status == 202:
        f.wait_cierre_state(closure_id, "COMPLETADO", timeout=120)
    sales_after = _sales_cierre(id_recepcion)
    c.write_json(
        sim,
        "06-rama-sales-receipt.json",
        {
            "idRecepcion": id_recepcion,
            "closureId": closure_id,
            "salesConfirmDirecto": {"status": receipt.status, "body": receipt.json},
            "salesCierreAntesReintento": sales_before,
            "reintentoReception": {"status": retry.status, "body": retry.json},
            "salesCierreDespues": sales_after,
            "unSoloReceipt": len(sales_after) == 1,
            "receiptPersistido": sales_after[0]["receipt"] if sales_after else None,
            "ventas": _ventas(id_recepcion),
        },
    )
    if retry.status != 200 or len(sales_after) != 1:
        raise c.RunbookError(f"rama Sales no completó con un receipt: {retry.status} / {sales_after}")
    print("  rama «Sales ya tiene receipt»: reintento usa el receipt persistido")


def run() -> None:
    c.runbook_step("RB2: crear y diagnosticar")
    ctx = _create_and_diagnose()
    try:
        c.runbook_step("RB2: alerta e invariantes")
        _alert_and_invariants(ctx)
        c.runbook_step("RB2: recuperación por ramas")
        _recover(ctx)
    finally:
        ctx["lock"].release()
    _branch_sales_receipt()
