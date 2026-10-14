# Proposal

## Why

Con el catálogo de hotel ya en F2, el servicio todavía no puede bloquear habitaciones ni limpiarlas: no existen el mantenimiento y la baja con `blockId`, la reactivación, la consulta de operaciones ni el ciclo de limpieza. Sin esto, reception no puede cerrar el ciclo de una habitación (el gate LIMPIEZA_PENDIENTE quedaría eterno), el check-in no puede rechazarse con seguridad frente a un mantenimiento concurrente y el panel no puede mostrar mantenimiento ni limpieza, que es el criterio de salida de F3 («mantenimiento 200/202 y limpieza visibles en el panel»). La habitación debe liberarse únicamente por el ciclo o bloqueo correlacionado.

## What Changes

- Migración V2 de `hotel_db` con las tablas limpieza y operacion_habitacion, sus checks e índices (una limpieza PENDIENTE y una operación vigente por habitación), derivada del DDL de referencia.
- Procedimientos `sp_limpieza_solicitar`, `sp_limpieza_completar`, `sp_habitacion_aplicar_bloqueo` y `sp_habitacion_habilitar` con la disciplina de locks de la spec (habitación → tarea; CAS de operaciones).
- Consumidor Rabbit de `limpieza.solicitada` con su cola durable, reintentos TTL y DLQ, inbox en la transacción del efecto, y reglas: valida la tupla, crea la tarea, no reabre ciclos completados, ignora ciclos viejos y manda a DLQ las contradicciones permanentes.
- Rutas de operación y limpieza del contrato: `DELETE /api/habitacion/eliminar/{id}`, `POST /api/habitacion/{id}/mantenimiento`, `PUT /api/habitacion/{id}/mantenimiento-completado`, `PUT /api/habitacion/{id}/reactivar`, `GET /api/habitacion/operaciones/{blockId}`, `GET /api/habitacion/limpiezas` y `PUT /api/habitacion/{id}/limpieza-completada`, con semántica 200/202/409, V/ETag e Idempotency-Key.
- Llamada saliente por Feign a reception `POST /internal/habitaciones/{id}/bloqueos` con `{blockId,idHotel,motivo}` y token máquina con scope `bloqueos:write`.
- Máquina de estados de operacion_habitacion (PREPARANDO → APLICADA → LIBERADA, o RECHAZADA) y job de recuperación de las PREPARANDO según el runbook RB3, que reintenta con el mismo `blockId` y nunca libera por timeout.
- Emisión por outbox en `hotel.habitacion.v1`: `HabitacionLista` (limpieza completada), `HabitacionHabilitada` (bloqueo liberado) y `HabitacionSnapshot` con estado=false en la baja aplicada.
- Efecto de la operación vigente sobre el catálogo: 409 `ROOM_OPERATION_IN_PROGRESS` en las mutaciones físicas/catálogo de esa habitación y bloqueo de los cambios de categoría o piso que la afecten.

## Capabilities

### New Capabilities

- `hotel/operaciones-habitacion`: mantenimiento, baja y reactivación de habitaciones con bloqueo durable en reception, consulta de operaciones administrativas, ciclo de limpieza desde `limpieza.solicitada` y eventos correlacionados (`HabitacionLista` y `HabitacionHabilitada`).

### Modified Capabilities

Ninguna: no hay capacidades existentes en `openspec/specs/` y este change introduce la primera.

## Impact

- `services/hotel-service/`: nuevas rutas, consumidor Rabbit, job de recuperación, emisión de eventos y los ajustes de concurrencia sobre rutas de catálogo ya existentes.
- `hotel_db`: migración V2 (limpieza, operacion_habitacion) y procedimientos nuevos; se reutiliza `last_cleaning_room_version` de habitacion.
- Contratos: rutas ya declaradas en `contracts/openapi/hotel.yaml` (se implementan tal cual) y schemas `contracts/events/` `HabitacionLista`, `HabitacionHabilitada`, `HabitacionSnapshot` y `LimpiezaSolicitada`.
- Integraciones: dependencia Feign hacia reception (`POST /internal/habitaciones/{id}/bloqueos`); el consumidor se implementa antes de que `back-reception-cierre` (F4) emita `limpieza.solicitada`; los consumidores de `HabitacionLista`/`HabitacionHabilitada` (`reception-gates-v1` y `reporting-proyeccion-v1`) son de otros planes.
- Dependencia de plan: `back-reception-operacion` debe estar `cerrado` para ejecutar.
