# Contrato Frontend ↔ Backend (vía Gateway)

Base: `http://localhost:8080` (HTTPS en despliegue). Todo Angular consume el Gateway.
Nunca puertos de servicios (`8081-8087`), Eureka (`8761`), brokers ni DB.

Fuentes: `especificacion/02-landing-y-panel.md`, `03-contrato-comun.md`,
`microservicios/*/api.md`, `infraestructura/gateway-eureka-docker.md`.

Artefactos máquina:

- `openapi/public-web.yaml` — landing (`public-web`), 4 endpoints públicos.
- `openapi/admin-web.yaml` — panel (`admin-web`), superficie privada por rol.

## 1. Reglas transversales

### 1.1 Headers

| Header | Uso |
|---|---|
| `Accept: application/json` | Todas las respuestas JSON |
| `Content-Type: application/json` | Requests con body |
| `Authorization: Bearer <JWT>` | Toda ruta privada. Nunca en query. JWT solo en memoria, logout lo borra |
| `X-Correlation-Id: <uuid>` | Opcional cliente; Gateway genera si falta, lo propaga y lo devuelve |
| `Idempotency-Key: <uuid>` | Obligatorio en POST/PUT/DELETE de negocio (salvo login). Misma clave al reintentar. Tras 409 de validación corregida → nueva intención, nueva clave |
| `If-Match: "<version>"` | Obligatorio en PUT/DELETE con `V`. Sin él → 428; versión distinta → 412 |
| `Retry-After` | Respuesta en 429/503 y 202 con operación en curso |
| `ETag`, `Location` | Lecturas/mutaciones y 202 con `Location: GET recurso` exponen estos headers (CORS `exposedHeaders`) |

Gateway elimina `X-User-Id`, `X-Role`, `X-Hotel-Id` falsificables. No enviar `idPersona/rol/precio` como autoridad (el servidor los deriva).

CORS dev: orígenes `http://localhost:4200` (admin) y `http://localhost:4300` (public).
Métodos `GET,POST,PUT,DELETE,OPTIONS`. `allowCredentials=false`, `maxAge=600`.

### 1.2 Envelope

Éxito:

```json
{"success":true,"message":"OK","data":{"idHabitacion":101,"version":1},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

Lista: `data: []`, `meta: {page, size, totalElements, correlationId}`.
Error:

```json
{"success":false,"message":"La habitación no admite check-in","data":null,"error":{"code":"ROOM_NOT_READY","fields":{}},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

Códigos: `201` creación, `200` update/baja y GET, `204` sin cuerpo,
`202` asíncrono (no es confirmación), `400` validación, `401` no autenticado,
`403` rol insuficiente, `404` inexistente o ajeno a sede/propietario (anti-enumeración),
`409` negocio/duplicado, `410` retirada, `412` versión, `428` falta `If-Match`,
`429` límite, `503` dependencia caída.

### 1.3 Convenciones L / H / V

- `L` = `?page=0&size=20` (máx 100), orden estable ID asc. Respuesta `data[] + meta`.
  Maestros aceptan `?estado=true|false` (default `true`).
- `H` = `?idHotel=` requerido en listados por sede. En recurso por ID se deriva de BD y se valida
  (excepción: producto por sede usa clave `(idProducto,idHotel)`, siempre exige `idHotel`).
- `V` = `If-Match` obligatorio en PUT/DELETE versionados. ETag devuelto en GET/mutación.

Idempotencia: clave `(actor, método, plantillaRuta, key)`. Misma clave + distinto body → `409 IDEMPOTENCY_CONFLICT`.
En curso → `202 + referencia`; completada → mismo status/body original.

### 1.4 Auth y sedes

JWT RS256 de identity (`iss=hotel-identity`, `aud=hotel-api`, TTL 15 min, sin refresh).
Claims: `sub=idPersona`, `tipoPersona=Administrador|Empleado|Cliente`,
`roles=["ADMIN"]|["EMPLEADO"]|["CLIENTE"]`, `sedes=[ids]`.

- `ADMIN`: global. `EMPLEADO`: solo `sedes` asignadas. `CLIENTE`: solo `idCliente == sub`.
- Empleado lee sede asignada; cliente nunca envía rol/precio/propietario autoritativo.
- Guards Angular son UX; el backend niega por defecto (`deny-by-default`).

## 2. Landing `public-web` — sí / no

Rutas Angular (no son endpoints backend): `/`, `/sedes`, `/sedes/:slug`,
`/sedes/:slug/habitaciones/:idCategoria`, `/contacto`, `/privacidad`.

| Pantalla | Endpoint | Notas |
|---|---|---|
| Sedes | `GET /api/public/hoteles?page=&size=` | Solo publicadas/activas. `Cache-Control: public,max-age=60` |
| Detalle sede | `GET /api/public/hoteles/{slug}` | Inactiva/oculta → 404 |
| Categorías por sede | `GET /api/public/hoteles/{slug}/categorias?page=&size=&capacidad=` | Solo categorías con habitaciones publicadas. `capacidad` 1..10 |
| Detalle categoría | `GET /api/public/hoteles/{slug}/categorias/{idCategoria}` | `precioDesde` = mínimo publicado + galería curada |
| Contacto | — | Solo enlace `tel:`/`mailto:`/WhatsApp. Sin formulario SMTP, sin upload |
| Acceso privado | — | Link al login del panel. Sin tokens embebidos |

`HotelPublico = {idHotel,slug,nombre,descripcion,direccion,ciudad,telefono,correoContacto}`.
`CategoriaPublica = {idCategoria,descripcion,detalle,capacidad,precioDesde,moneda:"PEN",imagenes:[{url,alt}]}`.

Prohibido en LP: huéspedes/documentos/correos/estadías/consumos/stock/ingresos/reportes;
números de habitación, ocupantes, limpieza, lista operativa; “disponible ahora” o reserva
de fechas; cualquier POST/PUT/DELETE; credenciales o rutas `/internal/**`, `*.well-known`
salvo JWKS interno. Texto tarifa fijo:
“Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.”
Error de red → mensaje + reintento, nunca inventar precios ni usar datos del panel como fallback.

Ver `openapi/public-web.yaml` para schemas y ejemplos.

## 3. Panel `admin-web` — superficie por módulo

Base + headers §1. Todos los `POST/PUT/DELETE` de negocio llevan `Idempotency-Key`.
Todos los PUT/DELETE con `V` llevan `If-Match`. Ver `openapi/admin-web.yaml`.

### 3.1 Auth y personas (identity)

| Acción | Endpoint | Rol |
|---|---|---|
| Login | `POST /api/auth/login {correo,clave}` → `Auth{idPersona,nombre,apellido,correo,tipoPersona,token,expiresIn:900,sedes}` | P. 401 genérico, 429 tras 5/min IP+cuenta. Sin idempotencia |
| Registro público | `POST /api/auth/registro {tipoDocumento,documento,nombre,apellido,correo,clave}` | P. Fuerza CLIENTE; rechaza `idTipoPersona/roles/sedes/estado` con 400. Máx 3/min IP |
| Sesión / perfil | `GET /api/auth/me`, `PUT /api/auth/me {nombre,apellido,fotoUrl?}` (V), `PUT /api/auth/password {claveActual,claveNueva}` | A/E/C. Clave 12–72 bytes UTF-8, BCrypt, write-only |
| Social | `POST /api/auth/social` → `410 SOCIAL_LOGIN_DISABLED` | No usar, retirar botón |
| Listar/buscar personas | `GET /api/persona/listar?page=&size=&idTipoPersona?=&documento?`, `GET /api/persona/buscar/{id}` | A/E. E solo CLIENTE y con `?documento=` exacto (sin él → 400). C solo propio |
| Alta persona | `POST /api/persona/registrar` | A/E. E solo CLIENTE sin sedes. ADMIN/EMPLEADO exigen clave; presencial sin cuenta → solo cliente |
| Editar / baja | `PUT /api/persona/actualizar/{id}` (V), `DELETE /api/persona/eliminar/{id}` (V) | A. Protege último ADMIN activo (PUT y DELETE → 409). No cambia documento por este DTO |
| Tipos persona | `GET /api/tipopersona/listar`, `GET /api/tipopersona/buscar/{id}`, `PUT /api/tipopersona/actualizar/{id} {descripcion}` (V) | A/E lectura; solo A edita etiqueta. `codigo` inmutable. Registrar/eliminar → `409 FIXED_ROLE_CATALOG` (no llamar) |

`Persona = {idPersona,tipoDocumento,documento,nombre,apellido,correo,fotoUrl,idTipoPersona,tipoPersona,sedes,estado,fechaCreacion,version}`. Nunca incluye clave/hash.

### 3.2 Sedes, pisos, categorías, habitaciones (hotel)

| Acción | Endpoint | Rol |
|---|---|---|
| Sedes | `GET /api/hotel/listar`, `GET /api/hotel/buscar/{id}`, `POST /api/hotel/registrar`, `PUT /api/hotel/actualizar/{id}` (V), `DELETE /api/hotel/eliminar/{id}` (V) | A/E lectura alcance; solo A muta. Baja solo sin habitaciones activas (409) |
| Pisos | `GET /api/piso/listar?` + `idHotel`, buscar, registrar, actualizar `{descripcion,estado}` (V), eliminar (V) | Igual. No cambiar sede por PUT |
| Categorías | `GET /api/categoria/listar`, buscar, registrar, actualizar (V), eliminar (V) | Igual. Global, con regla sin habitaciones activas |
| Habitaciones | `GET /api/habitacion/listar?idHotel=&idCategoria?`, buscar, `POST /api/habitacion/registrar {idHotel,numero,idPiso,idCategoria,detalle,precio,publicada,urlsImagenes[]}` → nace LISTA, `PUT /api/habitacion/actualizar/{id}` (V, catálogo; no físico/número/sede), `DELETE` (V, baja o 202 con blockId) | A/E lectura; solo A muta. Máx 10 imágenes, URL https allowlist o `/assets/`, precio>0 |
| Mantenimiento | `POST /api/habitacion/{id}/mantenimiento {motivo}` (V) → 200/202 `{blockId}`, `PUT .../mantenimiento-completado {blockId}` (V), `PUT .../reactivar {blockId}` (V), `GET /api/habitacion/operaciones/{blockId}` | A. 409 si gate ocupado |
| Limpieza | `GET /api/habitacion/limpiezas?idHotel=&estado=PENDIENTE\|COMPLETADA`, `PUT /api/habitacion/{id}/limpieza-completada {cleaningCycleId}` (V = version de la tarea) | A/E sede asignada |
| Estados físicos | `GET /api/estadohabitacion/listar`, buscar, `PUT /api/estadohabitacion/actualizar/{id} {descripcion}` (V) | A/E lectura; solo A etiqueta. Registrar/eliminar → `409 FIXED_STATE_CATALOG` |

`Habitacion = {idHabitacion,...,estado,estadoFisico,version,ocupacion:{estadoEstadia,idRecepcion,updatedAt},idEstadoHabitacion}` (+ `Limpieza{cleaningCycleId,idHabitacion,idRecepcion,estado,roomVersion,version}`).

### 3.3 Estadías (reception)

| Acción | Endpoint | Rol |
|---|---|---|
| Listar / ver | `GET /api/recepcion/listar?page=&size=&idHotel=&estadoEstadia?`, `GET /api/recepcion/{id}`, `GET /api/recepcion/cliente/{idCliente}?`, `GET /api/recepcion/habitacion-activa/{idHabitacion}` | A/E sede; C solo propia (`cliente/{sub}`, `/{id}` propio) |
| Check-in | `POST /api/recepcion/registrar {idHotel,idCliente,idHabitacion,fechaSalida,adelanto,observacion?}` | A/E sede. `fechaEntrada` = hoy Lima (servidor). 409 `ROOM_OCCUPIED/ROOM_NOT_READY`. Cliente primero existe en identity |
| Editar salida | `PUT /api/recepcion/actualizar/{id} {fechaSalida,observacion?}` (V) | A/E. Solo ACTIVA. No baja `precioInicial` bajo `adelanto` (409) |
| Cotizar / cerrar | `GET /api/recepcion/{id}/cotizacion-salida?costoPenalidad=0`, `POST /api/recepcion/registrar-salida {idRecepcion,idHabitacion,costoPenalidad,totalPagado}` | A/E. `totalPagado` = efectivo recibido AHORA. 409 `AMOUNT_MISMATCH/OPERATIONS_IN_PROGRESS`, 202 si queda CERRANDO con `Location + Retry-After`. Panel muestra “cierre pendiente, no volver a cobrar” |
| Archivar | `DELETE /api/recepcion/eliminar/{id}` (V) | A. Solo CERRADA (409 si no) |

`Recepcion` incluye snapshots cliente/habitación, `precioInicial/adelanto/precioRestante/totalPagado(historial alojamiento)/costoPenalidad`, `estadoEstadia ACTIVA/CERRANDO/CERRADA`, `version/closureId/cleaningCycleId` + `cierre{closureId,estado,lastErrorCode,calculo}`.

### 3.4 Productos y stock (inventory — sin CLIENTE directo)

`CLIENTE` no llama `/api/producto/**`; usa `GET /api/venta/catalogo` (sales).

| Acción | Endpoint | Rol |
|---|---|---|
| Listar / ver | `GET /api/producto/listar?page=&size=&idHotel=`, `GET /api/producto/buscar/{id}?idHotel=` | A/E sede |
| Alta / editar / baja sede | `POST /api/producto/registrar {sku,nombre,detalle?,imagenUrl?,idHotel,precio,cantidadInicial,umbralBajo}`, `PUT /api/producto/actualizar/{id}?idHotel=` (V + `productoVersion`), `DELETE /api/producto/eliminar/{id}?idHotel=` (V) | A. Precio>0, inicial>=0. Baja impide ventas nuevas pero permite restituir previas |
| Asignar sede / ajustar | `POST /api/producto/{id}/sedes {idHotel,precio,cantidadInicial,umbralBajo}`, `POST /api/producto/{id}/ajustes {idHotel,delta,motivo}` (V) | A. `delta != 0`, motivo 1..300 |
| Movimientos | `GET /api/producto/{id}/movimientos?idHotel=&page=&size=&inicio?=&fin?` | A/E. Unión `VENTA{...DESCONTADO/RECHAZADO/RESTITUIDO}` / `AJUSTE{...}` |

`Producto = {idProducto,sku,nombre,detalle,imagenUrl,idHotel,precio,cantidad,umbralBajo,estado,fechaCreacion,version,productoVersion}`.

### 3.5 Carrito y ventas (sales)

| Acción | Endpoint | Rol |
|---|---|---|
| Catálogo privado | `GET /api/venta/catalogo?idRecepcion=&page=&size=` | A/E/C. Solo recepción ACTIVA; precio/stock informativos. 503 sin fallback inventado |
| Carrito | `POST /api/carrito/agregar?idRecepcion=&idProducto=&cantidad=` (+ `Idempotency-Key`), `GET /api/carrito/listar/{idRecepcion}`, `DELETE /api/carrito/eliminar?idRecepcion=&idProducto=` | A/E/C (C solo propia ACTIVA; cerrada → `[]`). Cantidad 1..9999, upsert suma. Cambiar cantidad = eliminar + agregar |
| Ventas listar/ver | `GET /api/venta?page=&size=&idHotel=&estado?=&estadoOperacion?=`, `GET /api/venta/buscar/{id}`, `GET /api/venta/recepcion/{idRecepcion}?` | A/E sede; C solo propia |
| Crear venta | `POST /api/venta {idRecepcion,estado?,detalles:[{idProducto,cantidad}]}` | A/E/C. C solo `PENDIENTE`/omitido; A/E pueden `PAGADO` (cobro manual). Sin precios/total en body (400). Máx 50 líneas. 201 CONFIRMADA o 202 en recuperación |
| Pagar / anular | `PUT /api/venta/{id} {estado:"PAGADO"}` (V), `DELETE /api/venta/{id}?motivo=` (V) | A/E sede. Anulación impagada → 202 `CANCELACION_PENDIENTE`; PAGADA → 409, historial intacto |
| Operación | `GET /api/venta/operaciones/{operationId}` | A/E/C (C solo suya). Polling tras 202 (`Location + Retry-After: 2`) |

`Venta = {idVenta,idRecepcion,idCliente,idHotel,total,estado:PENDIENTE|PAGADO,estadoOperacion:PENDIENTE|CONFIRMADA|RECHAZADA|CANCELACION_PENDIENTE|CANCELADA,operationId,version,fechaCreacion,paidAt,detalles:[{idDetalleVenta,idProducto,nombreProducto,cantidad,precioUnitario,subTotal}]}`.
`202` mantiene la forma con `estadoOperacion` PENDIENTE/CANCELACION_PENDIENTE.

### 3.6 Reportes (reporting — sin CLIENTE)

`GET /api/reportes/productos-bajo-stock?idHotel=&limite=5`,
`GET /api/reportes/ventas?idHotel=&inicio=&fin=`,
`GET /api/reportes/ocupacion?idHotel=&inicio=&fin=`,
`GET /api/reportes/cobros?idHotel=&inicio=&fin=`,
`GET /api/reportes/dashboard?idHotel?=`.

A todas las sedes (dashboard consolidado solo A); E una sede asignada; C → 403.
`inicio/fin` ISO-8601 con offset, `[inicio,fin)`, máx 366 días; ocupación solo medianoche America/Lima.
`meta += {updatedAt,consistency:"EVENTUAL",degraded,bootstrapComplete}`.
Sin bootstrap → `503 REPORTING_NOT_READY`; degradado → 200 `degraded:true` o 503 si inutilizable.
Exportar PDF/Excel en frontend; sin endpoints extra de archivos.

## 4. Matriz rápida de roles (resumen 02-landing-y-panel)

| Función | ADMIN | EMPLEADO | CLIENTE |
|---|---|---|---|
| Personal / roles / sedes | Sí | No | No |
| Clientes crear/buscar | Sí | Solo CLIENTE (+ `documento` exacto) | Registro propio / perfil propio |
| Sedes/pisos/categorías/habitaciones | CRUD | Leer asignadas | No (LP pública sí) |
| Limpieza completar | Sí | Sí asignada | No |
| Estadía registrar/cerrar | Sí | Sí asignada | No (solo ver propia) |
| Productos/stock | CRUD + ajustes | Lectura sede | Vía `venta/catalogo` de su estadía |
| Carrito / consumo | Sí | Sí asignada | Solo estadía propia ACTIVA |
| Pago consumos / anular impagada | Sí | Sí asignada | No |
| Reportes | Todas | Asignada | No |

## 5. Checklist frontend antes de codificar

- [ ] Base Gateway configurable, sin URLs de servicios/brokers ni secretos en bundle LP.
- [ ] JWT en memoria, `401` → relogin, `403/404` sin filtrar sede ajena, `412` → recargar, `202` → polling `Location`, no re-cobrar cierre.
- [ ] `Idempotency-Key` una por intención, `If-Match` desde ETag, paginación `page/size`, `idHotel` por flujo (no como autorización).
- [ ] LP: 4 endpoints, caché 60 s, tarifa con disclaimer, `tel:/mailto:/WhatsApp` externos, responsive + a11y (labels, contraste, alt).
- [ ] Panel: vistas separadas admin vs cliente; sin precio/estadoOperacion/idCliente en bodies de venta; sin social login ni reserva futura; DELETE = baja/anulación con texto preciso.
