# Design

## Context

Hotel ya tiene en F2 el catálogo, las habitaciones, los estados físicos fijos y el outbox/inbox de plataforma; el servicio aún no tiene la tabla `operacion_habitacion` ni `limpieza`, ni el consumidor de comandos. La motivación está en `proposal.md`. Restricciones que gobiernan el diseño:

- No hay transacción distribuida: la coordinación hotel ↔ reception es un Feign idempotente por `blockId` más eventos correlacionados por Kafka (no hay garantía de orden Kafka ↔ Rabbit).
- Los rechazos durables se persisten con commit antes de responder 409; los efectos (mutación + outbox + idempotencia/inbox) van en una sola transacción local.
- La spec ya fija rutas, estados, DDL, procedimientos, envelopes y semántica 200/202/409; este documento solo decide el «cómo» donde la spec deja margen.
- El consumidor de `limpieza.solicitada` se implementa antes de que exista su productor (F4); su verificación end-to-end queda para `qa-matriz-aceptacion`.

## Goals / Non-Goals

**Goals:**

- Implementar la máquina de estados de `operacion_habitacion` con CAS y el bloqueo durable correlacionado, de forma que una habitación nunca quede a medio bloquear ni bloqueada sin dueño.
- Ejecutar el ciclo de limpieza de forma idempotente y ordenada por `cleaningCycleId`/`roomVersion`, sin reabrir ni liberar ciclos ajenos.
- Emitir `HabitacionLista`, `HabitacionHabilitada` y el snapshot de baja por outbox, con la correlación exigida por el contrato.
- Recuperar las operaciones PREPARANDO con el job del runbook RB3 y hacer observable su estado.

**Non-Goals:**

- El `room_gate` y la lógica de liberación de reception (otro servicio y otro plan).
- El cierre de estadía y la emisión de `limpieza.solicitada` (F4, `back-reception-cierre`).
- Los consumidores del topic `hotel.habitacion.v1` (`reception-gates-v1`, `reporting-proyeccion-v1`).
- Cambios de esquema en las tablas de catálogo más allá de reutilizar `last_cleaning_room_version`.

## Decisions

1. **Persistencia y procedimientos tal como la spec.** Migración V2 aditiva con limpieza y operacion_habitacion (checks, único parcial de limpieza PENDIENTE y de operación vigente, índice de recuperación). Los locks siguen el orden fijado: limpieza hace lock habitación → tarea y nunca toma después un lock de padre; los cambios de catálogo ya usan hotel → categoría → piso → habitación. Se implementan los cuatro procedimientos (`sp_limpieza_solicitar`, `sp_limpieza_completar`, `sp_habitacion_aplicar_bloqueo`, `sp_habitacion_habilitar`) porque concentran el CAS y la comparación de versiones en la base, sin HTTP ni publicación a brokers. Alternativa descartada: hacer todo con JPA y locks a nivel de servicio; la spec fija las firmas y el orden de locks, y los rechazos deben ser resultado de negocio persistido.

2. **Coordinación con reception por `blockId` idempotente.** La operación se persiste primero (PREPARANDO) y luego se pide el bloqueo; el POST a reception `{blockId,idHotel,motivo}` es idempotente por `blockId` y su rechazo también queda persistido en reception. Lecturas seguidas de escritura quedan descartadas: el flujo exige que la admisión sea atómica (solo LIBRE → BLOQUEADA). El 200 aplica el CAS en el mismo request; el 202 deja la operación al job; el 409 deja la operación RECHAZADA. El mapa 200/202/409 se implementa como un único flujo con transacciones cortas (nunca transacción SQL abierta durante el Feign).

3. **Rechazo durable con commit antes del 409.** Cuando reception rechaza, hotel marca su operación RECHAZADA y confirma antes de responder; así la habitación no queda trabada por una operación «vigente» de un rechazo, y el `blockId` no se admite después aunque el gate quede libre. Si el proceso cae entre el rechazo remoto y la marca local, el job del punto 4 reconcilia (RB3).

4. **Job de recuperación de PREPARANDO.** Un job periódico lee las operaciones PREPARANDO vencidas (`ix_operacion_recuperacion` sobre `estado` y `next_attempt_at`) y reintenta con el mismo `blockId`: si el bloqueo no existe, repite el POST idempotente; si está ACTIVO, aplica el CAS local; si fue rechazado, marca RECHAZADA. Nunca libera por timeout ni «arregla» el gate por SQL. La periodicidad y el backoff concretos se fijan al ejecutar como propiedades propias del servicio y se anotan en el PR (la spec no fija esos valores); `attempts` y `next_attempt_at` quedan visibles para la alerta de > 10 min. Alternativa descartada: compensar desocupando por SQL, prohibido por la spec.

5. **Consumidor de limpieza con inbox y correlación.** El consumer valida envelope y schema, aplica inbox + `sp_limpieza_solicitar` en la misma transacción y confirma después del commit. La comparación `roomVersion` ↔ `last_cleaning_room_version` decide si el ciclo reemplaza al vigente; los duplicados y ciclos viejos terminan en éxito sin efecto; la contradicción permanente (otro ciclo/habitación/recepción) va directo a DLQ. Alternativa descartada: crear la tarea en un método separado del ack (permitiría doble creación o pérdida del mensaje).

6. **Eventos correlacionados, sin liberar por snapshot genérico.** `HabitacionLista` lleva `cleaningCycleId` y `cleaningRoomVersion` de la tarea; `HabitacionHabilitada` lleva el `blockId` liberado; ambos se emiten por outbox en la transacción del cambio. La baja aplicada emite `HabitacionSnapshot` con estado=false (el schema exige el snapshot completo en una baja). El mantenimiento aplicado no emite snapshot propio: no es un cambio de catálogo y su visibilidad es el gate de reception y el catálogo local; de lo contrario se estaría inventando un efecto no pedido. Los eventos de limpieza/habilitación conservan su correlación aunque la version de la habitación sea antigua respecto de otro cambio de catálogo.

7. **Aislamiento por rol/sede y token máquina.** Resource server JWT con autorización por rol y sede en cada ruta (A global; E solo sedes; C fuera), deny-by-default; la identidad nunca viaja por headers. El Feign obtiene token máquina con scope `bloqueos:write`; la misma validación estricta de issuer/audiencia/aplicada al token máquina.

8. **Detalles no fijados por la spec.** Los 409 de estas rutas sin código propio (por ejemplo, reactivación con padre inactivo u operación no aplicada, o completar mantenimiento con un `blockId` que no corresponde) usan el genérico más cercano de `contracts/errors.md`, siguiendo el criterio ya aceptado en `back-hotel-catalogo`; el mapeo final se revisa en el PR de ejecución. Los nombres de la migración V2 y de las propiedades de configuración del job también quedan para ese PR.

## Risks / Trade-offs

- [Una operación PREPARANDO sin bloqueo aún bloquea mutaciones de la habitación] → el job reintenta el POST idempotente y aplica el CAS; la alerta de > 10 min y el runbook RB3 dan salida manual sin tocar el gate.
- [Timeout del Feign ambiguo (¿se creó el bloqueo?)] → el `blockId` es la clave de idempotencia en ambos lados; el job repite el mismo POST antes de aplicar; nunca se libera por timeout.
- [Evento `HabitacionLista`/`HabitacionHabilitada` viejo entregado tras un ciclo/bloqueo nuevo] → los eventos llevan la correlación y los consumidores liberan solo la coincidencia; hotel garantiza que el payload lleva la correlación correcta y no descarta la liberación por antigüedad de version.
- [Duplicación Kafka/Rabbit] → outbox/inbox y claves de negocio (`blockId`, `cleaningCycleId`); el reenvío conserva `eventId`/`commandId` y no duplica efectos.
- [El consumidor se prueba sin productor real en F3] → fixtures en `qa-matriz-aceptacion` con el productor F4; los casos CLN usan mensajes de prueba con el envelope del comando.
- [Contradicción permanente en un comando] → DLQ `limpieza.solicitada.dlq` con el mensaje original; la habitación no se toca y el operador usa el runbook de DLQ.

## Migration Plan

- Flyway V2 es aditivo (tablas nuevas y procedimientos); no hay migración de datos. El despliegue del servicio habilita las rutas nuevas sin afectar el CRUD de F2.
- Rollback: revertir el despliegue del servicio; las tablas nuevas pueden quedar sin uso (no hay datos operativos que conservar antes de la demo) y el catálogo de F2 sigue funcionando.
- El consumidor declara su cola y derivadas al arrancar; los mensajes sin consumidor quedan en la cola durable hasta que el servicio arranque.
