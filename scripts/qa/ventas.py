"""Simulacro de venta en recuperación (CANCELACION_PENDIENTE > 30min).

El «débito incierto» se reproduce con un proxy TCP que acepta la conexión de Sales y no
responde: Feign agota su read timeout y Sales pasa a CANCELACION_PENDIENTE sin confirmar. No
se toca inventory por SQL ni se fuerza el estado de la venta.
"""

from __future__ import annotations

import http.server
import socket
import threading
import time
import urllib.error
import urllib.request
from typing import Any

from . import common as c
from . import fixtures as f
from . import alerts

PROXY_PORT = 18084
INVENTORY = "http://127.0.0.1:8084"


class SelectiveProxy:
    """Proxy HTTP mínimo delante de inventory.

    Reenvía el catálogo (`GET /internal/stock/productos`) al inventory real y deja
    colgada la petición de débito (`POST /internal/stock/descontar`) sin responder,
    para que Sales agote su read timeout y pase a CANCELACION_PENDIENTE. No toca
    datos ni modifica el servicio.
    """

    def __init__(self, port: int = PROXY_PORT) -> None:
        self.port = port
        self.requests: list[str] = []
        outer = self

        class Handler(http.server.BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def log_message(self, *_: Any) -> None:
                return

            def do_GET(self) -> None:  # noqa: N802
                outer.requests.append(f"GET {self.path}")
                outer._forward(self)

            def do_POST(self) -> None:  # noqa: N802
                outer.requests.append(f"POST {self.path}")
                if "/descontar" in self.path:
                    # El cliente espera su read timeout; se cierra sin respuesta.
                    time.sleep(12)
                    self.close_connection = True
                    return
                outer._forward(self)

        self._server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)

    @staticmethod
    def _forward(handler: http.server.BaseHTTPRequestHandler) -> None:
        length = int(handler.headers.get("Content-Length", "0") or 0)
        body = handler.rfile.read(length) if length else None
        headers = {
            key: value
            for key, value in handler.headers.items()
            if key.lower() not in ("host", "content-length", "connection")
        }
        req = urllib.request.Request(
            INVENTORY + handler.path,
            data=body,
            headers=headers,
            method=handler.command,
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                payload = resp.read()
                handler.send_response(resp.status)
                for key, value in resp.headers.items():
                    if key.lower() not in ("transfer-encoding", "connection", "content-length"):
                        handler.send_header(key, value)
                handler.send_header("Content-Length", str(len(payload)))
                handler.end_headers()
                handler.wfile.write(payload)
        except urllib.error.HTTPError as err:
            payload = err.read()
            handler.send_response(err.code)
            handler.send_header("Content-Length", str(len(payload)))
            handler.end_headers()
            handler.wfile.write(payload)
        except Exception:
            handler.send_response(503)
            handler.send_header("Content-Length", "0")
            handler.end_headers()

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._server.shutdown()
        self._server.server_close()
        self._thread.join(timeout=3)


def run() -> None:
    sim = "ventas-recuperacion"
    c.runbook_step("Ventas en recuperación: F7 con débito incierto")
    token = f.admin_token()
    f.free_room(101, 1)
    check, stay = f.checkin(room_id=101, hotel_id=1, cliente=100, adelanto=0.0, token=token)
    if check.status != 201:
        raise c.RunbookError(f"check-in de F7 devolvió {check.status}: {check.body[:400]}")
    id_recepcion = int(stay["idRecepcion"])
    c.write_json(sim, "00-fixture-f7.json", {"idRecepcion": id_recepcion, "checkin": check.summary()})

    proxy = SelectiveProxy()
    proxy.start()
    try:
        c.stop_service("sales-service")
        c.start_service(
            "sales-service",
            timeout=180,
            env_overrides={"FEIGN_URL_INVENTORY": f"http://127.0.0.1:{PROXY_PORT}"},
            force=True,
        )
        # Inventory debe estar arriba para el lookup de productos; el proxy solo cuelga
        # el POST de débito. Se detiene después de recibir el 202 para que la
        # compensación (`stock.restituir`) no complete durante la espera de 30min.
        c.start_service("inventory-service", timeout=180)
        time.sleep(2)
        started = time.time()
        sale, venta = f.create_sale(
            id_recepcion=id_recepcion,
            items=[{"idProducto": 1, "cantidad": 2}],
            token=token,
            base="http://localhost:8085",
        )
        elapsed = int((time.time() - started) * 1000)
        c.stop_service("inventory-service")
        time.sleep(2)
    finally:
        proxy.stop()
        c.stop_service("sales-service")
        c.start_service("sales-service", timeout=180, force=True)
    id_venta = int(venta.get("idVenta", 0))
    c.write_json(
        sim,
        "01-venta-202.json",
        {
            "idRecepcion": id_recepcion,
            "idVenta": id_venta,
            "status": sale.status,
            "duracionMs": elapsed,
            "headers": c.redact_headers(sale.headers),
            "body": sale.json,
            "estadoOperacion": venta.get("estadoOperacion"),
            "proxy": f"127.0.0.1:{PROXY_PORT} acepta y no responde",
        },
    )
    if sale.status != 202 or venta.get("estadoOperacion") != "CANCELACION_PENDIENTE":
        c.start_service("inventory-service")
        raise c.RunbookError(f"venta con débito incierto devolvió {sale.status}: {sale.body[:500]}")

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
        timeout=240,
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
