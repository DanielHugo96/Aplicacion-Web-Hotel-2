# Evidencia de integración real — front-admin-web-recepcion

Fecha: 2026-10-20 (America/Lima). Todo el tráfico de la app salió por el API Gateway
`http://localhost:8080`; nunca por los puertos de los servicios ni por brokers. Los tokens no se
guardan en este archivo.

Servicios arriba y registrados en Eureka: `discovery-server` (8761), `api-gateway` (8080),
`identity-service` (8081), `hotel-service` (8082), `reception-service` (8083),
`inventory-service` (8084) y `sales-service` (8085). PostgreSQL, Kafka y RabbitMQ sanos.

## Sesión y datos

- `POST /api/auth/login` con `admin@hotel.test` → **HTTP 200** (`tipoPersona: Administrador`).
- `POST /api/auth/login` con `recepcion.lima@hotel.test` → **HTTP 200** (`Empleado`, sede 1).
- `POST /api/auth/login` con `cliente.uno@hotel.test` → **HTTP 200** (`Cliente`).
- `GET /api/persona/listar?page=0&size=20&documento=90000100` → **HTTP 200**; un único Cliente
  activo (`idPersona: 100`), sin personal ni inactivos tras el filtro del servicio.
- `GET /api/hotel/listar?page=0&size=20` → **HTTP 200**; tres sedes activas.

## Estadías

- `GET /api/recepcion/listar?page=0&size=20&idHotel=1` → **HTTP 200**; `meta.page=0`,
  `meta.size=20`, `meta.totalElements=4`. La pantalla muestra `Página 1 de 1 · 4 registros`,
  fechas `dd/MM/yyyy`, importes `S/ 70.00` / `S/ 140.00` y píldoras ACTIVA/CERRADA.
- `GET /api/recepcion/listar?page=0&size=20&idHotel=1&estadoEstadia=ACTIVA` → **HTTP 200**;
  `totalElements: 2` (estadías 3 y 4). El filtro viaja como query y no altera el tamaño de página.
- `GET /api/recepcion/4` → **HTTP 200**, `ETag: "1"`, `estadoEstadia: ACTIVA`, entrada
  `2026-10-20`, salida `2026-10-21`, `precioInicial: 70.00`, `adelanto: 20.00`,
  `precioRestante: 50.00`, `totalPagado: 20.00`, `medioPagoAdelanto: EFECTIVO`.
- `PUT /api/recepcion/actualizar/4` con `If-Match: "1"` y
  `{"fechaSalida":"2026-10-22","observacion":"Extiende una noche (integración)"}` → **HTTP 200**,
  `ETag: "2"`, `precioInicial: 140.00`, `precioRestante: 120.00`.
- `PUT /api/recepcion/actualizar/4` con `If-Match: "1"` (ETag viejo) → **HTTP 412**
  `PRECONDITION_FAILED`, mensaje «Otra persona modificó este registro. Recarga para ver los
  cambios.».
- `PUT /api/recepcion/actualizar/4` sin `If-Match` → **HTTP 428** `PRECONDITION_REQUIRED`.
- Edición desde la UI (`03-editar-salida-1024.png`): `PUT /api/recepcion/actualizar/4` →
  **HTTP 200**, `ETag: "4"`, salida `2026-10-23`, alojamiento `210.00`, toast «Cambios guardados».

## Check-in

- Habitaciones reales: `101` y `102` existen desde el seed sin evento de hotel, por lo que su gate
  en reception no existe y la proyección de ocupación llega `DESCONOCIDA`. Para tener gates
  reales se crearon por API tres habitaciones (`POST /api/habitacion/registrar` → **HTTP 201**):
  `104` (`idHabitacion 304`), `105` (`305`) y `106` (`306`); sus gates se crearon al consumir el
  evento `HabitacionCreada`. La proyección de ocupación de hotel siguió en `DESCONOCIDA`; la UI la
  muestra «Sin datos» y **nunca** la traduce a libre/disponible.
- `POST /api/recepcion/registrar` con habitación `101` (sin gate) → **HTTP 409**
  `ROOM_NOT_SYNCED`, mensaje «La habitación aún no está sincronizada con recepción.»; la pantalla
  lo pinta junto al selector (`05-check-in-409-1024.png`).
- `POST /api/recepcion/registrar` con habitación `303` (gate OCUPADA) → **HTTP 409**
  `ROOM_OCCUPIED`, mensaje «La habitación ya está ocupada.».
- `POST /api/recepcion/registrar` con `104` → **HTTP 201**, `idRecepcion: 4`; cuerpo cerrado
  `{idHotel, idCliente, idHabitacion, fechaSalida, adelanto, medioPago, observacion}`, sin
  `fechaEntrada`, precios ni nombre/documento.
- `POST /api/recepcion/registrar` con `106` desde la UI → **HTTP 201**, `idRecepcion: 6`; la
  pantalla navega a `/app/recepcion/6` y muestra el toast «Check-in registrado»
  (`06-check-in-201-detalle-1024.png`).
- Cabeceras del 201 observadas en Chrome: `idempotency-key`, `x-correlation-id`, `authorization`,
  `accept` y `content-type`. **No** viajan `X-User-Id`, `X-Role` ni `X-Hotel-Id`.

## Limpieza

- `GET /api/habitacion/limpiezas?page=0&size=20&idHotel=1&estado=PENDIENTE` → **HTTP 200**;
  `totalElements: 0`; la pantalla muestra el vacío exacto «No hay tareas pendientes»
  (`07-limpieza-vacia-1024.png`).
- `GET /api/habitacion/limpiezas?page=0&size=20&idHotel=1&estado=COMPLETADA` → **HTTP 200**;
  una tarea real de la habitación `303`, `version: 2`, `cleaningCycleId`
  `324d035d-5efa-4563-b4f0-2f768a77b187` (`08-limpieza-completadas-1024.png`).
- No había tarea PENDIENTE en el entorno para completar por UI. El `PUT
  /api/habitacion/{id}/limpieza-completada` con `cleaningCycleId` y `If-Match` de la versión de la
  tarea, sus 200 y 412, está cubierto con mocks de contrato en
  `limpieza.component.spec.ts`.

## Operaciones de habitación

- `POST /api/habitacion/305/mantenimiento` con `If-Match` y `{"motivo":"Fuga de agua (integración)"}`
  → **HTTP 200**; respuesta `estadoFisico: MANTENIMIENTO`, `blockId`
  `c1e83254-af34-4d31-aa01-b678920265be`, `version: 2`.
- `PUT /api/habitacion/305/mantenimiento-completado` con el `blockId` de la fila y el `If-Match`
  vigente → **HTTP 200**; `estadoFisico: LISTA`, `version: 3`.
- `DELETE /api/habitacion/eliminar/305` con `If-Match` → **HTTP 200**; `estado: false`,
  `blockId` de la baja. Un intento previo inmediato devolvió **HTTP 409** `GATE_NOT_AVAILABLE`
  («No se puede bloquear la habitación: está ocupada o en limpieza.»), rechazo durable que la
  pantalla muestra sin reintentar.
- `PUT /api/habitacion/305/reactivar` con el `blockId` de la baja y su `If-Match` → **HTTP 200**;
  `estado: true`, operación liberada.
- `POST /api/habitacion/101/mantenimiento` (sin gate) → **HTTP 409** `GATE_NOT_AVAILABLE` con
  `fields.idHabitacion`.
- **202 real:** con `reception-service` detenido, `POST /api/habitacion/305/mantenimiento` →
  **HTTP 202** en 0,05 s, `Location:
  /api/habitacion/operaciones/5d0bbd5f-63df-419d-9246-99d66e4e9ad2`, `Retry-After: 2`, cuerpo
  `{"blockId":"5d0bbd5f-…","idHabitacion":305,"tipo":"MANTENIMIENTO","estado":"PREPARANDO"}`.
- Sondeo real a `Location` tras reiniciar reception: `GET
  /api/habitacion/operaciones/5d0bbd5f-63df-419d-9246-99d66e4e9ad2` → **HTTP 200**,
  `estado: APLICADA` (terminal). La pantalla mantiene el banner hasta ese terminal y refresca la
  fila; la secuencia de dos sondeos y el refresco se ejercitan con mocks en
  `habitaciones-list.component.spec.ts`.
- Nota de entorno: el primer `reception-service` reiniciado se lanzó con el cliente máquina de
  `identity-service`; sus llamadas internas a identity/sales devolvieron **503**
  `DEPENDENCY_UNAVAILABLE`. Se relanzó con `SERVICE_CLIENT_ID=reception-service` y su secreto, y
  los flujos anteriores pasaron a 201/409. No es un defecto del front: el cliente máquina de
  reception es el que el seed de identity publica (`audiencias_scopes` con `hotel`, `sales` e
  `identity`).

## Red del navegador

- La lista de peticiones XHR/fetch de la sesión apunta **todas** a `http://localhost:8080`
  (`/api/auth`, `/api/hotel`, `/api/recepcion`, `/api/habitacion`, `/api/persona`). No hay llamadas
  a `8081`–`8085` ni a brokers.
- La respuesta expone por CORS `ETag`, `Location`, `Retry-After` y `X-Correlation-Id`.
- El `PUT` de edición y el `POST` de check-in llevan `Idempotency-Key`; el `PUT` lleva además
  `If-Match`. Ninguno lleva `X-User-Id`, `X-Role` ni `X-Hotel-Id`.

## Roles

- ADMIN: menú con Recepción y Limpieza; acciones de mantenimiento/baja/reactivar visibles en
  habitaciones (`09`, `10`, `11`).
- EMPLEADO (`recepcion.lima@hotel.test`): menú con Recepción y Limpieza; en habitaciones solo
  «Solo lectura», sin acciones de operación (`12`).
- CLIENTE (`cliente.uno@hotel.test`): entra a `/mi-estadia` con el shell de área de cliente, sin
  menú administrativo ni enlaces a recepción, limpieza o habitaciones. La ruta `/app/**` está
  protegida por `authGuard` + `rolGuard('ADMIN','EMPLEADO')`; la prueba de rutas y la del guard
  están en verde.
