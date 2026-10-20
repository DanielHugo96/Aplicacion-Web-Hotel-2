# Evidencia HTTP real — front-admin-web-catalogo

Gateway `http://localhost:8080` con identity-service, hotel-service, reception-service, discovery-server y api-gateway en ejecución. Corrida: 2026-10-20 04:xx (America/Lima).

## Listados y seguridad
- `GET /api/persona/listar?page=0&size=20` → **HTTP 200**  OK · ETag "10"
- `GET /api/tipopersona/listar` → **HTTP 200**  OK
- `GET /api/hotel/listar?page=0&size=100` → **HTTP 200**  OK
- `GET /api/piso/listar?page=0&size=20&idHotel=1` → **HTTP 200**  OK
- `GET /api/categoria/listar?page=0&size=100` → **HTTP 200**  OK
- `GET /api/habitacion/listar?page=0&size=20&idHotel=1` → **HTTP 200**  OK
- `GET /api/estadohabitacion/listar` → **HTTP 200**  OK
- `GET /api/persona/listar sin token` → **HTTP 401** UNAUTHENTICATED Tu sesión expiró. Vuelve a iniciar sesión.
- `GET /api/hotel/listar como CLIENTE` → **HTTP 403** FORBIDDEN No tienes permiso para esta acción.
- `GET /api/persona/buscar/999999` → **HTTP 404** RESOURCE_NOT_FOUND No encontramos lo que buscas.

## Personas
- `PUT /api/persona/actualizar/100 con correo inválido` → **HTTP 400** VALIDATION_ERROR Revisa los datos marcados.
  - fields: `{"correo": "formato de correo invalido"}`
- `PUT /api/persona/actualizar/100 (ETag vigente)` → **HTTP 200**  OK · ETag "5"
- `PUT /api/persona/actualizar/100 (ETag viejo)` → **HTTP 412** PRECONDITION_FAILED Otra persona modificó este registro. Recarga para ver los cambios.
  - fields: `{"If-Match": "la version enviada no es la vigente"}`
- `POST /api/persona/registrar cliente` → **HTTP 201**  OK · ETag "1"
- `DELETE /api/persona/eliminar/{id} (baja lógica)` → **HTTP 200**  OK · ETag "2"
- `POST /api/persona/registrar ADMIN sin clave` → **HTTP 409** STAFF_CREDENTIAL_REQUIRED Para crear personal, la persona necesita credenciales de acceso.
- `DELETE /api/persona/eliminar/1 (último ADMIN)` → **HTTP 409** LAST_ADMIN_PROTECTED Debe quedar al menos un administrador activo.

## Roles, sedes, pisos, categorías, habitaciones y estados
- `PUT /api/tipopersona/actualizar/3` → **HTTP 500** INTERNAL_ERROR Ocurrió un error inesperado.
- `DELETE /api/hotel/eliminar/1 (habitaciones activas)` → **HTTP 409** ACTIVE_DEPENDENCIES No se puede dar de baja mientras tenga elementos activos.
- `POST /api/habitacion/registrar numero 101 duplicado` → **HTTP 409** DUPLICATE_RESOURCE Ese valor ya está registrado.
- `POST /api/habitacion/registrar imagen http` → **HTTP 400** VALIDATION_ERROR Revisa los datos marcados.
- `PUT /api/estadohabitacion/actualizar/1` → **HTTP 200**  OK · ETag "1"
- `PUT /api/estadohabitacion/actualizar/1 sin If-Match` → **HTTP 428** PRECONDITION_REQUIRED Falta la versión del registro.

## CRUD de catálogo (altas, ediciones y bajas)
- `POST /api/hotel/registrar` → **HTTP 201**
- `PUT /api/hotel/actualizar/{id}` → **HTTP 200** · ETag "2"
- `DELETE /api/hotel/eliminar/{id}` → **HTTP 200**
- `POST /api/piso/registrar` → **HTTP 201**
- `PUT /api/piso/actualizar/{id}` → **HTTP 200** · ETag "2"
- `DELETE /api/piso/eliminar/{id}` → **HTTP 200**
- `POST /api/categoria/registrar` → **HTTP 201**
- `PUT /api/categoria/actualizar/{id}` → **HTTP 200** · ETag "2"
- `DELETE /api/categoria/eliminar/{id}` → **HTTP 200**
- `POST /api/habitacion/registrar` → **HTTP 201**
- `PUT /api/habitacion/actualizar/{id}` → **HTTP 200** · ETag "2"
