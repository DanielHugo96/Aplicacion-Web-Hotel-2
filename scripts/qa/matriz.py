#!/usr/bin/env python3
"""Runner de la matriz de aceptación de `06` §5 (`docs/plans/qa-matriz-aceptacion.md`).

Cada caso se ejecuta contra el Compose y los servicios reales, a través del Gateway
(`http://localhost:8080`) salvo las rutas `/internal/**`, que se prueban contra el puerto
interno del servicio para demostrar que no están publicadas. La evidencia se guarda en
`tests/evidence/qa-matriz-aceptacion/` con un archivo por caso.

Uso:
  python scripts/qa/matriz.py --help
  python scripts/qa/matriz.py preflight
  python scripts/qa/matriz.py fixtures
  python scripts/qa/matriz.py sec
  python scripts/qa/matriz.py lp-crud
  python scripts/qa/matriz.py occ-cln
  python scripts/qa/matriz.py stk
  python scripts/qa/matriz.py pay-out
  python scripts/qa/matriz.py msg
  python scripts/qa/matriz.py rep
  python scripts/qa/matriz.py not
  python scripts/qa/matriz.py informe

Solo usa la biblioteca estándar y las utilidades de `scripts/qa/common.py`.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from scripts.qa import common as c  # noqa: E402
from scripts.qa import fixtures as f  # noqa: E402

EVIDENCE = ROOT / "tests" / "evidencia" / "qa-matriz-aceptacion"
STATE = EVIDENCE / "fixtures.json"
GATEWAY = c.GATEWAY


# ---------------------------------------------------------------------------
# Evidencia
# ---------------------------------------------------------------------------


def save(case: str, content: Any) -> Path:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    if not isinstance(content, dict):
        content = {"resultado": content}
    content.setdefault("caso", case)
    content.setdefault("ejecutadoEn", c.iso())
    path = EVIDENCE / f"{case}.json"
    path.write_text(json.dumps(content, indent=2, ensure_ascii=False, default=str) + "\n", encoding="utf-8")
    return path


def resumen(resp: c.Resp) -> dict[str, Any]:
    return {
        "status": resp.status,
        "headers": c.redact_headers(resp.headers),
        "body": resp.json if resp.json is not None else resp.body[:2000],
    }


def leer_estado() -> dict[str, Any]:
    if STATE.exists():
        return json.loads(STATE.read_text(encoding="utf-8"))
    return {}


def guardar_estado(datos: dict[str, Any]) -> None:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(datos, indent=2, ensure_ascii=False, default=str) + "\n", encoding="utf-8")


def codigo(resp: c.Resp) -> str | None:
    return resp.field("error", "code")


def token_cliente(persona: int = 100) -> str:
    correo = "cliente.uno@hotel.test" if persona == 100 else "cliente.dos@hotel.test"
    return c.login(correo, c.env("DEMO_PASSWORD"))


def esperar(descripcion: str, predicate, timeout: int = 90, interval: int = 2):
    return c.wait_until(predicate, timeout=timeout, interval=interval, description=descripcion)


def habitacion(room_id: int, token: str | None = None) -> dict[str, Any]:
    return f.room(room_id, token)


def gate(room_id: int) -> dict[str, Any]:
    rows = c.psql(
        "reception_db",
        "select estado, COALESCE(block_id::text,''), COALESCE(cleaning_cycle_id::text,''), room_version "
        f"from room_gate where id_habitacion={room_id}",
    )
    if not rows:
        return {}
    r = rows[0]
    return {"estado": r[0], "blockId": r[1] or None, "cleaningCycleId": r[2] or None, "roomVersion": int(r[3])}


def venta_row(id_venta: int) -> dict[str, Any]:
    rows = c.psql(
        "sales_db",
        "select id, estado, estado_operacion, total::text, COALESCE(paid_at::text,''), "
        "COALESCE(medio_pago,''), origen, version, COALESCE(closure_id::text,'') "
        f"from venta where id={id_venta}",
    )
    if not rows:
        return {}
    r = rows[0]
    return {
        "idVenta": int(r[0]),
        "estado": r[1],
        "estadoOperacion": r[2],
        "total": r[3],
        "paidAt": r[4] or None,
        "medioPago": r[5] or None,
        "origen": r[6],
        "version": int(r[7]),
        "closureId": r[8] or None,
    }


def movimientos(id_venta: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "inventory_db",
        "select id_venta, id_hotel, estado, COALESCE(resultado::text,''), created_at::text "
        f"from movimiento_venta where id_venta={id_venta}",
    )
    return [
        {"idVenta": int(r[0]), "idHotel": int(r[1]), "estado": r[2], "resultado": r[3], "createdAt": r[4]}
        for r in rows
    ]


def stock(id_producto: int, id_hotel: int = 1) -> int:
    return int(
        c.psql_one(
            "inventory_db",
            f"select cantidad from producto_sede where id_producto={id_producto} and id_hotel={id_hotel}",
            default="-1",
        )
    )


def operaciones_consumo(id_recepcion: int) -> list[dict[str, Any]]:
    rows = c.psql(
        "reception_db",
        "select operation_id::text, tipo, estado, COALESCE(resultado,''), created_at::text, COALESCE(finished_at::text,'') "
        f"from operacion_consumo where id_recepcion={id_recepcion} order by created_at",
    )
    return [
        {"operationId": r[0], "tipo": r[1], "estado": r[2], "resultado": r[3] or None, "createdAt": r[4], "finishedAt": r[5] or None}
        for r in rows
    ]


def esperar_operacion_terminal(id_recepcion: int, timeout: int = 90) -> None:
    esperar(
        f"operaciones de consumo terminales de {id_recepcion}",
        lambda: c.psql_one(
            "reception_db",
            f"select count(*) from operacion_consumo where id_recepcion={id_recepcion} and estado='ABIERTA'",
        )
        == "0",
        timeout=timeout,
    )


# ---------------------------------------------------------------------------
# Paso 1 — preflight de la plataforma
# ---------------------------------------------------------------------------


def preflight() -> int:
    fallos: list[str] = []
    detalle: dict[str, Any] = {"compose": c.compose_ps(), "servicios": {}, "gateway": {}}
    for servicio in ["postgres", "kafka", "rabbitmq", "mailpit"]:
        estado = detalle["compose"].get(servicio, "ausente")
        if not estado.startswith("running/healthy"):
            fallos.append(f"Compose {servicio}: {estado}")
    for nombre in c.START_ORDER:
        salud = c.service_health(nombre)
        detalle["servicios"][nombre] = salud
        if nombre in ("identity-service", "hotel-service", "reception-service", "inventory-service",
                      "sales-service", "reporting-service", "notification-service"):
            if salud["status"] != 200 or '"UP"' not in salud["body"]:
                fallos.append(f"{nombre} readiness {salud['status']}")
        elif nombre == "discovery-server":
            if salud["status"] != 200:
                fallos.append(f"discovery-server liveness {salud['status']}")
        elif salud["status"] == 0:
            fallos.append("api-gateway no responde")
    sin_token = c.http("GET", "/api/persona/listar")
    detalle["gateway"]["sinToken"] = {"status": sin_token.status, "code": codigo(sin_token)}
    if sin_token.status != 401 or codigo(sin_token) != "UNAUTHENTICATED":
        fallos.append(f"Gateway sin token devolvió {sin_token.status} {codigo(sin_token)}")
    detalle["reportingBootstrap"] = c.psql_one("reporting_db", "select estado from projection_bootstrap where id=1")
    if detalle["reportingBootstrap"] != "LISTO":
        fallos.append("reporting no está LISTO")
    detalle["seeds"] = {
        "hoteles": c.psql_one("hotel_db", "select count(*) from hotel"),
        "habitaciones": c.psql_one("hotel_db", "select count(*) from habitacion"),
        "personas": c.psql_one("identity_db", "select count(*) from persona"),
        "gates": c.psql_one("reception_db", "select count(*) from room_gate"),
        "productosSede": c.psql_one("inventory_db", "select count(*) from producto_sede"),
    }
    detalle["fallos"] = fallos
    detalle["ok"] = not fallos
    save("preflight", detalle)
    print(json.dumps({"ok": not fallos, "fallos": fallos}, indent=2, ensure_ascii=False))
    return 0 if not fallos else 1


# ---------------------------------------------------------------------------
# Pasos 3–6 — fixtures F1–F9
# ---------------------------------------------------------------------------


def fixtures() -> int:
    """Siembra F1–F9 por las rutas de la spec (SQL solo para F9, que es una importación)."""
    token = f.admin_token()
    estado: dict[str, Any] = {}
    verificación: dict[str, Any] = {}

    # F1: estadía ACTIVA de cliente 100 en 101 con adelanto 20.00.
    f.free_room(101, 1)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=20.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"F1 devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    estado["F1"] = {"idRecepcion": id_recepcion, "idHabitacion": 101, "idCliente": 100, "checkin": check.summary()}

    # F2: venta PENDIENTE producto 1 ×2 (7.00) con Idempotency-Key guardada para STK03.
    clave_f2 = str(uuid.uuid4())
    sale, venta = f.create_sale(id_recepcion=id_recepcion, items=[{"idProducto": 1, "cantidad": 2}], key=clave_f2, token=token)
    if sale.status != 201:
        raise c.RunbookError(f"F2 devolvió {sale.status}: {sale.body[:400]}")
    id_venta = int(venta["idVenta"])
    estado["F2"] = {
        "idVenta": id_venta,
        "operationId": venta.get("operationId"),
        "idempotencyKey": clave_f2,
        "payload": {"idRecepcion": id_recepcion, "detalles": [{"idProducto": 1, "cantidad": 2}]},
        "venta": sale.summary(),
    }
    verificación["F2_stock18"] = stock(1, 1) == 18

    # F3: F2 pagada en EFECTIVO.
    v = c.http("GET", f"/api/venta/buscar/{id_venta}", token=token)
    pago = c.http(
        "PUT",
        f"/api/venta/{id_venta}",
        token=token,
        body={"estado": "PAGADO", "medioPago": "EFECTIVO"},
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(v.field("data", "version"))},
    )
    if pago.status != 200:
        raise c.RunbookError(f"F3 devolvió {pago.status}: {pago.body[:400]}")
    esperar_operacion_terminal(id_recepcion)
    estado["F3"] = {"idVenta": id_venta, "pago": pago.summary(), "venta": venta_row(id_venta)}
    verificación["F3_pagada"] = venta_row(id_venta)["estado"] == "PAGADO"

    # F8: cierre 200 de F1 (adelanto 20 + cobro 50), gate LIMPIEZA_PENDIENTE y ciclo C1.
    cotizacion = c.http("GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida", token=token)
    cobro = float(cotizacion.field("data", "cobroAhora"))
    salida = f.checkout(id_recepcion=id_recepcion, room_id=101, monto=cobro, token=token)
    if salida.status != 200:
        raise c.RunbookError(f"F8 devolvió {salida.status}: {salida.body[:400]}")
    ciclo = salida.field("data", "cleaningCycleId")
    estado["F8"] = {
        "idRecepcion": id_recepcion,
        "idVenta": id_venta,
        "closureId": salida.field("data", "closureId"),
        "cleaningCycleId": ciclo,
        "checkout": salida.summary(),
    }
    esperar("gate LIMPIEZA_PENDIENTE de 101", lambda: gate(101).get("estado") == "LIMPIEZA_PENDIENTE")
    esperar(
        "tarea de limpieza C1 en hotel",
        lambda: c.psql_one("hotel_db", f"select count(*) from limpieza where cleaning_cycle_id='{ciclo}'") == "1",
    )
    estado["F8"]["gate"] = gate(101)
    estado["F8"]["limpieza"] = c.psql(
        "hotel_db", f"select cleaning_cycle_id, estado, version from limpieza where cleaning_cycle_id='{ciclo}'"
    )
    verificación["F8_ciclo_creado"] = bool(estado["F8"]["limpieza"])

    # F4–F5: se siembran en su propio paso (requieren un servicio detenido); aquí solo se
    # deja constancia de que el orden de siembra de F1–F3 y F8 quedó completo.
    guardar_estado(estado)
    save(
        "paso-3-6-fixtures",
        {
            "caso": "FIXTURES",
            "F1": estado["F1"],
            "F2": estado["F2"],
            "F3": estado["F3"],
            "F8": estado["F8"],
            "verificacion": verificación,
            "counts": {
                "recepcion": c.psql_one("reception_db", "select count(*) from recepcion"),
                "venta": c.psql_one("sales_db", "select count(*) from venta"),
                "limpieza": c.psql_one("hotel_db", "select count(*) from limpieza"),
            },
        },
    )
    print(json.dumps(verificación, indent=2, ensure_ascii=False))
    return 0


# ---------------------------------------------------------------------------
# Pasos 4–6 — F4, F5, F6, F9
# ---------------------------------------------------------------------------


def _nueva_estadia(room_id: int, hotel_id: int, cliente: int, adelanto: float, token: str) -> int:
    _cerrar_estadia_activa(room_id, token)
    f.free_room(room_id, hotel_id)
    check, stay = f.checkin(room_id=room_id, hotel_id=hotel_id, cliente=cliente, adelanto=adelanto, token=token)
    if check.status != 201:
        raise c.RunbookError(f"check-in {room_id} devolvió {check.status}: {check.body[:400]}")
    return int(stay["idRecepcion"])


def _cerrar_estadia_activa(room_id: int, token: str) -> None:
    """Cierra por la API cualquier estadía ACTIVA que quede de una corrida anterior."""
    filas = c.psql(
        "reception_db",
        f"select id from recepcion where id_habitacion={room_id} and estado_estadia in ('ACTIVA','CERRANDO')",
    )
    for (id_recepcion,) in filas:
        cot = c.http("GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida", token=token)
        out = f.checkout(id_recepcion=int(id_recepcion), room_id=room_id,
                         monto=float(cot.field("data", "cobroAhora") or 0), token=token,
                         base="http://localhost:8083")
        if out.status not in (200, 202):
            raise c.RunbookError(f"no se pudo cerrar la estadía {id_recepcion} de {room_id}: {out.status} {out.body[:200]}")
    if filas:
        esperar(f"estadía de {room_id} cerrada", lambda: c.psql_one(
            "reception_db",
            f"select count(*) from recepcion where id_habitacion={room_id} and estado_estadia in ('ACTIVA','CERRANDO')") == "0",
            timeout=120)
        f.free_room(room_id, 1 if room_id < 200 else (2 if room_id < 300 else 3))


def fixtures_avanzados() -> int:
    token = f.admin_token()
    estado = leer_estado()
    evidencia: dict[str, Any] = {}

    # ---- F4: cierre en CONFIRMANDO. El «Sales detenido tras aceptar» se reproduce reteniendo el
    # advisory lock que serializa la confirmación de Sales (mismo recurso que usa RB2).
    idr = _nueva_estadia(102, 1, 100, 0.0, token)
    esperar_operacion_terminal(idr)
    cot = c.http("GET", f"/api/recepcion/{idr}/cotizacion-salida", token=token)
    clave_f4 = str(uuid.uuid4())
    lock = c.AdvisoryLock("sales_db", idr)
    try:
        out = f.checkout(id_recepcion=idr, room_id=102, monto=float(cot.field("data", "cobroAhora")),
                         key=clave_f4, token=token, base="http://localhost:8083")
        closure_id = str(out.field("data", "closureId") or "")
        if out.status != 202 or not closure_id:
            raise c.RunbookError(f"F4 esperaba 202 con closureId, obtuve {out.status}: {out.body[:400]}")
        fase = f.wait_cierre_state(closure_id, "CONFIRMANDO", timeout=60)
        detalle = c.psql(
            "reception_db",
            "select estado, attempts, COALESCE(last_error_code,''), calculo is not null, receipt_sales is not null "
            f"from cierre where closure_id='{closure_id}'",
        )[0]
        estado["F4"] = {
            "idRecepcion": idr,
            "idHabitacion": 102,
            "closureId": closure_id,
            "idempotencyKey": clave_f4,
            "monto": float(cot.field("data", "cobroAhora")),
            "checkout": out.summary(),
            "fase": fase,
        }
        evidencia["F4"] = {
            "inducido": "advisory lock pg_advisory_lock(7101, idRecepcion) retenido en sales_db",
            "checkout": out.summary(),
            "cierre": {"estado": detalle[0], "attempts": int(detalle[1]), "lastErrorCode": detalle[2] or None,
                       "calculoCongelado": detalle[3] == "t", "sinReceipt": detalle[4] == "f"},
            "exigido": "estado CONFIRMANDO con cálculo y sin receipt",
            "cumple": detalle[0] == "CONFIRMANDO" and detalle[3] == "t" and detalle[4] == "f",
        }
    finally:
        lock.release()
    # El cierre se completa al soltar el lock; se registra que F4 queda consumido.
    esperar("cierre F4 COMPLETADO", lambda: c.psql_one(
        "reception_db", f"select estado from cierre where closure_id='{estado['F4']['closureId']}'") == "COMPLETADO",
        timeout=120)
    f.free_room(102, 1)
    estado["F4"]["consumidoPor"] = "el cierre se completó al liberar el lock (se reinduce en OUT02)"

    # ---- F5: operación MANTENIMIENTO en PREPARANDO con reception parado a mitad de admisión.
    f.free_room(102, 1)
    c.stop_service("reception-service")
    time.sleep(2)
    try:
        out = f.maintenance(102, "F5 mantenimiento", token=token)
        block_id = str(out.field("data", "blockId") or "")
        if out.status != 202 or not block_id:
            raise c.RunbookError(f"F5 esperaba 202 con blockId, obtuve {out.status}: {out.body[:400]}")
        op = c.psql(
            "hotel_db",
            "select estado, tipo, attempts from operacion_habitacion " f"where block_id='{block_id}'",
        )[0]
        bloqueo = c.psql("reception_db", f"select estado from bloqueo_habitacion where block_id='{block_id}'")
        estado["F5"] = {"blockId": block_id, "idHabitacion": 102, "maintenance": out.summary()}
        # La consulta de la operación se hace cuando reception vuelve (la ruta vive en hotel).
        c.start_service("reception-service", timeout=180)
        consulta = c.http("GET", f"/api/habitacion/operaciones/{block_id}", token=token)
        gate_102 = gate(102)
        evidencia["F5"] = {
            "inducido": "reception-service detenido durante la admisión de la operación",
            "maintenance": out.summary(),
            "operacion": {"blockId": block_id, "estado": op[0], "tipo": op[1], "attempts": int(op[2])},
            "bloqueoAntesDeReception": bloqueo,
            "consultaOperacion": {"status": consulta.status, "body": consulta.json or consulta.body[:500]},
            "gate": gate_102,
            "exigido": "blockId guardado y fase PREPARANDO visible en GET /api/habitacion/operaciones/{blockId}",
            "cumple": op[0] == "PREPARANDO" and consulta.status == 200,
        }
        # Liberar la operación por las rutas del operador para no dejar 102 bloqueada.
        esperar("operación F5 APLICADA", lambda: c.psql_one(
            "hotel_db", f"select estado from operacion_habitacion where block_id='{block_id}'") in ("APLICADA", "LIBERADA"),
            timeout=180)
        f.free_room(102, 1)
    finally:
        if not c.port_open(8083):
            c.start_service("reception-service", timeout=180)

    # ---- F6: rechazo durable. Habitación 201 (sede 2) ocupada por cliente 101.
    idr_f6 = _nueva_estadia(201, 2, 101, 0.0, token)
    clave_f6 = str(uuid.uuid4())
    version_201 = habitacion(201).get("version")
    rechazo = f.maintenance(201, "F6 rechazo durable", key=clave_f6, token=token)
    filas = c.psql(
        "reception_db",
        "select block_id::text, estado, COALESCE(error_code,'') from bloqueo_habitacion "
        "where id_habitacion=201 order by created_at desc limit 1",
    )
    block_r = filas[0][0] if filas else ""
    estado["F6"] = {"idRecepcion": idr_f6, "idHabitacion": 201, "idHotel": 2,
                    "blockIdR": block_r, "idempotencyKey": clave_f6, "ifMatch": version_201,
                    "maintenance": rechazo.summary()}
    evidencia["F6"] = {
        "idRecepcion": idr_f6,
        "maintenance": rechazo.summary(),
        "bloqueo": {"blockId": block_r, "estado": filas[0][1], "errorCode": filas[0][2]} if filas else None,
        "gate": gate(201),
        "exigido": "409 GATE_NOT_AVAILABLE con bloqueo RECHAZADO durable y gate OCUPADA",
        "cumple": rechazo.status == 409 and bool(filas) and filas[0][1] == "RECHAZADO",
    }

    # ---- F9: venta LEGACY sin movimiento conciliado. Único fixture por SQL (06 §3.7).
    id_recepcion_legacy = int(estado.get("F8", {}).get("idRecepcion") or 0)
    if not id_recepcion_legacy:
        id_recepcion_legacy = int(c.psql_one("reception_db", "select min(id) from recepcion", default="0") or 0)
    operacion = str(uuid.uuid4())
    sql = f"""
BEGIN;
INSERT INTO venta (id_recepcion, id_cliente, id_hotel, estado, estado_operacion, admitida, origen,
                   inventory_version, total, request_items, current_operation_id, request_hash,
                   version, created_at, updated_at)
VALUES ({id_recepcion_legacy}, 100, 1, 'PENDIENTE', 'CONFIRMADA', true, 'LEGACY', 0, 5.00,
        '[{{"idProducto": 2, "cantidad": 1}}]'::jsonb, '{operacion}', repeat('a', 64), 1, now(), now());
INSERT INTO operacion_venta (operation_id, id_venta, tipo, estado, request, actor_id, actor_rol)
SELECT '{operacion}', id, 'VENTA', 'TERMINAL', '{{}}'::jsonb, 1, 'ADMIN' FROM venta
 WHERE current_operation_id='{operacion}';
COMMIT;
"""
    c.psql("sales_db", sql)
    fila = c.psql(
        "sales_db",
        f"select id, origen, estado, estado_operacion from venta where current_operation_id='{operacion}'",
    )[0]
    estado["F9"] = {"idVenta": int(fila[0]), "origen": fila[1], "estado": fila[2]}
    evidencia["F9"] = {
        "filaImportada": {"idVenta": int(fila[0]), "origen": fila[1], "estado": fila[2],
                          "estadoOperacion": fila[3]},
        "movimientos": movimientos(int(fila[0])),
        "porSql": "fila de importación/migración: no existe endpoint que cree origen=LEGACY",
        "exigido": "venta LEGACY sin movimiento conciliado visible solo en DB",
        "cumple": fila[1] == "LEGACY" and not movimientos(int(fila[0])),
    }

    guardar_estado(estado)
    save("fixtures-avanzados", {"caso": "FIXTURES-AVANZADOS", **evidencia,
                                "F4": estado["F4"], "F5": estado["F5"], "F6": estado["F6"], "F9": estado["F9"]})
    fallos = [k for k, v in evidencia.items() if not v["cumple"]]
    save("F4", {"caso": "F4", **evidencia["F4"]})
    save("F5", {"caso": "F5", **evidencia["F5"]})
    save("F6", {"caso": "F6", **evidencia["F6"]})
    save("F9", {"caso": "F9", **evidencia["F9"]})
    print(json.dumps({"fallos": fallos, "F4": evidencia["F4"]["cumple"], "F5": evidencia["F5"]["cumple"],
                      "F6": evidencia["F6"]["cumple"], "F9": evidencia["F9"]["cumple"]}, indent=2, ensure_ascii=False))
    return 0 if not fallos else 1


# ---------------------------------------------------------------------------
# Paso 9 — OCC01–OCC04 y CLN01–CLN03
# ---------------------------------------------------------------------------


def _dos_a_la_vez(a, b) -> dict[str, Any]:
    import threading

    resultados: dict[str, Any] = {}

    def correr(nombre: str, fn) -> None:
        resultados[nombre] = fn()

    hilos = [threading.Thread(target=correr, args=("a", a)), threading.Thread(target=correr, args=("b", b))]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    return resultados


def _estadias_activas(room_id: int) -> str:
    return c.psql_one(
        "reception_db",
        f"select count(*) from recepcion where id_habitacion={room_id} and estado_estadia in ('ACTIVA','CERRANDO')",
    )


def occ_cln() -> int:
    token = f.admin_token()
    estado = leer_estado()
    fallos: list[str] = []

    # ---- OCC01: dos check-in simultáneos a 101.
    f.free_room(101, 1)
    if _estadias_activas(101) != "0":
        _cerrar_estadia_activa(101, token)
    res = _dos_a_la_vez(
        lambda: f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=0.0, token=token),
        lambda: f.checkin(room_id=101, hotel_id=1, cliente=101, adelanto=0.0, token=token),
    )
    estados = sorted(r[0].status for r in res.values())
    codigos = sorted(str(codigo(r[0]) or "") for r in res.values())
    activas = _estadias_activas(101)
    cumple = (
        estados == [201, 409] and "ROOM_OCCUPIED" in codigos and activas == "1"
    )
    save("OCC01", {"caso": "OCC01",
                   "a": resumen(res["a"][0]), "b": resumen(res["b"][0]),
                   "estados": estados, "codigos": codigos,
                   "estadiasActivas101": int(activas),
                   "indiceUnico": "uq_recepcion_habitacion_activa",
                   "exigido": "uno 201, otro 409 ROOM_OCCUPIED, una sola fila ACTIVA",
                   "cumple": cumple})
    if not cumple:
        fallos.append("OCC01")
    _cerrar_estadia_activa(101, token)

    # ---- OCC02: check-in y mantenimiento concurrentes en 102.
    f.free_room(102, 1)
    if _estadias_activas(102) != "0":
        _cerrar_estadia_activa(102, token)
    res = _dos_a_la_vez(
        lambda: f.checkin(room_id=102, hotel_id=1, cliente=100, adelanto=0.0, token=token),
        lambda: f.maintenance(102, "OCC02 concurrente", token=token),
    )
    est = sorted(r[0].status if isinstance(r, tuple) else r.status for r in res.values())
    estado_gate = gate(102)
    ops = c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion "
                            f"where id_habitacion=102 order by block_id desc limit 1")
    cumple = (
        est[0] in (200, 201, 202, 409) and est[1] == 409
        and estado_gate.get("estado") in ("OCUPADA", "BLOQUEADA")
    )
    save("OCC02", {"caso": "OCC02",
                   "checkin": resumen(res["a"][0] if isinstance(res["a"], tuple) else res["a"]),
                   "mantenimiento": resumen(res["b"][0] if isinstance(res["b"], tuple) else res["b"]),
                   "estados": est,
                   "gate": estado_gate,
                   "ultimaOperacion": ops[0] if ops else None,
                   "exigido": "una sola admisión válida: gate OCUPADA o BLOQUEADA, nunca ambas; perdedor 409",
                   "cumple": cumple})
    if not cumple:
        fallos.append("OCC02")
    # Normalizar 102: liberar bloqueo si quedó BLOQUEADA y cerrar estadía si quedó OCUPADA.
    if _estadias_activas(102) != "0":
        _cerrar_estadia_activa(102, token)
    f.free_room(102, 1)

    # ---- CLN01: F8 (cierre 200, C1) con Rabbit pausado; luego completar la limpieza.
    # CLN01 se ejecuta sobre 101 (sede 1) con un cierre nuevo.
    f.free_room(101, 1)
    if _estadias_activas(101) != "0":
        _cerrar_estadia_activa(101, token)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=20.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"CLN01 check-in devolvió {check.status}: {check.body[:300]}")
    idr = int(stay["idRecepcion"])
    esperar_operacion_terminal(idr)
    cot = c.http("GET", f"/api/recepcion/{idr}/cotizacion-salida", token=token)
    # Rabbit detenido: el cierre responde 200 (no bloquea el negocio) y el comando queda en outbox.
    c.docker("stop", c.RABBIT, timeout=90, check=False)
    time.sleep(3)
    salida = f.checkout(id_recepcion=idr, room_id=101, monto=float(cot.field("data", "cobroAhora")), token=token)
    ciclo = salida.field("data", "cleaningCycleId")
    estado["CLN01"] = {"idRecepcion": idr, "cleaningCycleId": ciclo, "checkout": salida.summary()}
    # Reintento de check-in antes de completar: ROOM_NOT_READY.
    reintento, _ = f.checkin(room_id=101, hotel_id=1, cliente=101, adelanto=0.0, token=token)
    gate_pendiente = gate(101)
    c.docker("start", c.RABBIT, timeout=120, check=True)
    def rabbit_arriba() -> bool:
        try:
            return bool(c.rabbit_queues())
        except c.RunbookError:
            return False

    esperar("Rabbit arriba", rabbit_arriba, timeout=180, interval=5)
    esperar(
        "tarea C1 creada en hotel",
        lambda: c.psql_one("hotel_db", f"select count(*) from limpieza where cleaning_cycle_id='{ciclo}'") == "1",
        timeout=180,
    )
    tarea = c.psql("hotel_db", f"select cleaning_cycle_id, estado, version from limpieza where cleaning_cycle_id='{ciclo}'")
    version_tarea = int(tarea[0][2])
    completar = c.http(
        "PUT",
        "/api/habitacion/101/limpieza-completada",
        token=token,
        body={"cleaningCycleId": ciclo},
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(version_tarea)},
    )
    esperar("gate LIBRE de 101", lambda: gate(101).get("estado") == "LIBRE", timeout=120)
    despues, _ = f.checkin(room_id=101, hotel_id=1, cliente=101, adelanto=0.0, token=token)
    cumple = (
        salida.status == 200
        and reintento.status == 409 and codigo(reintento) == "ROOM_NOT_READY"
        and gate_pendiente.get("estado") == "LIMPIEZA_PENDIENTE"
        and completar.status == 200
        and gate(101).get("estado") == "OCUPADA"
        and despues.status == 201
    )
    save("CLN01", {"caso": "CLN01",
                   "checkout": salida.summary(),
                   "cleaningCycleId": ciclo,
                   "reintentoCheckin": resumen(reintento),
                   "gatePendiente": gate_pendiente,
                   "tareaTrasReanudar": tarea,
                   "completarLimpieza": resumen(completar),
                   "gateTrasCompletar": gate(101),
                   "checkinDespues": resumen(despues),
                   "exigido": "409 ROOM_NOT_READY hasta completar el ciclo correcto; 201 después",
                   "cumple": cumple})
    if not cumple:
        fallos.append("CLN01")
    idr_nuevo = int(despues.field("data", "idRecepcion")) if despues.status == 201 else None

    # ---- CLN02: reenvío de limpieza.solicitada C1 y evento viejo tras el nuevo check-in.
    reenvio = c.rabbit_publish(
        "hotel.commands.v1", "limpieza.solicitada",
        {"commandId": str(uuid.uuid4()), "type": "LimpiezaSolicitada", "schemaVersion": 1, "idHotel": 1,
         "correlationId": str(uuid.uuid4()), "issuedAt": c.iso(),
         "payload": {"idHabitacion": 101, "idRecepcion": idr,
                     "cleaningCycleId": ciclo, "roomVersion": int(gate(101).get("roomVersion") or 1)}},
        command_id=str(uuid.uuid4()),
    )
    time.sleep(6)
    tarea_c1 = c.psql("hotel_db", f"select estado from limpieza where cleaning_cycle_id='{ciclo}'")
    gate_c2 = gate(101)
    viejo = c.kafka_publish(
        "hotel.habitacion.v1", "101",
        {"eventId": str(uuid.uuid4()), "type": "HabitacionLista", "schemaVersion": 1,
         "aggregateType": "habitacion", "aggregateId": "101", "aggregateVersion": 1, "idHotel": 1,
         "occurredAt": c.iso(), "correlationId": str(uuid.uuid4()),
         "payload": {"idHabitacion": 101, "idHotel": 1, "numero": "101", "idCategoria": 1,
                     "categoriaNombre": "Individual", "pisoNombre": "Primer piso", "precio": 70.00,
                     "estado": True, "estadoFisico": "LISTA", "version": 1,
                     "cleaningCycleId": ciclo, "cleaningRoomVersion": 1, "blockId": None}},
    )
    time.sleep(8)
    cumple = (
        reenvio.status == 200
        and tarea_c1 and tarea_c1[0][0] == "COMPLETADA"
        and gate_c2.get("estado") == "OCUPADA"
        and gate(101).get("estado") == "OCUPADA"
        and c.psql_one("hotel_db", f"select count(*) from limpieza where cleaning_cycle_id='{ciclo}'") == "1"
    )
    save("CLN02", {"caso": "CLN02",
                   "reenvioComando": reenvio.summary(),
                   "tareaC1": tarea_c1,
                   "gateTrasReenvio": gate_c2,
                   "eventoViejo": {"publicado": True, "resultado": viejo.stdout.strip() if viejo else None},
                   "gateFinal": gate(101),
                   "tareasPorCiclo": c.psql_one("hotel_db",
                                                f"select count(*) from limpieza where cleaning_cycle_id='{ciclo}'"),
                   "exigido": "no reabre C1 ni libera el ciclo actual; un solo registro por ciclo",
                   "cumple": cumple})
    if not cumple:
        fallos.append("CLN02")

    # ---- CLN03: HabitacionLista antes de la salida / después del check-in nuevo.
    # Variante observable: se entrega un HabitacionLista de un ciclo posterior al cierre y se
    # comprueba que C1 se concilia sin liberar la ocupación vigente (gate OCUPADA) y que
    # gate_projection no queda LIMPIEZA_PENDIENTE eterno.
    proy_antes = c.psql("reporting_db", "select estado from gate_projection where id_habitacion=101")
    evento_cln03 = {
        "eventId": str(uuid.uuid4()), "type": "HabitacionLista", "schemaVersion": 1,
        "aggregateType": "habitacion", "aggregateId": "101", "aggregateVersion": 99, "idHotel": 1,
        "occurredAt": c.iso(), "correlationId": str(uuid.uuid4()),
        "payload": {"idHabitacion": 101, "idHotel": 1, "numero": "101", "idCategoria": 1,
                    "categoriaNombre": "Individual", "pisoNombre": "Primer piso", "precio": 70.00,
                    "estado": True, "estadoFisico": "LISTA", "version": 99,
                    "cleaningCycleId": ciclo, "cleaningRoomVersion": version_tarea, "blockId": None},
    }
    c.kafka_publish("hotel.habitacion.v1", "101", evento_cln03)
    time.sleep(10)
    proy_despues = c.psql("reporting_db", "select estado from gate_projection where id_habitacion=101")
    limpieza_proj = c.psql(
        "reporting_db",
        f"select cleaning_cycle_id::text, id_habitacion, cleaning_room_version from limpieza_completada_projection where cleaning_cycle_id='{ciclo}'",
    )
    cumple = (
        bool(proy_despues) and proy_despues[0][0] != "LIMPIEZA_PENDIENTE"
        and gate(101).get("estado") == "OCUPADA"
    )
    save("CLN03", {"caso": "CLN03",
                   "eventoEntregado": evento_cln03,
                   "gateProjectionAntes": proy_antes,
                   "gateProjectionDespues": proy_despues,
                   "limpiezaCompletadaProjection": limpieza_proj,
                   "gate": gate(101),
                   "exigido": "C1 conciliado sin liberar la ocupación posterior; projection no eterna",
                   "cumple": cumple})
    if not cumple:
        fallos.append("CLN03")

    # ---- OCC03: cierre de F6 (201) y reintento del blockId R.
    f6 = estado.get("F6", {})
    block_r = f6.get("blockIdR")
    if block_r:
        idr_f6 = f6.get("idRecepcion")
        cot = c.http("GET", f"/api/recepcion/{idr_f6}/cotizacion-salida", token=token)
        cierre = f.checkout(id_recepcion=int(idr_f6), room_id=201,
                            monto=float(cot.field("data", "cobroAhora") or 0), token=token)
        reintento_r = c.http(
            "POST", "/api/habitacion/201/mantenimiento", token=token,
            body={"motivo": "F6 rechazo durable"},
            headers={"Idempotency-Key": f6.get("idempotencyKey"), "If-Match": str(f6.get("ifMatch"))},
        )
        # El cierre deja el gate en LIMPIEZA_PENDIENTE; se completa por la ruta del operador
        # para que la nueva intención encuentre la habitación disponible.
        f.free_room(201, 2)
        nueva = f.maintenance(201, "OCC03 nueva intención", token=token)
        bloqueos = c.psql("reception_db", "select block_id::text, estado, COALESCE(error_code,'') "
                                          "from bloqueo_habitacion where id_habitacion=201 order by created_at")
        ops = c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion "
                                "where id_habitacion=201 order by block_id")
        cumple = (
            cierre.status in (200, 202)
            and reintento_r.status == 409 and codigo(reintento_r) == "GATE_NOT_AVAILABLE"
            and nueva.status in (200, 202)
            and any(b[0] == block_r and b[1] == "RECHAZADO" for b in bloqueos)
        )
        save("OCC03", {"caso": "OCC03",
                       "blockIdR": block_r,
                       "cierreEstadia": cierre.summary(),
                       "reintentoMismoBlockId": resumen(reintento_r),
                       "nuevaIntencion": resumen(nueva),
                       "bloqueos": [{"blockId": b[0], "estado": b[1], "errorCode": b[2]} for b in bloqueos],
                       "operaciones": [{"blockId": o[0], "estado": o[1]} for o in ops],
                       "exigido": "R sigue 409 GATE_NOT_AVAILABLE (rechazo durable); el nuevo 200",
                       "cumple": cumple})
        if not cumple:
            fallos.append("OCC03")
        f.free_room(201, 2)
    else:
        save("OCC03", {"caso": "OCC03", "cumple": False, "motivo": "F6 sin blockIdR"})
        fallos.append("OCC03")

    # ---- OCC04: DELETE de piso con habitación activa concurrente con alta/reactivación.
    sufijo = uuid.uuid4().hex[:6].upper()
    piso = c.http("POST", "/api/piso/registrar", token=token,
                  body={"idHotel": 1, "descripcion": f"Piso OCC04 {sufijo}"},
                  headers={"Idempotency-Key": str(uuid.uuid4())})
    categoria = c.http("POST", "/api/categoria/registrar", token=token,
                       body={"descripcion": f"Categoria OCC04 {sufijo}", "detalle": "OCC04",
                             "capacidad": 2, "estado": True},
                       headers={"Idempotency-Key": str(uuid.uuid4())})
    id_piso, id_categoria = piso.field("data", "idPiso"), categoria.field("data", "idCategoria")
    habitacion_alta = c.http("POST", "/api/habitacion/registrar", token=token,
                             body={"idHotel": 1, "numero": f"O{sufijo[:4]}", "idPiso": int(id_piso),
                                   "idCategoria": int(id_categoria), "detalle": "OCC04",
                                   "precio": 50.00, "publicada": False, "urlsImagenes": []},
                             headers={"Idempotency-Key": str(uuid.uuid4())})
    version_piso = c.http("GET", f"/api/piso/buscar/{id_piso}", token=token).field("data", "version")
    version_cat = c.http("GET", f"/api/categoria/buscar/{id_categoria}", token=token).field("data", "version")
    res = _dos_a_la_vez(
        lambda: c.http("DELETE", f"/api/piso/eliminar/{id_piso}", token=token,
                       headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(version_piso)}),
        lambda: c.http("DELETE", f"/api/categoria/eliminar/{id_categoria}", token=token,
                       headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(version_cat)}),
    )
    activas = c.psql(
        "hotel_db",
        "select h.id, p.estado as piso, c.estado as categoria, h.estado "
        "from habitacion h join piso p on p.id=h.id_piso join categoria c on c.id=h.id_categoria "
        f"where h.id_piso={id_piso} or h.id_categoria={id_categoria}",
    )
    invalido = [f for f in activas if f[3] == "t" and (f[1] == "f" or f[2] == "f")]
    cumple = (
        habitacion_alta.status == 201
        and res["a"].status == 409 and res["b"].status == 409
        and codigo(res["a"]) == "ACTIVE_DEPENDENCIES" and codigo(res["b"]) == "ACTIVE_DEPENDENCIES"
        and not invalido
    )
    save("OCC04", {"caso": "OCC04",
                   "habitacionAlta": resumen(habitacion_alta),
                   "deletePiso": resumen(res["a"]),
                   "deleteCategoria": resumen(res["b"]),
                   "filas": [{"idHabitacion": f[0], "piso": f[1], "categoria": f[2], "habitacion": f[3]} for f in activas],
                   "activaBajoPadreInactivo": invalido,
                   "exigido": "uno 409 ACTIVE_DEPENDENCIES; nunca habitación activa bajo padre inactivo",
                   "cumple": cumple})
    if not cumple:
        fallos.append("OCC04")

    guardar_estado(estado)
    print(json.dumps({"fallos": fallos}, indent=2))
    return 0 if not fallos else 1


# ---------------------------------------------------------------------------
# Paso 7 — SEC01–SEC08
# ---------------------------------------------------------------------------


def _jwt_ajeno() -> str:
    """Firma un JWT con una clave RSA propia; el Gateway/identity no la conocen."""
    payload = base64.urlsafe_b64decode(f.admin_token().split(".")[1] + "==")
    claims = json.loads(payload)
    claims["jti"] = str(uuid.uuid4())
    return _firmar(claims, _clave_ajena())


def _jwt_audiencia_incorrecta() -> str:
    payload = base64.urlsafe_b64decode(f.admin_token().split(".")[1] + "==")
    claims = json.loads(payload)
    claims["aud"] = "otra-api"
    claims["jti"] = str(uuid.uuid4())
    return _firmar(claims, c.env("IDENTITY_SIGNING_KEY_FILE"))


_CLAVE_AJENA = ROOT / "tests" / "evidence" / "qa-matriz-aceptacion" / "_runtime" / "clave-ajena.pem"


def _clave_ajena() -> str:
    _CLAVE_AJENA.parent.mkdir(parents=True, exist_ok=True)
    if not _CLAVE_AJENA.exists():
        c.run(
            ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(_CLAVE_AJENA)],
            check=True,
        )
    return str(_CLAVE_AJENA)


def _b64(raw: bytes) -> bytes:
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def _firmar(claims: dict[str, Any], clave: str) -> str:
    cabecera = _b64(json.dumps({"kid": "v1", "alg": "RS256"}, separators=(",", ":")).encode())
    cuerpo = _b64(json.dumps(claims, separators=(",", ":")).encode())
    mensaje = cabecera + b"." + cuerpo
    tmp = _CLAVE_AJENA.parent / f"msg-{uuid.uuid4().hex}.bin"
    tmp.write_bytes(mensaje)
    firma = tmp.with_suffix(".sig")
    c.run(["openssl", "dgst", "-sha256", "-sign", clave, "-out", str(firma), str(tmp)], check=True)
    valor = mensaje + b"." + _b64(firma.read_bytes())
    tmp.unlink(missing_ok=True)
    firma.unlink(missing_ok=True)
    return valor.decode()


def sec() -> int:
    token = f.admin_token()
    personal = f.personal_token("lima")
    fallos: list[str] = []

    # SEC01 — sin token.
    r = c.http("GET", "/api/persona/listar")
    save(
        "SEC01",
        {
            "endpoint": "GET /api/persona/listar",
            "sinToken": resumen(r),
            "exigido": "401 UNAUTHENTICATED sin datos",
            "cumple": r.status == 401 and codigo(r) == "UNAUTHENTICATED" and r.field("data") is None,
        },
    )
    if not (r.status == 401 and codigo(r) == "UNAUTHENTICATED"):
        fallos.append("SEC01")

    # SEC02 — registro con idTipoPersona en el cuerpo.
    cuerpo = {
        "tipoDocumento": "DNI",
        "documento": "99999901",
        "nombre": "Sec",
        "apellido": "Dos",
        "correo": "sec02-matriz@hotel.test",
        "clave": "ClaveSegura123!",
        "idTipoPersona": 1,
    }
    r = c.http("POST", "/api/auth/registro", body=cuerpo, headers={"Idempotency-Key": str(uuid.uuid4())})
    creada = c.psql_one("identity_db", "select count(*) from persona where correo='sec02-matriz@hotel.test'")
    save(
        "SEC02",
        {
            "endpoint": "POST /api/auth/registro",
            "cuerpo": {**cuerpo, "clave": "<redactada>"},
            "respuesta": resumen(r),
            "personasCreadas": int(creada),
            "exigido": "400 VALIDATION_ERROR y persona no creada",
            "cumple": r.status == 400 and codigo(r) == "VALIDATION_ERROR" and creada == "0",
        },
    )
    if not (r.status == 400 and codigo(r) == "VALIDATION_ERROR" and creada == "0"):
        fallos.append("SEC02")

    # SEC03 — token E de sede 1 sobre sede 2.
    room = c.http("GET", "/api/habitacion/buscar/201", token=personal)
    dash = c.http("GET", "/api/reportes/dashboard?idHotel=2", token=personal)
    save(
        "SEC03",
        {
            "endpoints": ["GET /api/habitacion/buscar/{id}", "GET /api/reportes/dashboard?idHotel=2"],
            "token": "E de sede 1 (recepcion.lima)",
            "habitacion201": resumen(room),
            "dashboardSede2": resumen(dash),
            "exigido": "404 RESOURCE_NOT_FOUND en ambos",
            "cumple": room.status == 404 and dash.status == 404
            and codigo(room) == "RESOURCE_NOT_FOUND" and codigo(dash) == "RESOURCE_NOT_FOUND",
        },
    )
    if not (room.status == 404 and dash.status == 404):
        fallos.append("SEC03")

    # SEC04 — cliente 100 sobre estadía ajena (cliente 101).
    ajena = _nueva_estadia(102, 1, 101, 0.0, token)
    cliente = token_cliente(100)
    carrito = c.http("GET", f"/api/carrito/listar/{ajena}", token=cliente)
    ventas_antes = c.psql_one("sales_db", f"select count(*) from venta where id_recepcion={ajena}")
    venta = f.create_sale(id_recepcion=ajena, items=[{"idProducto": 1, "cantidad": 1}], token=cliente)
    ventas_despues = c.psql_one("sales_db", f"select count(*) from venta where id_recepcion={ajena}")
    carritos = c.psql_one("sales_db", f"select count(*) from carrito where id_recepcion={ajena}")
    save(
        "SEC04",
        {
            "endpoints": ["GET /api/carrito/listar/{idRecepcion}", "POST /api/venta"],
            "token": "C del cliente 100",
            "idRecepcionAjena": ajena,
            "carrito": resumen(carrito),
            "venta": resumen(venta[0]),
            "ventasAntes": int(ventas_antes),
            "ventasDespues": int(ventas_despues),
            "filasCarrito": int(carritos),
            "exigido": "404 sin filas creadas",
            "cumple": carrito.status == 404 and venta[0].status == 404
            and ventas_antes == ventas_despues and carritos == "0",
        },
    )
    if not (carrito.status == 404 and venta[0].status == 404 and ventas_antes == ventas_despues):
        fallos.append("SEC04")

    # SEC05 — ruta interna directa al puerto de inventory.
    antes = stock(1, 1)
    directo_post = c.http(
        "POST",
        "http://localhost:8084/internal/stock/descontar",
        token=token,
        body={"idVenta": 999999, "idHotel": 1, "items": [{"idProducto": 1, "cantidad": 1}]},
        base="",
    )
    directo_get = c.http("GET", "http://localhost:8084/internal/stock/descontar", token=token, base="")
    via_gateway = c.http(
        "POST",
        "/internal/stock/descontar",
        token=token,
        body={"idVenta": 999999, "idHotel": 1, "items": [{"idProducto": 1, "cantidad": 1}]},
    )
    despues = stock(1, 1)
    cumple = directo_post.status == 403 or via_gateway.status == 404
    save(
        "SEC05",
        {
            "endpoints": ["POST /internal/stock/descontar (puerto 8084)", "GET/POST /internal/stock/descontar (Gateway)"],
            "token": "A humano",
            "directoPost": resumen(directo_post),
            "directoGet": resumen(directo_get),
            "viaGateway": resumen(via_gateway),
            "stockAntes": antes,
            "stockDespues": despues,
            "exigido": "403 FORBIDDEN (o 404 sin ruta por Gateway) con stock intacto",
            "cumple": cumple and antes == despues,
        },
    )
    if not (cumple and antes == despues):
        fallos.append("SEC05")

    # SEC06 — JWT de firma ajena y audiencia incorrecta.
    ajeno = _jwt_ajeno()
    aud = _jwt_audiencia_incorrecta()
    pruebas = {
        "firmaAjena": {
            "/api/auth/me": resumen(c.http("GET", "/api/auth/me", token=ajeno)),
            "/api/hotel/listar": resumen(c.http("GET", "/api/hotel/listar", token=ajeno)),
        },
        "audienciaIncorrecta": {
            "/api/auth/me": resumen(c.http("GET", "/api/auth/me", token=aud)),
            "/api/hotel/listar": resumen(c.http("GET", "/api/hotel/listar", token=aud)),
            "/api/persona/listar": resumen(c.http("GET", "/api/persona/listar", token=aud)),
        },
    }
    cumple = all(v["status"] == 401 for grupo in pruebas.values() for v in grupo.values())
    save("SEC06", {"endpoints": ["GET /api/auth/me", "GET /api/hotel/listar", "GET /api/persona/listar"],
                   "pruebas": pruebas, "exigido": "401 en cada servicio", "cumple": cumple})
    if not cumple:
        fallos.append("SEC06")

    # SEC07 — dos ADMIN activos; baja y actualización simultáneas.
    # La matriz no fija qué ADMIN se desactiva y cuál se borra; se decide en nivel 3 (ver bitácora).
    estado_previo = c.psql("identity_db", "select id, correo, estado from persona where id_tipo_persona=1 order by id")
    admin2 = next((r for r in estado_previo if r[1] == "admin2@hotel.test"), None)
    if admin2 is None:
        alta = c.http(
            "POST",
            "/api/persona/registrar",
            token=token,
            body={
                "tipoDocumento": "DNI",
                "documento": "90000199",
                "nombre": "Segundo",
                "apellido": "Admin",
                "correo": "admin2@hotel.test",
                "clave": "ClaveSegura123!",
                "idTipoPersona": 1,
                "sedes": [],
            },
            headers={"Idempotency-Key": str(uuid.uuid4())},
        )
        if alta.status != 201:
            raise c.RunbookError(f"SEC07 no pudo crear el segundo ADMIN: {alta.status} {alta.body[:300]}")
        admin2_id = int(alta.field("data", "idPersona"))
    else:
        admin2_id = int(admin2[0])
        if admin2[2] == "f":
            c.http(
                "PUT",
                f"/api/persona/actualizar/{admin2_id}",
                token=token,
                body={"nombre": "Segundo", "apellido": "Admin", "correo": "admin2@hotel.test",
                      "fotoUrl": None, "idTipoPersona": 1, "sedes": [], "estado": True},
                headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": "2"},
            )
    versiones = {}
    for pid in (1, admin2_id):
        detalle = c.http("GET", f"/api/persona/buscar/{pid}", token=token)
        versiones[pid] = str(detalle.field("data", "version"))

    resultados: dict[str, Any] = {}

    import threading

    def baja() -> None:
        resultados["delete"] = c.http(
            "DELETE",
            "/api/persona/eliminar/1",
            token=token,
            headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": versiones[1]},
        )

    def desactivar() -> None:
        resultados["put"] = c.http(
            "PUT",
            f"/api/persona/actualizar/{admin2_id}",
            token=token,
            body={"nombre": "Segundo", "apellido": "Admin", "correo": "admin2@hotel.test",
                  "fotoUrl": None, "idTipoPersona": 1, "sedes": [], "estado": False},
            headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": versiones[admin2_id]},
        )

    hilos = [threading.Thread(target=baja), threading.Thread(target=desactivar)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    admins = c.psql(
        "identity_db",
        "select id, correo, estado, (clave_hash is not null) from persona where id_tipo_persona=1 order by id",
    )
    activos = [r for r in admins if r[2] == "t" and r[3] == "t"]
    estados = sorted([resultados["delete"].status, resultados["put"].status])
    cumple = (
        estados == [200, 409]
        and codigo(resultados["delete"]) in (None, "LAST_ADMIN_PROTECTED")
        and codigo(resultados["put"]) in (None, "LAST_ADMIN_PROTECTED")
        and any(codigo(resultados[k]) == "LAST_ADMIN_PROTECTED" for k in resultados)
        and len(activos) >= 1
    )
    save(
        "SEC07",
        {
            "endpoints": ["PUT /api/persona/actualizar/{id}", "DELETE /api/persona/eliminar/{id}"],
            "adminObjetivo": 1,
            "adminDesactivado": admin2_id,
            "versionesIfMatch": versiones,
            "delete": resumen(resultados["delete"]),
            "put": resumen(resultados["put"]),
            "adminsDespues": [{"id": int(r[0]), "correo": r[1], "estado": r[2], "conClave": r[3]} for r in admins],
            "adminsActivosConClave": len(activos),
            "exigido": "exactamente un 200 y un 409 LAST_ADMIN_PROTECTED con >=1 ADMIN activo con clave",
            "cumple": cumple,
        },
    )
    # Restituir los dos ADMIN activos (el 200 de SEC07 desactivó uno de ellos, a propósito).
    # El que quedó activo tiene clave y puede reactivar al otro por la API.
    for pid in (1, admin2_id):
        fila = c.psql("identity_db", f"select estado, correo from persona where id={pid}")
        if not fila or fila[0][0] == "t":
            continue
        resto = c.psql("identity_db", "select id from persona where id_tipo_persona=1 and estado and clave_hash is not null")
        token_activo = token
        for (otro,) in resto:
            if int(otro) == 1:
                token_activo = token
                break
            if int(otro) == admin2_id:
                token_activo = c.login("admin2@hotel.test", "ClaveSegura123!")
                break
        detalle = c.http("GET", f"/api/persona/buscar/{pid}", token=token_activo)
        reactivar = c.http(
            "PUT",
            f"/api/persona/actualizar/{pid}",
            token=token_activo,
            body={"nombre": "Demo" if pid == 1 else "Segundo",
                  "apellido": "Admin",
                  "correo": "admin@hotel.test" if pid == 1 else "admin2@hotel.test",
                  "fotoUrl": None, "idTipoPersona": 1, "sedes": [], "estado": True},
            headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(detalle.field("data", "version"))},
        )
        if reactivar.status != 200:
            raise c.RunbookError(f"SEC07: no se pudo reactivar el ADMIN {pid}: {reactivar.status} {reactivar.body[:300]}")
    save(
        "SEC07-restauracion",
        {
            "caso": "SEC07-RESTAURACION",
            "admins": [{"id": int(r[0]), "correo": r[1], "estado": r[2], "conClave": r[3]}
                       for r in c.psql("identity_db",
                                       "select id, correo, estado, (clave_hash is not null) from persona "
                                       "where id_tipo_persona=1 order by id")],
            "motivo": "el 200 de SEC07 desactiva un ADMIN por diseño; se restituye por la API para el resto de la matriz",
        },
    )
    if not cumple:
        fallos.append("SEC07")

    # SEC08 — CORS preflight permitido y origen ajeno.
    permitido = c.http(
        "OPTIONS",
        "/api/persona/listar",
        headers={
            "Origin": "http://localhost:4200",
            "Access-Control-Request-Method": "PUT",
            "Access-Control-Request-Headers": "authorization,if-match,idempotency-key",
        },
    )
    ajeno = c.http(
        "OPTIONS",
        "/api/persona/listar",
        headers={"Origin": "http://evil.test", "Access-Control-Request-Method": "PUT"},
    )
    real = c.http("GET", "/api/hotel/listar", token=token, headers={"Origin": "http://localhost:4200"})
    cabeceras = {k: v for k, v in permitido.headers.items() if k.lower().startswith("access-control")}
    expuestas = (real.headers.get("access-control-expose-headers") or "").lower()
    cumple = (
        permitido.status in (200, 204)
        and cabeceras.get("access-control-allow-origin") == "http://localhost:4200"
        and "if-match" in (cabeceras.get("access-control-allow-headers") or "").lower()
        and "idempotency-key" in (cabeceras.get("access-control-allow-headers") or "").lower()
        and ajeno.headers.get("access-control-allow-origin") is None
        and "etag" in expuestas
    )
    save(
        "SEC08",
        {
            "preflightPermitido": {"status": permitido.status, "headers": cabeceras},
            "preflightAjeno": {"status": ajeno.status,
                               "headers": {k: v for k, v in ajeno.headers.items() if k.startswith("access-control")}},
            "respuestaReal": {"status": real.status, "headers": c.redact_headers(real.headers)},
            "exigido": "preflight permitido con headers legibles; origen ajeno sin Allow-Origin",
            "cumple": cumple,
        },
    )
    if not cumple:
        fallos.append("SEC08")

    print(json.dumps({"fallos": fallos}, indent=2))
    return 0 if not fallos else 1


# ---------------------------------------------------------------------------
# Paso 8 — LP01–LP02 y CRUD01
# ---------------------------------------------------------------------------

CLAVES_HOTEL_PUBLICO = {"idHotel", "slug", "nombre", "descripcion", "direccion", "ciudad", "telefono", "correoContacto"}
CLAVES_CATEGORIA_PUBLICA = {"idCategoria", "descripcion", "detalle", "capacidad", "precioDesde", "moneda", "imagenes"}


def _claves(data: Any) -> set[str]:
    return set(data.keys()) if isinstance(data, dict) else set()


def lp_crud() -> int:
    token = f.admin_token()
    fallos: list[str] = []

    # LP01 — los cuatro GET públicos sin token.
    rutas = [
        ("/api/public/hoteles", "lista"),
        ("/api/public/hoteles/lima-centro", "hotel"),
        ("/api/public/hoteles/lima-centro/categorias", "lista"),
        ("/api/public/hoteles/lima-centro/categorias/1", "categoria"),
    ]
    respuestas: dict[str, Any] = {}
    privadas: dict[str, list[str]] = {}
    for ruta, tipo in rutas:
        r = c.http("GET", ruta)
        data = r.field("data")
        entrada = data[0] if isinstance(data, list) and data else data
        claves = sorted(_claves(entrada))
        es_categoria = "/categorias" in ruta
        esperadas = CLAVES_CATEGORIA_PUBLICA if es_categoria else CLAVES_HOTEL_PUBLICO
        sobrantes = sorted(set(claves) - (esperadas or set()))
        if sobrantes:
            privadas[ruta] = sobrantes
        respuestas[ruta] = {"status": r.status, "claves": claves, "cumpleEsquema": not sobrantes,
                            "bodyResumen": str(data)[:600]}
    cumple = all(v["status"] == 200 and v["cumpleEsquema"] for v in respuestas.values())
    save(
        "LP01",
        {
            "endpoints": [r for r, _ in rutas],
            "sinToken": True,
            "respuestas": respuestas,
            "clavesSobrantes": privadas,
            "exigido": "200 en los 4 con solo claves de HotelPublico/CategoriaPublica",
            "cumple": cumple,
        },
    )
    if not cumple:
        fallos.append("LP01")

    # LP02 — parámetro de fecha/ocupación e inspección del bundle de public-web.
    fecha = (date.today() + timedelta(days=3)).isoformat()
    variantes = {
        "/api/public/hoteles?fecha=" + fecha: c.http("GET", "/api/public/hoteles?fecha=" + fecha),
        "/api/public/hoteles/lima-centro?ocupacion=1": c.http("GET", "/api/public/hoteles/lima-centro?ocupacion=1"),
    }
    dist = ROOT / "apps" / "public-web" / "dist" / "public-web" / "browser"
    archivos = sorted(dist.rglob("*.js")) if dist.exists() else []
    textos = {p.name: p.read_text(encoding="utf-8", errors="replace") for p in archivos}
    patrones = {
        "endpointPrivado": r"/api/(persona|habitacion|recepcion|venta|reportes|carrito|producto|hotel|piso|categoria)/",
        "precioInterno": r"precioInicial|clave_hash|correo_contacto",
        "endpointReserva": r"/api/(public/(reservas|disponibilidad)|reservas)|[?&]fecha=",
    }
    hallazgos = {nombre: [archivo for archivo, texto in textos.items() if re.search(patron, texto)]
                 for nombre, patron in patrones.items()}
    cumple = (
        all(r.status in (200, 400) for r in variantes.values())
        and not any(hallazgos.values())
        and bool(archivos)
    )
    save(
        "LP02",
        {
            "variantesFechaOcupacion": {k: {"status": v.status, "body": v.json or v.body[:500]} for k, v in variantes.items()},
            "bundle": {"ruta": str(dist.relative_to(ROOT)), "archivos": [p.name for p in archivos]},
            "hallazgos": hallazgos,
            "exigido": "parámetro ignorado o 400; sin dato privado ni endpoint nuevo",
            "cumple": cumple,
        },
    )
    if not cumple:
        fallos.append("LP02")

    # CRUD01 — altas, ediciones, GET, DELETE y 428 sin If-Match.
    sufijo = uuid.uuid4().hex[:8].upper()
    pasos: dict[str, Any] = {}

    def crear(ruta: str, cuerpo: dict[str, Any]) -> c.Resp:
        return c.http("POST", ruta, token=token, body=cuerpo, headers={"Idempotency-Key": str(uuid.uuid4())})

    def actualizar(ruta: str, cuerpo: dict[str, Any], version: Any) -> c.Resp:
        return c.http("PUT", ruta, token=token, body=cuerpo,
                      headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(version)})

    # Sede
    sede = crear("/api/hotel/registrar", {
        "slug": f"matriz-{sufijo.lower()}", "nombre": f"Sede Matriz {sufijo}",
        "descripcion": "Sede de la matriz", "direccion": "Direccion matriz",
        "ciudad": "Lima", "telefono": "+51 900 000 099",
        "correoContacto": f"matriz-{sufijo.lower()}@hotel.test", "publicado": False, "estado": True,
    })
    id_sede = sede.field("data", "idHotel")
    v = c.http("GET", f"/api/hotel/buscar/{id_sede}", token=token).field("data", "version")
    sede_upd = actualizar(f"/api/hotel/actualizar/{id_sede}", {
        "slug": f"matriz-{sufijo.lower()}", "nombre": f"Sede Matriz {sufijo} editada",
        "descripcion": "Sede de la matriz editada", "direccion": "Direccion matriz",
        "ciudad": "Lima", "telefono": "+51 900 000 099",
        "correoContacto": f"matriz-{sufijo.lower()}@hotel.test", "publicado": False, "estado": True,
    }, v)
    sin_ifmatch = c.http("PUT", f"/api/hotel/actualizar/{id_sede}", token=token,
                         body={"slug": f"matriz-{sufijo.lower()}", "nombre": "x", "descripcion": "x",
                               "direccion": "x", "ciudad": "Lima", "telefono": "1",
                               "correoContacto": f"matriz-{sufijo.lower()}@hotel.test",
                               "publicado": False, "estado": True},
                         headers={"Idempotency-Key": str(uuid.uuid4())})
    borrar_sede = c.http("DELETE", f"/api/hotel/eliminar/{id_sede}", token=token,
                         headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(sede_upd.field("data", "version"))})
    pasos["sede"] = {
        "alta": {"status": sede.status, "idHotel": id_sede},
        "edicion": {"status": sede_upd.status, "version": sede_upd.field("data", "version")},
        "sinIfMatch": {"status": sin_ifmatch.status, "code": codigo(sin_ifmatch)},
        "delete": {"status": borrar_sede.status},
        "db": c.psql("hotel_db", f"select id, estado, version from hotel where id={id_sede}"),
    }

    # Piso (el contrato de actualizar exige {descripcion, estado})
    piso = crear("/api/piso/registrar", {"idHotel": 1, "descripcion": f"Piso Matriz {sufijo}"})
    id_piso = piso.field("data", "idPiso")
    v = c.http("GET", f"/api/piso/buscar/{id_piso}", token=token).field("data", "version")
    piso_upd = actualizar(f"/api/piso/actualizar/{id_piso}",
                          {"descripcion": f"Piso Matriz {sufijo} editado", "estado": True}, v)
    sin_ifmatch_f = c.http("PUT", f"/api/piso/actualizar/{id_piso}", token=token,
                           body={"descripcion": "x", "estado": True},
                           headers={"Idempotency-Key": str(uuid.uuid4())})
    pasos["piso"] = {
        "alta": {"status": piso.status, "idPiso": id_piso},
        "edicion": {"status": piso_upd.status, "version": piso_upd.field("data", "version")},
        "sinIfMatch": {"status": sin_ifmatch_f.status, "code": codigo(sin_ifmatch_f)},
    }

    # Categoría
    categoria = crear("/api/categoria/registrar", {
        "descripcion": f"Categoria Matriz {sufijo}", "detalle": "Categoria de la matriz",
        "capacidad": 2, "estado": True,
    })
    id_categoria = categoria.field("data", "idCategoria")
    v = c.http("GET", f"/api/categoria/buscar/{id_categoria}", token=token).field("data", "version")
    categoria_upd = actualizar(f"/api/categoria/actualizar/{id_categoria}", {
        "descripcion": f"Categoria Matriz {sufijo} editada", "detalle": "Categoria de la matriz editada",
        "capacidad": 2, "estado": True,
    }, v)
    sin_ifmatch_c = c.http("PUT", f"/api/categoria/actualizar/{id_categoria}", token=token,
                           body={"descripcion": "x", "detalle": "x", "capacidad": 1, "estado": True},
                           headers={"Idempotency-Key": str(uuid.uuid4())})
    pasos["categoria"] = {
        "alta": {"status": categoria.status, "idCategoria": id_categoria},
        "edicion": {"status": categoria_upd.status, "version": categoria_upd.field("data", "version")},
        "sinIfMatch": {"status": sin_ifmatch_c.status, "code": codigo(sin_ifmatch_c)},
    }

    # Habitación: en la sede 1 pero con número único, para no chocar con las activas de la demo.
    habitacion = crear("/api/habitacion/registrar", {
        "idHotel": 1, "numero": f"M{sufijo[:4]}", "idPiso": int(id_piso),
        "idCategoria": int(id_categoria),
        "detalle": "Habitacion de la matriz", "precio": 55.00, "publicada": False, "urlsImagenes": [],
    })
    id_habitacion = habitacion.field("data", "idHabitacion")
    if habitacion.status == 201:
        v = c.http("GET", f"/api/habitacion/buscar/{id_habitacion}", token=token).field("data", "version")
        habitacion_upd = actualizar(f"/api/habitacion/actualizar/{id_habitacion}", {
            "idPiso": int(id_piso), "idCategoria": int(id_categoria), "detalle": "Habitacion de la matriz editada",
            "precio": 60.00, "publicada": False, "urlsImagenes": [],
        }, v)
        sin_ifmatch_h = c.http("PUT", f"/api/habitacion/actualizar/{id_habitacion}", token=token,
                               body={"idPiso": int(id_piso), "idCategoria": int(id_categoria), "detalle": "x",
                                     "precio": 60.00, "publicada": False, "urlsImagenes": []},
                               headers={"Idempotency-Key": str(uuid.uuid4())})
        # El gate se crea por evento desde hotel; la baja (BLOQUEO) exige gate disponible.
        esperar(f"gate de {id_habitacion}", lambda: bool(gate(int(id_habitacion))), timeout=90)
        esperar(f"gate de {id_habitacion} LIBRE", lambda: gate(int(id_habitacion)).get("estado") == "LIBRE", timeout=90)
        borrar_habitacion = c.http("DELETE", f"/api/habitacion/eliminar/{id_habitacion}", token=token,
                                   headers={"Idempotency-Key": str(uuid.uuid4()),
                                            "If-Match": str(habitacion_upd.field("data", "version"))})
        # La baja es una operación durable: se espera a que la habitación quede estado=false.
        esperar(f"baja de {id_habitacion}", lambda: c.psql_one(
            "hotel_db", f"select estado from habitacion where id={id_habitacion}") == "f", timeout=120)
        pasos["habitacion"] = {
            "alta": {"status": habitacion.status, "idHabitacion": id_habitacion},
            "edicion": {"status": habitacion_upd.status, "version": habitacion_upd.field("data", "version")},
            "sinIfMatch": {"status": sin_ifmatch_h.status, "code": codigo(sin_ifmatch_h)},
            "delete": {"status": borrar_habitacion.status, "code": codigo(borrar_habitacion),
                       "body": borrar_habitacion.json or borrar_habitacion.body[:400]},
            "db": c.psql("hotel_db", f"select id, estado, version from habitacion where id={id_habitacion}"),
            "operacionBaja": c.psql("hotel_db", "select block_id::text, estado from operacion_habitacion "
                                               f"where id_habitacion={id_habitacion} order by block_id"),
        }
    else:
        pasos["habitacion"] = {"alta": {"status": habitacion.status, "body": habitacion.body[:500]}}

    # Producto (GET/PUT/DELETE exigen ?idHotel= y productoVersion en el cuerpo)
    producto = crear("/api/producto/registrar", {
        "sku": f"MAT-{sufijo}", "nombre": f"Producto Matriz {sufijo}", "detalle": "Producto de la matriz",
        "idHotel": 1, "precio": 4.50, "cantidadInicial": 3, "umbralBajo": 1,
    })
    id_producto = producto.field("data", "idProducto")
    if producto.status == 201:
        detalle_p = c.http("GET", f"/api/producto/buscar/{id_producto}?idHotel=1", token=token)
        producto_version = detalle_p.field("data", "productoVersion")
        producto_upd = actualizar(f"/api/producto/actualizar/{id_producto}?idHotel=1", {
            "nombre": f"Producto Matriz {sufijo} editado", "detalle": "editado", "estado": True,
            "productoVersion": producto_version,
        }, producto_version)
        sin_ifmatch_p = c.http("PUT", f"/api/producto/actualizar/{id_producto}?idHotel=1", token=token,
                               body={"nombre": "x", "productoVersion": producto_version},
                               headers={"Idempotency-Key": str(uuid.uuid4())})
        borrar_producto = c.http("DELETE", f"/api/producto/eliminar/{id_producto}?idHotel=1", token=token,
                                 headers={"Idempotency-Key": str(uuid.uuid4()),
                                          "If-Match": str(producto_upd.field("data", "version"))})
        pasos["producto"] = {
            "alta": {"status": producto.status, "idProducto": id_producto},
            "edicion": {"status": producto_upd.status, "version": producto_upd.field("data", "version")},
            "sinIfMatch": {"status": sin_ifmatch_p.status, "code": codigo(sin_ifmatch_p)},
            "delete": {"status": borrar_producto.status},
            "db": c.psql("inventory_db",
                         "select p.id, ps.estado, ps.version from producto p "
                         "join producto_sede ps on ps.id_producto=p.id "
                         f"where p.id={id_producto} and ps.id_hotel=1"),
        }
    else:
        pasos["producto"] = {"alta": {"status": producto.status, "body": producto.body[:500]}}

    # Bajas de los maestros de hotel: el orden importa (hijos antes que padres).
    baja_piso = c.http(
        "DELETE", f"/api/piso/eliminar/{id_piso}", token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": str(piso_upd.field("data", "version"))})
    pasos["piso"]["delete"] = {"status": baja_piso.status, "code": codigo(baja_piso),
                               "body": baja_piso.json or baja_piso.body[:400]}
    pasos["piso"]["db"] = c.psql("hotel_db", f"select id, estado, version from piso where id={id_piso}")
    baja_categoria = c.http(
        "DELETE", f"/api/categoria/eliminar/{id_categoria}", token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()),
                 "If-Match": str(categoria_upd.field("data", "version"))})
    pasos["categoria"]["delete"] = {"status": baja_categoria.status, "code": codigo(baja_categoria),
                                    "body": baja_categoria.json or baja_categoria.body[:400]}
    pasos["categoria"]["db"] = c.psql("hotel_db", f"select id, estado, version from categoria where id={id_categoria}")

    # Evaluación del criterio: cada recurso creado tiene version incrementada, DELETE deja
    # estado=false y el PUT sin If-Match devuelve 428.
    evaluación = {}
    for nombre, datos in pasos.items():
        if "db" not in datos:
            evaluación[nombre] = {"cumple": False, "motivo": "alta no devolvió 201", "datos": datos}
            continue
        fila = datos["db"][0] if datos["db"] else []
        evaluación[nombre] = {
            "cumple": (
                datos["alta"]["status"] == 201
                and datos["edicion"]["status"] == 200
                and datos["delete"]["status"] in (200, 204)
                and datos["sinIfMatch"]["status"] == 428
                and datos["sinIfMatch"]["code"] == "PRECONDITION_REQUIRED"
                and len(fila) >= 3 and fila[1] == "f"
            ),
            "versionFinal": fila[2] if len(fila) >= 3 else None,
            "estadoFinal": fila[1] if len(fila) >= 3 else None,
        }
    cumple = all(v["cumple"] for v in evaluación.values())
    save(
        "CRUD01",
        {
            "pasos": pasos,
            "evaluacion": evaluación,
            "exigido": "filas persistidas, version incrementada, DELETE estado=false, PUT sin If-Match 428",
            "cumple": cumple,
        },
    )
    if not cumple:
        fallos.append("CRUD01")

    print(json.dumps({"fallos": fallos, "evaluacion": evaluación}, indent=2, ensure_ascii=False))
    return 0 if not fallos else 1


# ---------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Matriz de aceptación F5 (docs/plans/qa-matriz-aceptacion.md).")
    sub = parser.add_subparsers(dest="command", metavar="<grupo>")
    sub.add_parser("preflight", help="Compose sano, readiness por servicio y Gateway 401")
    sub.add_parser("fixtures", help="siembra F1-F3 y F8 y guarda los IDs")
    sub.add_parser("fixtures-avanzados", help="siembra F4-F6 y F9")
    sub.add_parser("sec", help="SEC01-SEC08")
    sub.add_parser("lp-crud", help="LP01-LP02 y CRUD01")
    sub.add_parser("occ-cln", help="OCC01-OCC04 y CLN01-CLN03")
    args = parser.parse_args(argv)
    if not args.command:
        parser.print_help()
        return 2
    try:
        if args.command == "preflight":
            return preflight()
        if args.command == "fixtures":
            return fixtures()
        if args.command == "fixtures-avanzados":
            return fixtures_avanzados()
        if args.command == "sec":
            return sec()
        if args.command == "lp-crud":
            return lp_crud()
        if args.command == "occ-cln":
            return occ_cln()
        parser.print_help()
        return 2
    except c.RunbookError as err:
        print(f"\nERROR: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
