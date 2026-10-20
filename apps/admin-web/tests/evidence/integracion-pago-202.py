"""Integración del 202 de pago con recuperación real por el Gateway.

Detiene temporalmente reception-service para que la admisión de la operación
PAGO no se confirme en el request: sales responde 202 con Location/Retry-After
y deja la operación durable. Al volver reception, el recuperador de sales aplica
el pago y el polling a Location llega al terminal. No usa mocks.

Uso (desde la raíz del repositorio):
    python apps/admin-web/tests/evidence/integracion-pago-202.py >> apps/admin-web/tests/evidence/front-admin-web-ventas-integracion.txt
"""

from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))

from scripts.qa import common as c  # noqa: E402


def mostrar(etiqueta: str, resp: c.Resp, extra: str = "") -> None:
    cuerpo = resp.body if len(resp.body) <= 1400 else resp.body[:1400] + "…"
    print(f"\n[{etiqueta}] HTTP {resp.status}{(' · ' + extra) if extra else ''}")
    print(f"  {cuerpo}")


def main() -> int:
    token = c.login("admin@hotel.test", c.env("DEMO_PASSWORD"))
    id_recepcion = 22

    print("\n=== 202 de pago con recuperación real (reception-service detenido) ===")
    venta = c.http(
        "POST",
        "/api/venta",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "estado": "PENDIENTE",
            "detalles": [{"idProducto": 2, "cantidad": 1}],
        },
    )
    mostrar("venta crear para pago 202", venta)
    id_venta = venta.field("data", "idVenta")
    detalle = c.http("GET", f"/api/venta/buscar/{id_venta}", token=token)
    version = detalle.field("data", "version")
    if_match = f'"{version}"'

    print("\nDeteniendo reception-service para que la admisión no se confirme…")
    c.stop_service("reception-service")
    print("reception-service detenido; puerto 8083 cerrado.")

    pago = c.http(
        "PUT",
        f"/api/venta/{id_venta}",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": if_match},
        body={"estado": "PAGADO", "medioPago": "TARJETA"},
        timeout=30,
    )
    mostrar(
        "pago con reception caído (202 esperado)",
        pago,
        f"Location={pago.headers.get('location')} Retry-After={pago.headers.get('retry-after')}",
    )
    location = pago.headers.get("location")

    print("\nReiniciando reception-service…")
    c.start_service("reception-service", wait=True, timeout=240)
    print("reception-service listo otra vez.")

    if not location:
        print("Sin Location no se puede sondear; el 202 no se dio.")
        return 1

    for intento in range(1, 31):
        time.sleep(3)
        seguimiento = c.http("GET", location, token=token)
        print(
            f"[polling pago 202 {intento}] HTTP {seguimiento.status} · estado="
            f"{seguimiento.field('data', 'estado')} estadoOperacion="
            f"{seguimiento.field('data', 'estadoOperacion')} paidAt="
            f"{seguimiento.field('data', 'paidAt')}"
        )
        if seguimiento.field("data", "estado") == "PAGADO":
            mostrar("pago 202 terminal", seguimiento)
            print(json.dumps({"idVenta": id_venta, "resultado": "PAGADO tras 202"}, ensure_ascii=False))
            return 0

    print("El pago no llegó a PAGADO en el tiempo de sondeo.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
