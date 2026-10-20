"""Simulacro de venta en recuperación (CANCELACION_PENDIENTE > 30min)."""

from __future__ import annotations

import time

from . import common as c
from . import fixtures as f
from . import alerts


def run() -> None:
    sim = "ventas-recuperacion"
    c.runbook_step("Ventas en recuperación: F7 con inventory detenido")
    token = f.admin_token()
    f.free_room(101, 1)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=0.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"check-in de F7 devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])

    c.stop_service("inventory-service")
    time.sleep(2)
    sale, venta = f.create_sale(
        id_recepcion=id_recepcion,
        items=[{"idProducto": 1, "cantidad": 2}],
        token=token,
    )
    if sale.status != 202:
        c.start_service("inventory-service")
        raise c.RunbookError(f"venta con inventory caído devolvió {sale.status}: {sale.body[:500]}")
    id_venta = int(venta.get("idVenta", 0))
    c.write_json(
        sim,
        "01-venta-202.json",
        {
            "idRecepcion": id_recepcion,
            "idVenta": id_venta,
            "status": sale.status,
            "headers": c.redact_headers(sale.headers),
            "body": sale.json,
        },
    )

    linea = []
    found = None
    started = time.time()
    while time.time() - started < 60 * 36:
        found = next(a for a in alerts.collect() if a["alerta"] == "Ventas en recuperación")
        linea.append({"ts": c.iso(), "valor": found["valor"], "dispara": found["dispara"]})
        if found["dispara"]:
            break
        time.sleep(30)
    c.write_json(sim, "02-alerta-ventas.json", {"linea": linea, "alerta": found})
    if not found or not found["dispara"]:
        c.start_service("inventory-service")
        raise c.RunbookError("la alerta de ventas en recuperación no disparó en 30min")

    c.start_service("inventory-service")
    venta_final = c.wait_until(
        lambda: c.psql(
            "sales_db",
            "select id, estado_operacion, COALESCE(paid_at::text,'') from venta "
            f"where id={id_venta} and estado_operacion='CANCELADA'",
        ),
        timeout=180,
        interval=5,
        description="venta CANCELADA",
    )
    movimientos = c.psql(
        "inventory_db",
        "select id_venta, id_hotel, estado, COALESCE(resultado::text,''), created_at::text, updated_at::text "
        f"from movimiento_venta where id_venta={id_venta}",
    )
    detalle = []
    if movimientos:
        detalle = c.psql(
            "inventory_db",
            f"select id_producto, cantidad from movimiento_detalle where id_venta={id_venta}",
        )
    stock = c.psql("inventory_db", "select id_producto, cantidad from producto_sede where id_hotel=1 and id_producto=1")
    c.write_json(
        sim,
        "03-recuperacion.json",
        {
            "ventaFinal": {
                "idVenta": venta_final[0][0] if venta_final else None,
                "estadoOperacion": venta_final[0][1] if venta_final else None,
                "paidAt": venta_final[0][2] if venta_final else None,
            },
            "movimientos": [
                {
                    "idVenta": r[0],
                    "idHotel": r[1],
                    "estado": r[2],
                    "resultado": r[3],
                    "createdAt": r[4],
                    "updatedAt": r[5],
                }
                for r in movimientos
            ],
            "detalle": [{"idProducto": r[0], "cantidad": r[1]} for r in detalle],
            "stock": [{"idProducto": r[0], "cantidad": r[1]} for r in stock],
            "unMovimiento": len(movimientos) == 1 and movimientos[0][2] == "RESTITUIDO",
            "alerta": next(a for a in alerts.collect() if a["alerta"] == "Ventas en recuperación"),
        },
    )
    if not movimientos or movimientos[0][2] != "RESTITUIDO":
        raise c.RunbookError(f"compensación inesperada: {movimientos}")
    print("  venta CANCELADA con un movimiento RESTITUIDO y alerta apagada")
