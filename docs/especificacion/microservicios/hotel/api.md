# Hotel — contrato API

[Comunes](../../03-contrato-comun.md): headers/idempotencia/V,403/404 por sede. L/H según contrato. A global; E solo sedes asignadas. C no usa APIs privadas de habitación.

## Públicas nuevas: solo lectura

| Método/ruta | Query | data 200 |
|---|---|---|
| GET /api/public/hoteles | page,size | HotelPublico[] |
| GET /api/public/hoteles/{slug} | — | HotelPublico |
| GET /api/public/hoteles/{slug}/categorias | page,size,capacidad?1..10 | CategoriaPublica[] |
| GET /api/public/hoteles/{slug}/categorias/{idCategoria} | — | CategoriaPublica |

Acceso P. Detalle oculto/inactivo404. No fechas ni parámetro “disponible”. Cache-Control public,max-age=60. HotelPublico `{idHotel,slug,nombre,descripcion,direccion,ciudad,telefono,correoContacto}`. CategoriaPublica `{idCategoria,descripcion,detalle,capacidad,precioDesde,moneda:"PEN",imagenes:[{url,alt}]}`. No números de habitación/huéspedes/stock/estado físico.

## Sedes, pisos y categorías

| Método y ruta | Acceso | Entrada | Resultado |
|---|---|---|---|
| GET /api/hotel/listar · NUEVA | A/E | L | Hotel[] de alcance |
| GET /api/hotel/buscar/{id} · NUEVA | A/E | id | Hotel |
| POST /api/hotel/registrar · NUEVA | A | HotelInput | 201 Hotel |
| PUT /api/hotel/actualizar/{id} · NUEVA | A,V | HotelInput | 200 Hotel |
| DELETE /api/hotel/eliminar/{id} · NUEVA | A,V | id | 200 baja;409 habitaciones activas |
| GET /api/piso/listar · LEGACY | A/E | L,H | Piso[] |
| GET /api/piso/buscar/{id} · LEGACY | A/E | id | Piso |
| POST /api/piso/registrar · LEGACY | A | PisoInput | 201 Piso |
| PUT /api/piso/actualizar/{id} · LEGACY | A,V | descripcion,estado | 200 Piso |
| DELETE /api/piso/eliminar/{id} · LEGACY | A,V | id | 200 baja;409 dependencias |
| GET /api/categoria/listar · LEGACY | A/E | L | Categoria[] global |
| GET /api/categoria/buscar/{id} · LEGACY | A/E | id | Categoria |
| POST /api/categoria/registrar · LEGACY | A | CategoriaInput | 201 Categoria |
| PUT /api/categoria/actualizar/{id} · LEGACY | A,V | CategoriaInput | 200 Categoria |
| DELETE /api/categoria/eliminar/{id} · LEGACY | A,V | id | 200 baja;409 dependencias |

## Habitaciones y físico

| Método y ruta | Acceso | Entrada | Resultado |
|---|---|---|---|
| GET /api/habitacion/listar · LEGACY | A/E | L,H,idCategoria? | Habitacion[] |
| GET /api/habitacion/buscar/{id} · LEGACY | A/E | id | Habitacion |
| POST /api/habitacion/registrar · LEGACY | A | HabitacionInput | 201 Habitacion LISTA; sincroniza gate por Kafka |
| PUT /api/habitacion/actualizar/{id} · LEGACY | A,V | HabitacionUpdate | 200 catálogo; no cambia físico/ocupación |
| DELETE /api/habitacion/eliminar/{id} · LEGACY | A,V | id | 200 baja /202 operación con blockId |
| POST /api/habitacion/{id}/mantenimiento · NUEVA | A,V | motivo:string1..300 | 200/202 operación con blockId;409 gate ocupado |
| PUT /api/habitacion/{id}/mantenimiento-completado · NUEVA | A,V | blockId:uuid | 200 Habitacion; libera gate vía Kafka |
| PUT /api/habitacion/{id}/reactivar · NUEVA | A,V | blockId:uuid | 200 Habitacion; sede/piso/categoría activos |
| GET /api/habitacion/operaciones/{blockId} · NUEVA | A | blockId | 200 {blockId,idHabitacion,tipo,estado} |
| GET /api/habitacion/limpiezas · NUEVA | A/E | H,estado=PENDIENTE/COMPLETADA,L | Limpieza[] |
| PUT /api/habitacion/{id}/limpieza-completada · NUEVA | A/E,V | cleaningCycleId:uuid | 200 {limpieza:Limpieza,habitacion:Habitacion}; V y ETag son version de la tarea |
| GET /api/estadohabitacion/listar · LEGACY | A/E | — | EstadoFisico[] |
| GET /api/estadohabitacion/buscar/{id} · LEGACY | A/E | id | EstadoFisico |
| PUT /api/estadohabitacion/actualizar/{id} · LEGACY | A,V | descripcion | 200 etiqueta; código fijo |
| POST /api/estadohabitacion/registrar · RETIRADA | A | — | 409 FIXED_STATE_CATALOG |
| DELETE /api/estadohabitacion/eliminar/{id} · RETIRADA | A | — | 409 FIXED_STATE_CATALOG |

Los GET no marcados con status retornan200. V para acciones de habitación valida versión antes de crear operación y revalida al aplicar cambio local. Rutas estáticas limpiezas/operaciones tienen prioridad frente a IDs.

## DTO

HotelInput: `{slug,nombre,descripcion,direccion,ciudad,telefono,correoContacto,publicado,estado}`; slug lowercase[a-z0-9-], estable tras publicación. Hotel añade idHotel,version. PUT estado=false aplica la misma regla que DELETE: sin habitaciones activas; tampoco categoría/piso se inactivan si hay habitaciones activas. No cambiar sede de un piso por PUT.

PisoInput: `{idHotel,descripcion}`; Piso añade idPiso,estado,version.
CategoriaInput: `{descripcion,detalle,capacidad,estado}`; Categoria añade idCategoria,version.
HabitacionInput: `{idHotel,numero,idPiso,idCategoria,detalle,precio,publicada,urlsImagenes:[{url,alt,orden}]}`.
HabitacionUpdate: `{idPiso,idCategoria,detalle,precio,publicada,urlsImagenes}`; no permite estado físico ni número/sede.
Máximo10 imágenes; URL https allowlist o /assets/; precio>0; no file:// ni fetch arbitrario.

Habitacion añade `idHabitacion,estado,estadoFisico,version,ocupacion:{estadoEstadia,idRecepcion,updatedAt},idEstadoHabitacion`. Ocupación puede ser DESCONOCIDA hasta bootstrap; no se convierte a disponible. EstadoFisico `{idEstadoHabitacion,codigo,descripcion,estado,version}`. Limpieza `{cleaningCycleId,idHabitacion,idRecepcion,estado,roomVersion,version}`.

## Feign interno

| Ruta | Caller | Entrada / data |
|---|---|---|
| GET /internal/habitaciones/{id} | reception,habitaciones:read | {idHabitacion,idHotel,numero,detalle,idCategoria,categoriaNombre,pisoNombre,precio,estadoFisico,estado,hotelActivo,hotelNombre,version} |
| GET /internal/hoteles/{id} | identity/inventory,hoteles:read | {idHotel,nombre,estado,version} |

Salida: hotel llama `POST /internal/habitaciones/{id}/bloqueos` a reception con blockId,motivo,idHotel. Leer “libre” y luego actualizar NO basta. Operación bloqueada se consulta por blockId mediante mismo POST idempotente.

## Mensajería

Produce `hotel.sede.v1` SedeSnapshot payload `{idHotel,nombre,ciudad,estado,publicado,version}`.
Produce `hotel.habitacion.v1`: snapshot `{idHabitacion,idHotel,numero,idCategoria,categoriaNombre,pisoNombre,precio,estado,estadoFisico,version,cleaningCycleId:null,cleaningRoomVersion:null,blockId:null}`. HabitacionLista exige cleaningCycleId y cleaningRoomVersion (=roomVersion del comando de limpieza); no es hotel.version; HabitacionHabilitada exige blockId; HabitacionCreada crea gate inicial. Cualquier cambio de categorías/pisos que afecte estos snapshots dispara actualización de habitaciones afectadas, en la misma transacción de catálogo, incrementando version y outbox de cada habitación afectada. Si alguna está en operación administrativa PREPARANDO, devuelve409 sin aplicar el cambio de catálogo.

Consume reception.recepcion.v1 para ocupacion_projection usando ocupacionActual, orden roomVersion; no infiere desocupación de una estadía histórica archivada. Consume Rabbit limpieza.solicitada; no necesita consumir eventos de identity/inventory/sales. Emisión siempre con outbox.
