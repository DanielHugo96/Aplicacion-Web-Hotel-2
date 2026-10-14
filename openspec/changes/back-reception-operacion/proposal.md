# Proposal

## Why

La fase F3 del primer incremento abre la operación de recepción: sin `reception-service` no hay ocupación real que admita ventas ni limpieza. El check-in es el primer flujo del proyecto con estados persistidos, concurrencia por habitación, doble broker (Kafka y RabbitMQ) y lápidas durables; un error ahí produce dobles ocupaciones, cobros indebidos o habitaciones bloqueadas de por vida. Por eso este plan se ejecuta en modo `openspec` ([norma §3](../../../docs/plans/README.md)), y su propuesta acompaña al plan [`docs/plans/back-reception-operacion.md`](../../../docs/plans/back-reception-operacion.md).

## What Changes

- Alta de `services/reception-service/` con su base `reception_db`: tablas `room_gate`, `recepcion`, `operacion_consumo` y `bloqueo_habitacion` del [DDL de referencia](../../../docs/especificacion/microservicios/reception/base-de-datos.md#ddl-de-referencia), más seguridad JWT (usuario y máquina), idempotencia y ETag de la convención común.
- Sincronización de gates: consumidor `reception-gates-v1` de `hotel.habitacion.v1` (HabitacionCreada inicializa; HabitacionSnapshot no libera; HabitacionLista libera solo ciclo; HabitacionHabilitada libera solo bloqueo; DLT `hotel.habitacion.v1.reception-gates-v1.dlt`).
- Check-in inmediato con adelanto y medio de pago: `POST /api/recepcion/registrar`, validación por Feign de cliente (identity) y habitación/sede (hotel), snapshots congelados, lock de gate, `roomVersion++`, emisión por outbox de `CheckInRegistrado` y `notificacion.checkin`.
- Edición de salida: `PUT /api/recepcion/actualizar/{id}` solo ACTIVA, con `If-Match`, recálculo del precio, rechazo `ADVANCE_EXCEEDS_NEW_TOTAL` y emisión de `RecepcionActualizada`.
- Lecturas de estadía: `GET /api/recepcion/listar`, `GET /api/recepcion/{id}`, `GET /api/recepcion/cliente/{idCliente}` y `GET /api/recepcion/habitacion-activa/{idHabitacion}`, con aislamiento por sede/propietario y el campo derivado `vencida`.
- Bloqueos: `POST /internal/habitaciones/{id}/bloqueos` con decisión durable por `blockId` (ACTIVO o RECHAZADO) y liberación solo con `HabitacionHabilitada` coincidente.
- Operaciones de consumo: `POST /internal/recepciones/{id}/operaciones` (admisión durable), `GET /internal/recepciones/{id}` (resumen máquina) y consumidor Rabbit `recepcion.operacion-finalizar` con inbox y lápidas `FINALIZADA`/`RECHAZADA` completas.

Sin cambios de ruptura: el monorepo no tiene código todavía y las rutas ya están declaradas en [`reception.yaml`](../../../docs/contracts/openapi/reception.yaml).

## Capabilities

### New Capabilities

- `reception/operacion`: operación del servicio de recepción — gates de ocupación sincronizados desde hotel, check-in con adelanto y medio de pago, edición de salida, lecturas por estadía con `vencida`, bloqueos administrativos durables y admisión/finalización de operaciones de consumo con lápidas.

### Modified Capabilities

Ninguna: `openspec/specs/` está vacío y no hay requisitos previos que cambien.

## Impact

- **Código**: `services/reception-service/**` (nuevo); `contracts/openapi/reception.yaml` solo se corrige si al ejecutar aparece una discrepancia con la spec.
- **Datos**: `reception_db` nueva (esquema V1 y fixtures de gates para pruebas aisladas); sin seeds de estadías activas.
- **Dependencias**: identity (`GET /internal/clientes/{id}`, scope `clientes:read`), hotel (`GET /internal/habitaciones/{id}`, scope `habitaciones:read`), Kafka (`hotel.habitacion.v1`, `reception.recepcion.v1`) y RabbitMQ (`recepcion.operacion-finalizar`, `notificacion.checkin`).
- **Casos**: OCC01–OCC03, CLN01 y STK06 de [`06-migracion-y-pruebas.md`](../../../docs/especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional), con su evidencia en el PR de ejecución.
- **Otros planes**: `back-sales-ventas` produce `recepcion.operacion-finalizar`; `back-notification-worker` consume `notificacion.checkin`; `back-reception-cierre` añade salida, anulación y `limpieza.solicitada`; `back-hotel-operaciones` emite `HabitacionLista` y `HabitacionHabilitada` y comparte OCC02, OCC03 y CLN01.
