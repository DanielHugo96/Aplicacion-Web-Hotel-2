# Evidencia de integración real — front-admin-web-area-cliente

Fecha: 2026-10-20 (America/Lima). Gateway `http://localhost:8080` con `identity-service` (8081),
`hotel-service` (8082), `reception-service` (8083), `inventory-service` (8084) y `sales-service`
(8085) arriba y registrados en Eureka. La app solo llama al Gateway: ningún endpoint de servicio
ni broker.

La sesión del navegador se hizo con `cliente.uno@hotel.test` (idPersona 100); la clave usada fue
temporal y se restauró al terminar. No se guardan tokens ni claves en este archivo.

## Mi estadía

- `GET /api/recepcion/cliente/100?page=0&size=100` → **HTTP 200**; `meta.page=0`, `meta.size=100`,
  `meta.totalElements=13`; tres estadías `ACTIVA` (ids 3, 4 y 7) y el resto `CERRADA`.
- `GET /api/recepcion/3` → **HTTP 200**, `ETag: "1"`, `estadoEstadia: ACTIVA`, habitación `101`,
  `precioHabitacion: 80.00`, `medioPagoAdelanto: null`.
- La pantalla muestra `19/10/2026`, `20/10/2026`, `S/ 80.00`, `No registrado` y `Activa`.
- Capturas: `01-mi-estadia-1024.png` y `02-mi-estadia-390.png`.

## Catálogo y carrito

- `GET /api/venta/catalogo?idRecepcion=3&page=0&size=12` → **HTTP 200**, 3 productos:
  `Agua 500ml` `S/ 3.50`, `Snack` `S/ 8.00`, `Kit de aseo` `S/ 5.00`.
- `POST /api/carrito/agregar?idRecepcion=3&idProducto=1&cantidad=2` → **HTTP 200** (upsert).
- `POST /api/carrito/agregar?idRecepcion=3&idProducto=1&cantidad=1` → **HTTP 200**, cantidad 3.
- `GET /api/carrito/listar/3` → **HTTP 200** con `idProducto`/`cantidad`/`precioUnitario`.
- `DELETE /api/carrito/eliminar?idRecepcion=3&idProducto=3` → **HTTP 204**; la línea desaparece del
  listado.
- Capturas: `03-catalogo-1024.png`, `04-catalogo-390.png`, `05-carrito-1024.png`,
  `11-carrito-390.png`.

## Venta PENDIENTE (201 y 202)

- `POST /api/venta` con
  `{"idRecepcion":3,"estado":"PENDIENTE","detalles":[{"idProducto":1,"cantidad":3},{"idProducto":2,"cantidad":1}]}`
  → **HTTP 201**, `Location: /api/venta/buscar/21`, `Retry-After: 2`, `total: 18.50`,
  `estado: PENDIENTE`, `estadoOperacion: CONFIRMADA`. El cuerpo no lleva `idHotel`, `idCliente`,
  precios, subtotales ni `estadoOperacion`; esos campos los calcula el backend.
- `POST /api/venta` con
  `{"idRecepcion":3,"estado":"PENDIENTE","detalles":[{"idProducto":1,"cantidad":1}]}`
  → **HTTP 202** en 0,34 s, `Location: /api/venta/buscar/20`, `Retry-After: 2`, venta
  `estadoOperacion: RECHAZADA`.
- Sondeo real de `Location`:
  `GET /api/venta/buscar/20` → **HTTP 200**, `estado: PENDIENTE`, `estadoOperacion: RECHAZADA`
  (terminal). El stock de los tres productos de la sede 3 quedó igual antes y después
  (`{1: 20, 2: 10, 3: 5}`), porque la admisión falló antes del débito.
- Causa del 202: se inyectó un fallo controlado y temporal en la admisión de reception (trigger
  `fail_admision_evidencia` en `reception_db`, solo para la estadía 3 y tipo VENTA) para ejercitar
  la compensación de sales. El trigger se eliminó al terminar; no queda en la base.
- Observación de entorno: con reception caída del todo, el `POST /api/venta` responde **503**
  porque sales no puede leer la estadía antes de crear la venta. El 202 con `Location` aparece por
  el camino de admisión incierta/fallida, que es el que se ejerció.

## Mis ventas y detalle

- `GET /api/venta/recepcion/3?page=0&size=20` → **HTTP 200** con las ventas de la estadía 3.
- La pantalla agregó las ventas de las 13 estadías propias y mostró
  `Página 1 de 2 · 21 registros`; en cada fila van separadas las píldoras `Pago pendiente`/
  `Pagado` y `Confirmada`/`Rechazada`/`Anulada`/`En proceso`.
- `GET /api/venta/buscar/21` → **HTTP 200**, `idCliente: 100`, `total: 18.50`, detalle con
  `Agua 500ml` ×3 (`S/ 10.50`) y `Snack` ×1 (`S/ 8.00`), `medioPago: null`.
- `GET /api/venta/operaciones/{operationId}` → **HTTP 200**, `estado: TERMINAL`,
  `estadoOperacion: CONFIRMADA`.
- Capturas: `06-mis-ventas-1024.png`, `07-mis-ventas-390.png`, `08-venta-detalle-1024.png`,
  `12-venta-detalle-390.png`.

## Perfil y clave

- `GET /api/auth/me` → **HTTP 200**, `ETag: "5"`, persona 100.
- `PUT /api/auth/me` con `If-Match: "99"` → **HTTP 412** `PRECONDITION_FAILED`.
- `PUT /api/auth/me` sin `If-Match` → **HTTP 428** `PRECONDITION_REQUIRED`.
- `PUT /api/auth/me` con `If-Match: "5"` y `{nombre, apellido, fotoUrl}` → **HTTP 200**,
  `ETag: "6"`, `version: 6`. El body no incluye rol, sede, documento ni estado.
- `PUT /api/auth/password` con la actual incorrecta → **HTTP 400**
  `CURRENT_PASSWORD_MISMATCH`, `fields.claveActual`.
- `PUT /api/auth/password` con la actual correcta y nueva de 5 caracteres → **HTTP 400**
  `PASSWORD_POLICY`, `fields.claveNueva`.
- `PUT /api/auth/password` a una clave temporal → **HTTP 200** (`data: null`); login con la
  temporal → **HTTP 200**; `PUT` de vuelta a la clave original → **HTTP 200**; login con la
  original → **HTTP 200**.
- Capturas: `09-mi-perfil-1024.png` y `10-mi-perfil-390.png`.

## Límites del rol C y anti-enumeración

- `GET /api/hotel/listar?page=0&size=5` con token CLIENTE → **HTTP 403** `FORBIDDEN`.
- `GET /api/venta/buscar/999999` con token CLIENTE → **HTTP 404** `RESOURCE_NOT_FOUND`.
- `GET /api/carrito/listar/5` con token CLIENTE → **HTTP 404** `RESOURCE_NOT_FOUND`.
- El shell de cliente no tiene enlaces ni acciones de personas, sedes, habitaciones, limpieza,
  recepción, productos, ventas administrativas ni reportes.

## Cabeceras y red del navegador

- `POST /api/venta` (201) desde Chrome incluye `idempotency-key`,
  `x-correlation-id`, `authorization` y `content-type: application/json`; **no** incluye
  `X-User-Id`, `X-Role` ni `X-Hotel-Id`.
- La lista de red del navegador (24 peticiones XHR/fetch) apunta **todas** a
  `http://localhost:8080`; no hay llamadas a `8081`–`8085` ni a brokers.
- La respuesta expone `ETag`, `Location`, `Retry-After` y `X-Correlation-Id` por CORS.

## Comandos canónicos

En `apps/admin-web`:

```text
$ npm ci
(ver salida en la sección de cierre del plan)

$ npm run build
Application bundle generation complete.

$ npm test -- --watch=false
Test Files  20 passed (20)
Tests       74 passed (74)
```

En la raíz:

```text
$ python scripts/validar-planes.py
0 error(es), 0 aviso(s)

$ python scripts/validar-contratos.py --base origin/main
Los contratos solo se han ampliado. Correcto.
```
