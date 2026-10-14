# Design

## Context

Ver [`proposal.md`](proposal.md) para la motivación. El cambio se implementa en un monorepo sin código todavía: `services/reception-service/` nace en este plan, aplica la convención ya mergeada de `back-plataforma-comunes` y `back-plataforma-mensajeria` (JWT, envelope, idempotencia, outbox/inbox, health) y no migra datos del monolito. La autoridad de admisión es `room_gate`; la ocupación que muestra hotel es una proyección informativa ([`04-flujos.md` §1](../../../docs/especificacion/04-flujos.md#1-check-in-inmediato)). Las constraints que no se pueden relajar: identificadores de operación, bloqueo y evento UUID; dinero `numeric(12,2)` con HALF_UP y comparación exacta; entrega al-menos-una-vez con efectos idempotentes; sin transacción distribuida entre brokers.

## Goals / Non-Goals

**Goals:**

- Operar la ocupación por habitación con una única transición ganadora bajo lock (`fn_checkin`, `sp_habitacion_bloquear`, `sp_operacion_admitir`/`sp_operacion_finalizar`).
- Sincronizar gates desde `hotel.habitacion.v1` sin aceptar eventos genéricos como permiso de limpieza o liberación.
- Producir `CheckInRegistrado`/`RecepcionActualizada` y `notificacion.checkin` por outbox, en la misma transacción del cambio local.
- Idempotencia y decisiones durables por `blockId`/`operationId`, aun con entregas duplicadas o tardías.

**Non-Goals:**

- Cierre de estadía, salida, anulación de check-in, `limpieza.solicitada` y `SalidaRegistrada` (plan `back-reception-cierre`, F4).
- Emisión de `HabitacionLista`/`HabitacionHabilitada` y el ciclo de limpieza (plan `back-hotel-operaciones`).
- Venta, débito de stock y cierre en Sales; consumo de `notificacion.checkin` (planes `back-sales-ventas` y `back-notification-worker`).
- Infraestructura de brokers, DLT/DLQ y pollers de outbox (planes `back-plataforma-*`).

## Decisions

1. **Esquema y migración.** `reception_db` se crea con una migración Flyway V1 derivada del [DDL de referencia](../../../docs/especificacion/microservicios/reception/base-de-datos.md#ddl-de-referencia); las tablas técnicas (`api_idempotency`, `outbox`, `inbox`, `audit_log`) llegan con la convención de plataforma. Sin seeds de estadías: los gates se crean consumiendo las 6 HabitacionCreada del seed de hotel y las pruebas aisladas cargan gates 101/102/201/202/301/302 LIBRE con una fixture equivalente a evento ([§Seeds](../../../docs/especificacion/microservicios/reception/base-de-datos.md#seeds)). Alternativa descartada: sembrar gates o estadías por SQL; el contrato prohíbe resetear gates con seed reejecutable y la admisión debe nacer del evento.

2. **Sincronización de gates por estado y correlación.** El consumidor `reception-gates-v1` decide la liberación con el estado del gate más el ciclo/bloqueo que trae el evento, y usa el inbox para el efecto único; la versión del agregado no basta por sí sola porque un `HabitacionLista`/`HabitacionHabilitada` válido puede llegar después de un snapshot más nuevo ([`04-flujos.md` §7](../../../docs/especificacion/04-flujos.md#7-entrega-duplicados-y-orden)). `HabitacionCreada` solo crea el gate ausente si la habitación es LISTA y activa y nunca sobreescribe uno existente; `HabitacionSnapshot` actualiza únicamente el seguimiento (`hotel_version`, con la `version` de la habitación del evento; informativo, nunca comparado con `roomVersion`). Las liberaciones incrementan `roomVersion` y la fila de `room_gate` queda consistente con sus CHECK.

3. **Check-in como transacción única.** Las validaciones remotas (identity `clientes:read`; hotel `habitaciones:read`) ocurren antes de abrir la transacción, y guardan los snapshots `cliente_snapshot`/`habitacion_snapshot` y la tarifa congelada. `fn_checkin` toma el lock del gate, exige LIBRE y catálogo sincronizado, inserta la estadía ACTIVA, ocupa el gate e incrementa `roomVersion`; el índice único parcial por habitación es la segunda defensa ante carreras. La idempotencia se consulta antes de evaluar `If-Match`, de modo que un reintento válido no falla por la versión que su primer intento ya incrementó ([`03-contrato-comun.md` §Headers](../../../docs/especificacion/03-contrato-comun.md#headers)).

4. **Orden de locks único.** Admisión y finalización de operaciones toman el lock de recepción antes que el de operación; bloqueos y check-in toman el lock del gate. Este orden se mantiene en todos los caminos para evitar deadlocks; los rechazos que deben dejar evidencia (bloqueo RECHAZADO, admisión tardía) se persisten y se confirman antes de responder 409.

5. **Lápidas de operaciones de consumo.** `sp_operacion_finalizar` hace inbox + upsert de la fila completa `{operationId,idRecepcion,idVenta,tipo,resultado}` y acepta llegada previa a la admisión; una combinación tipo/resultado inválida, una tupla que no coincide o un `idHotel` contradictorio van a la DLQ sin liberar otra venta. El listener no consulta stock ni dinero; su única responsabilidad es la tupla y la lápida. Reenvíos y llegadas tardías se resuelven por inbox y por estado terminal (`no revive FINALIZADA`).

6. **Idempotencia de rutas internas.** Las POST internas derivan su `Idempotency-Key` de `operationId` o `blockId` ([`reception/api.md` §Feign entrante](../../../docs/especificacion/microservicios/reception/api.md#feign-entrante)), de modo que el reintento del caller conserva la misma intención y el hash de request se compara contra el payload.

7. **Salidas por outbox.** Una fila por destino en la misma transacción: `KAFKA`/`reception.recepcion.v1` para `CheckInRegistrado` y `RecepcionActualizada` (key y partición `idHabitacion`, `aggregateVersion=roomVersion`), y `RABBIT`/`notificacion.checkin` (envelope `commandId`, `templateVersion=1`). La caída de un broker solo deja pendientes en outbox; no revierte el cambio local. El poller, backoff y alertas son de `back-plataforma-mensajeria`.

8. **Errores sin código propio.** Cuando la spec no fija un código, se usa el genérico más cercano de [`errors.md`](../../../docs/contracts/errors.md) como decisión local, sin inventar códigos: p. ej. 403 `FORBIDDEN` cuando `actorRol=CLIENTE` no coincide con la estadía o el tipo no es VENTA, y 400 `VALIDATION_ERROR` para un header requerido ausente o un campo calculado en el body.

9. **Pruebas con dependencias reales.** PostgreSQL, Kafka y RabbitMQ reales (Testcontainers o Compose), sin sustituir locks ni inbox por mocks; los casos de concurrencia (OCC01–OCC03) y de entrega (STK06, CLN01) se prueban con transacciones controladas y publicación manual de los comandos.

## Risks / Trade-offs

- [Gate inexistente por bootstrap no procesado] → 409 `ROOM_NOT_SYNCED`/503 transitorio; alertas de DLT y runbook del outbox/proyección; nunca se admite por defecto.
- [Habitación "sucia" liberada por un evento genérico] → las liberaciones exigen correlación de ciclo o bloqueo; el físico LISTA de un snapshot no libera nada.
- [Rabbit o SMTP caídos] → `notificacion.checkin` queda pendiente en outbox; el check-in no se revierte; el correo nunca es confirmación autoritativa.
- [Comando de finalización antes de la admisión] → lápida completa e idempotente; la admisión tardía no revive estado terminal (STK06).
- [Duplicados end-to-end] → inbox/outbox y decisiones durables por `blockId`; el mismo `blockId` conserva su decisión aun cuando el gate cambie.
- [Zona horaria] → fechas de negocio en America/Lima calculadas en servidor; `vencida` es derivado y no se persiste.
- [Carrera check-in vs mantenimiento (OCC02)] → el lock del gate decide un único ganador: OCUPADA o BLOQUEADA, nunca ambas.
