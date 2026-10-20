# Evidencia HTTP real — front-admin-web-catalogo

Gateway `http://localhost:8080` con `identity-service` (8081), `hotel-service` (8082),
`reception-service`, `discovery-server` y `api-gateway` arrancados desde `main` el 2026-10-20 a las
06:00 (America/Lima). Los dos planes de arreglo de backend (`#92` y `#94`) ya están en la rama.

## Listados, paginación y seguridad

- `GET /api/persona/listar?page=0&size=20` → **HTTP 200** · `data` con `personas` y `meta.page/size/totalElements`.
- `GET /api/tipopersona/listar` → **HTTP 200** · `data` con `tipos`.
- `GET /api/hotel/listar?page=0&size=100` → **HTTP 200** · `meta.page=0 size=100 totalElements=5`.
- `GET /api/piso/listar?page=0&size=20&idHotel=1` → **HTTP 200** · `meta.page=0 size=20 totalElements=3`.
- `GET /api/categoria/listar?page=0&size=100` → **HTTP 200** · `meta.page=0 size=100 totalElements=6`.
- `GET /api/habitacion/listar?page=0&size=20&idHotel=1` → **HTTP 200** · `meta.page=0 size=20 totalElements=5`.
- `GET /api/estadohabitacion/listar` → **HTTP 200** · tres estados fijos.
- `GET /api/persona/listar` sin token → **HTTP 401** `UNAUTHENTICATED`.
- `GET /api/hotel/listar` como CLIENTE → **HTTP 403** `FORBIDDEN`.
- `GET /api/persona/buscar/{id}` inexistente → **HTTP 404** `RESOURCE_NOT_FOUND`.

## Personas (ADMIN y EMPLEADO)

- `GET /api/persona/listar?page=0&size=20` como EMPLEADO sin `documento` → **HTTP 400** `VALIDATION_ERROR` (`fields.documento = obligatorio para consultar como empleado`).
- `GET /api/persona/listar?page=0&size=20&documento=90000100` como EMPLEADO → **HTTP 200** con una persona y `meta` paginado.
- `GET /api/persona/buscar/{id}` como EMPLEADO → **HTTP 404** `RESOURCE_NOT_FOUND`. Observación residual: la lista por documento sí responde 200; la pantalla de solo lectura no usa `buscar/{id}`.
- `PUT /api/persona/actualizar/{id}` con correo inválido → **HTTP 400** `VALIDATION_ERROR` (`fields.correo`).
- `PUT /api/persona/actualizar/{id}` con `If-Match` vigente → **HTTP 200** con `version` incrementada y `ETag`.
- `PUT /api/persona/actualizar/{id}` con `If-Match` viejo → **HTTP 412** `PRECONDITION_FAILED`.
- `PUT /api/persona/actualizar/{id}` sin `If-Match` → **HTTP 428** `PRECONDITION_REQUIRED`.
- `POST /api/persona/registrar` cliente → **HTTP 201**.
- `DELETE /api/persona/eliminar/{id}` con `If-Match` → **HTTP 200** con `estado: false`.
- `POST /api/persona/registrar` ADMIN sin clave → **HTTP 409** `STAFF_CREDENTIAL_REQUIRED`.
- `DELETE /api/persona/eliminar/{id}` del último ADMIN → **HTTP 409** `LAST_ADMIN_PROTECTED`.
- `POST /api/persona/registrar` se probó después de desactivar temporalmente los otros ADMIN y se reactivaron en la misma corrida.

## Roles (paso 7)

- `GET /api/tipopersona/listar` → **HTTP 200** con `ADMIN`, `EMPLEADO`, `CLIENTE`.
- `PUT /api/tipopersona/actualizar/{id}` con `If-Match` → **HTTP 200**, `ETag: "5"`, `version` de 4 a 5.
- `GET /api/tipopersona/buscar/{id}` tras el PUT → **HTTP 200**, `{"descripcion":"Cliente","codigo":"CLIENTE","version":5}`.
- `grep -R "tipoPersona\|tipopersona" apps/admin-web/src/app | grep -Ei "registrar|eliminar"` → sin resultados: el cliente no expone ni invoca altas o bajas de roles.
- Capturas: `14-tipos-persona-listado.png` y `15-tipos-persona-editado.png`.

## Sedes, pisos, categorías y habitaciones

- `POST /api/hotel/registrar` → **HTTP 201**; `PUT /api/hotel/actualizar/{id}` → **HTTP 200** `ETag: "2"`; `DELETE /api/hotel/eliminar/{id}` → **HTTP 200**.
- `DELETE /api/hotel/eliminar/{id}` con habitaciones activas → **HTTP 409** `ACTIVE_DEPENDENCIES`.
- `POST /api/piso/registrar` → **HTTP 201**; `PUT /api/piso/actualizar/{id}` → **HTTP 200** `ETag: "2"`; `DELETE /api/piso/eliminar/{id}` → **HTTP 200**.
- `POST /api/categoria/registrar` → **HTTP 201**; `PUT /api/categoria/actualizar/{id}` → **HTTP 200** `ETag: "2"`; `DELETE /api/categoria/eliminar/{id}` → **HTTP 200**.
- `POST /api/habitacion/registrar` → **HTTP 201** (nace `LISTA`); `PUT /api/habitacion/actualizar/{id}` → **HTTP 200** `ETag: "2"` sin cambiar número ni sede.
- `GET /api/habitacion/buscar/{id}` → **HTTP 200** con `urlsImagenes: [{"url":"https://example.test/h2.jpg","alt":"Habitacion de demostracion editada","orden":1}]`.
- `POST /api/habitacion/registrar` con número `101` duplicado → **HTTP 409** `DUPLICATE_RESOURCE`.
- `POST /api/habitacion/registrar` con `url` `http://` → **HTTP 400** `VALIDATION_ERROR`.

## Estados físicos

- `PUT /api/estadohabitacion/actualizar/{id}` solo con `requestBody` → **HTTP 200** `ETag: "3"` (ya no exige query param).
- `PUT /api/estadohabitacion/actualizar/{id}` sin `If-Match` → **HTTP 428** `PRECONDITION_REQUIRED`.
- `PUT /api/estadohabitacion/actualizar/{id}` con versión distinta → **HTTP 412** `PRECONDITION_FAILED`.
- Captura del diálogo: `06-estados-dialogo.png`.

## Comandos canónicos

En `apps/admin-web`:

```text
$ npm ci
added 281 packages, and audited 282 packages in 32s
found 0 vulnerabilities

$ npm run build
Application bundle generation complete. [3.163 seconds]
Output location: apps/admin-web/dist/admin-web

$ npm test -- --watch=false
Test Files  8 passed (8)
Tests       22 passed (22)
```

En la raíz:

```text
$ python scripts/validar-planes.py
28 plan(es) revisado(s); 28 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```
