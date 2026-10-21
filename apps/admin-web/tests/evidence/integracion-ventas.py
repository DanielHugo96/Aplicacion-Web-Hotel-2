"""Integración real del módulo de ventas del panel por el Gateway.

Ejecuta el ciclo completo de productos, movimientos, carrito, venta, pago y
anulación contra `http://localhost:8080` con los servicios de la campaña
levantados. No usa mocks ni puertos de servicios: todo pasa por el Gateway.

Uso (desde la raíz del repositorio):
    python apps/admin-web/tests/evidence/integracion-ventas.py > apps/admin-web/tests/evidence/front-admin-web-ventas-integracion.txt
"""

from __future__ import annotations

import json
import sys
import threading
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))

from scripts.qa import common as c  # noqa: E402


def mostrar(etiqueta: str, resp: c.Resp, extra: str | None = None) -> None:
    cuerpo = resp.body
    if len(cuerpo) > 1200:
        cuerpo = cuerpo[:1200] + "…"
    print(f"\n[{etiqueta}] HTTP {resp.status}")
    if extra:
        print(f"  {extra}")
    print(f"  {cuerpo}")


def etag(resp: c.Resp) -> str | None:
    return resp.headers.get("etag")


def if_match(resp: c.Resp) -> str | None:
    """Productos devuelven ETag; ventas exponen `version` y el front la cita."""
    directo = etag(resp)
    if directo:
        return directo
    version = resp.field("data", "version")
    return f'"{version}"' if version is not None else None


def main() -> int:
    token = c.login("admin@hotel.test", c.env("DEMO_PASSWORD"))
    print("Integración front-admin-web-ventas por el Gateway http://localhost:8080")
    print("Token de administrador obtenido con /api/auth/login (sin imprimir la credencial).")

    sufijo = uuid.uuid4().hex[:8].upper()
    sku = f"INT-{sufijo}"
    clave = str(uuid.uuid4())

    # ------------------------------------------------------------- productos
    alta = c.http(
        "POST",
        "/api/producto/registrar",
        token=token,
        headers={"Idempotency-Key": clave},
        body={
            "sku": sku,
            "nombre": f"Producto integración {sufijo}",
            "detalle": "Alta real del plan front-admin-web-ventas",
            "imagenUrl": "/assets/demo/producto-agua.png",
            "idHotel": 1,
            "precio": 9.99,
            "cantidadInicial": 5,
            "umbralBajo": 2,
        },
    )
    mostrar("producto/registrar", alta)
    if alta.status != 201:
        return 1
    id_producto = alta.field("data", "idProducto")

    buscar = c.http("GET", f"/api/producto/buscar/{id_producto}?idHotel=1", token=token)
    mostrar("producto/buscar", buscar, f"ETag={etag(buscar)}")
    version_producto = buscar.field("data", "productoVersion")

    editar = c.http(
        "PUT",
        f"/api/producto/actualizar/{id_producto}?idHotel=1",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": etag(buscar)},
        body={
            "nombre": f"Producto integración {sufijo} editado",
            "detalle": "Edición real con If-Match",
            "imagenUrl": "/assets/demo/producto-agua.png",
            "precio": 11.5,
            "umbralBajo": 3,
            "estado": True,
            "productoVersion": version_producto,
        },
    )
    mostrar("producto/actualizar (200)", editar, f"ETag={etag(editar)}")

    asignar = c.http(
        "POST",
        f"/api/producto/{id_producto}/sedes",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={"idHotel": 2, "precio": 12.0, "cantidadInicial": 4, "umbralBajo": 2},
    )
    mostrar("producto/{id}/sedes (201)", asignar)

    buscar2 = c.http("GET", f"/api/producto/buscar/{id_producto}?idHotel=1", token=token)
    ajuste = c.http(
        "POST",
        f"/api/producto/{id_producto}/ajustes",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": etag(buscar2)},
        body={"idHotel": 1, "delta": 2, "motivo": "Reposición de integración"},
    )
    mostrar("producto/{id}/ajustes (201)", ajuste)

    hoy = "2026-10-20T00:00:00Z"
    manana = "2026-10-21T00:00:00Z"
    movimientos = c.http(
        "GET",
        f"/api/producto/{id_producto}/movimientos?idHotel=1&page=0&size=20&inicio={hoy}&fin={manana}",
        token=token,
    )
    mostrar("producto/{id}/movimientos", movimientos)

    buscar3 = c.http("GET", f"/api/producto/buscar/{id_producto}?idHotel=1", token=token)
    baja = c.http(
        "DELETE",
        f"/api/producto/eliminar/{id_producto}?idHotel=1",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": etag(buscar3)},
    )
    mostrar("producto/eliminar (200)", baja, f"ETag={etag(baja)}")

    # El arreglo de inventory filtra por producto_sede.estado, así que la baja
    # ya se alcanza con el filtro de dados de baja.
    inactivos = c.http(
        "GET",
        "/api/producto/listar?idHotel=1&page=0&size=100&estado=false",
        token=token,
    )
    fila = next(
        (p for p in (inactivos.field("data") or []) if p["idProducto"] == id_producto), None
    )
    print(
        "\n[producto/listar estado=false tras la baja] HTTP",
        inactivos.status,
        "· fila:",
        json.dumps(fila, ensure_ascii=False) if fila else "no encontrada",
    )

    buscar4 = c.http("GET", f"/api/producto/buscar/{id_producto}?idHotel=1", token=token)
    version_baja = buscar4.field("data", "productoVersion")
    reactivar = c.http(
        "PUT",
        f"/api/producto/actualizar/{id_producto}?idHotel=1",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": etag(buscar4)},
        body={"estado": True, "productoVersion": version_baja},
    )
    mostrar("producto/actualizar reactivación (200)", reactivar)

    # ---------------------------------------------------- carrito y catálogo
    id_recepcion = 22
    catalogo = c.http(
        "GET",
        f"/api/venta/catalogo?idRecepcion={id_recepcion}&page=0&size=5",
        token=token,
    )
    mostrar("venta/catalogo", catalogo)

    carrito_inicial = c.http("GET", f"/api/carrito/listar/{id_recepcion}", token=token)
    mostrar("carrito/listar (inicial)", carrito_inicial)

    agregar = c.http(
        "POST",
        f"/api/carrito/agregar?idRecepcion={id_recepcion}&idProducto={id_producto}&cantidad=2",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    mostrar("carrito/agregar", agregar)
    carrito = c.http("GET", f"/api/carrito/listar/{id_recepcion}", token=token)
    mostrar("carrito/listar (tras agregar)", carrito)

    quitar = c.http(
        "DELETE",
        f"/api/carrito/eliminar?idRecepcion={id_recepcion}&idProducto={id_producto}",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    print(f"\n[carrito/eliminar] HTTP {quitar.status} (204 sin cuerpo)")

    # ---------------------------------------------------------------- ventas
    venta1 = c.http(
        "POST",
        "/api/venta",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "estado": "PENDIENTE",
            "detalles": [{"idProducto": id_producto, "cantidad": 2}],
        },
    )
    mostrar("venta crear PENDIENTE (201)", venta1, f"Location={venta1.headers.get('location')}")
    id_venta1 = venta1.field("data", "idVenta")

    detalle1 = c.http("GET", f"/api/venta/buscar/{id_venta1}", token=token)
    mostrar("venta/buscar (detalle)", detalle1, f"ETag={etag(detalle1)}")

    pago = c.http(
        "PUT",
        f"/api/venta/{id_venta1}",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": if_match(detalle1)},
        body={"estado": "PAGADO", "medioPago": "EFECTIVO"},
    )
    mostrar("pago 200", pago, f"Location={pago.headers.get('location')}")

    # Segunda venta impaga para anulación (202) y consulta de operación.
    venta2 = c.http(
        "POST",
        "/api/venta",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "estado": "PENDIENTE",
            "detalles": [{"idProducto": id_producto, "cantidad": 1}],
        },
    )
    mostrar("venta crear PENDIENTE para anular (201)", venta2)
    id_venta2 = venta2.field("data", "idVenta")
    detalle2 = c.http("GET", f"/api/venta/buscar/{id_venta2}", token=token)

    anular = c.http(
        "DELETE",
        f"/api/venta/{id_venta2}?motivo=Anulacion%20de%20integracion",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": if_match(detalle2)},
    )
    mostrar(
        "anulación impagada (202)",
        anular,
        f"Location={anular.headers.get('location')} Retry-After={anular.headers.get('retry-after')}",
    )
    location = anular.headers.get("location")
    if location:
        for intento in range(1, 16):
            time.sleep(2)
            seguimiento = c.http("GET", location, token=token)
            print(
                f"\n[polling {intento}] HTTP {seguimiento.status} · estadoOperacion="
                f"{seguimiento.field('data', 'estadoOperacion')}"
            )
            if seguimiento.field("data", "estadoOperacion") in {
                "CANCELADA",
                "CONFIRMADA",
                "RECHAZADA",
            }:
                mostrar("polling terminal", seguimiento)
                break

    anular_otra_vez = c.http(
        "DELETE",
        f"/api/venta/{id_venta2}?motivo=Anulacion%20repetida",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": if_match(detalle2)},
    )
    mostrar("anulación repetida (200 ya cancelada)", anular_otra_vez)

    detalle_pagada = c.http("GET", f"/api/venta/buscar/{id_venta1}", token=token)
    anular_pagada = c.http(
        "DELETE",
        f"/api/venta/{id_venta1}?motivo=No%20corresponde",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4()), "If-Match": if_match(detalle_pagada)},
    )
    mostrar("anulación de pagada (409 SALE_ALREADY_PAID)", anular_pagada)

    # Listados y operación consultada, como los usa el panel.
    ventas = c.http("GET", "/api/venta?idHotel=1&page=0&size=10", token=token)
    mostrar("venta listar (panel)", ventas)
    try:
        operation_id = json.loads(detalle2.body)["data"]["operationId"]
        operacion = c.http("GET", f"/api/venta/operaciones/{operation_id}", token=token)
        mostrar("venta/operaciones/{operationId}", operacion)
    except Exception as exc:  # pragma: no cover - evidencia
        print("No se pudo consultar la operación:", exc)

    print("\nFin de la integración real.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
