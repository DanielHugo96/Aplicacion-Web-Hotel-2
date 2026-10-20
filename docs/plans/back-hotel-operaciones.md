# back-hotel-operaciones — mantenimiento, baja, reactivación y limpieza de habitaciones (F3)

| Campo | Valor |
|---|---|
| Plan | `back-hotel-operaciones` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F3` |
| Modo | `openspec` |
| Estado | `cerrado` |
| Depende de | `back-reception-operacion` |
| Spec | [`hotel/api.md` §Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico), [§DTO](../especificacion/microservicios/hotel/api.md#dto), [§Feign interno](../especificacion/microservicios/hotel/api.md#feign-interno) y [§Mensajería](../especificacion/microservicios/hotel/api.md#mensajería); [`hotel/base-de-datos.md` §Tablas](../especificacion/microservicios/hotel/base-de-datos.md#tablas), [§DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia), [§Esquemas JSONB](../especificacion/microservicios/hotel/base-de-datos.md#esquemas-jsonb) y [§Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos); [`03-contrato-comun.md` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [§Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§Headers](../especificacion/03-contrato-comun.md#headers) y [§Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal); [`04-flujos.md` §5](../especificacion/04-flujos.md#5-limpieza-sin-ventana-de-doble-ingreso), [§6](../especificacion/04-flujos.md#6-mantenimiento-y-baja-de-habitación) y [§7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden); [`05-mensajeria.md` §Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta), [§Envelope de evento Kafka](../especificacion/05-mensajeria.md#envelope-de-evento-kafka), [§Envelope de comando RabbitMQ](../especificacion/05-mensajeria.md#envelope-de-comando-rabbitmq) y [§Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit); [`06-migracion-y-pruebas.md` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional); [`hotel.yaml`](../contracts/openapi/hotel.yaml) (rutas de habitación, físico y limpieza), [`errors.md`](../contracts/errors.md) y los schemas [`HabitacionLista`](../contracts/events/hotel.habitacion.v1.HabitacionLista.schema.json), [`HabitacionHabilitada`](../contracts/events/hotel.habitacion.v1.HabitacionHabilitada.schema.json), [`HabitacionSnapshot`](../contracts/events/hotel.habitacion.v1.HabitacionSnapshot.schema.json) y [`LimpiezaSolicitada`](../contracts/events/limpieza.solicitada.LimpiezaSolicitada.v1.schema.json); runbook [§RB3](../especificacion/infraestructura/runbooks.md#rb3--operación-de-habitación-en-preparando) |
| Casos | `OCC02`, `OCC03`, `CLN01–CLN03` |
| Change | `openspec/changes/back-hotel-operaciones/` |

## 1. Objetivo

Al cerrar el plan, `hotel-service` bloquea habitaciones con reception por `blockId` (200/202/409 durables), aplica mantenimiento o baja con CAS `PREPARANDO → APLICADA`, reactiva solo con padres activos, expone la consulta de operaciones y ejecuta el ciclo de limpieza (consumo de `limpieza.solicitada`, listado y completado con V de la tarea), emitiendo `HabitacionLista` y `HabitacionHabilitada` por outbox para que una habitación se libere únicamente por el ciclo o bloqueo correlacionado. Los casos `OCC02`, `OCC03` y `CLN01–CLN03` quedan evidenciados en su parte de hotel.

## 2. Alcance

**Entra:**

- Migración V2 de `hotel_db` con las tablas limpieza y operacion_habitacion, sus checks e índices (una limpieza PENDIENTE por habitación y una operación vigente por habitación), derivadas del [DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia) y del [esquema JSONB `request`](../especificacion/microservicios/hotel/base-de-datos.md#esquemas-jsonb).
- Procedimientos `sp_limpieza_solicitar`, `sp_limpieza_completar`, `sp_habitacion_aplicar_bloqueo` y `sp_habitacion_habilitar` con la disciplina de locks de [§Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos).
- Consumidor Rabbit de `limpieza.solicitada`: cola durable con reintentos TTL y DLQ según [§Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit), inbox en la transacción del efecto y ack tras el commit; valida la tupla del comando, crea la tarea y pone EN_LIMPIEZA, no reabre ciclos completados, ignora ciclos viejos y manda a DLQ las contradicciones permanentes.
- Las rutas del catálogo de endpoints: `DELETE /api/habitacion/eliminar/{id}`, `POST /api/habitacion/{id}/mantenimiento`, `PUT /api/habitacion/{id}/mantenimiento-completado`, `PUT /api/habitacion/{id}/reactivar`, `GET /api/habitacion/operaciones/{blockId}`, `GET /api/habitacion/limpiezas` y `PUT /api/habitacion/{id}/limpieza-completada`, con su semántica 200/202/409, V/ETag e Idempotency-Key.
- La máquina de estados de operacion_habitacion (PREPARANDO → APLICADA → LIBERADA, o RECHAZADA) y el job de recuperación de las PREPARANDO según [RB3](../especificacion/infraestructura/runbooks.md#rb3--operación-de-habitación-en-preparando): reintento con el mismo `blockId` (aplicación local por CAS o POST idempotente), sin liberar por timeout.
- La llamada saliente a reception `POST /internal/habitaciones/{id}/bloqueos` con `{blockId,idHotel,motivo}`, token máquina con scope `bloqueos:write` y los timeouts/circuit breaker del común.
- La emisión por outbox en `hotel.habitacion.v1`: `HabitacionLista` al completar limpieza, `HabitacionHabilitada` al liberar un bloqueo y `HabitacionSnapshot` con estado=false en la baja aplicada, con key `idHabitacion` y payload validado contra los schemas.
- El efecto de la operación vigente sobre las rutas ya existentes: 409 `ROOM_OPERATION_IN_PROGRESS` en las mutaciones físicas/catálogo de esa habitación y bloqueo de los cambios de categoría/piso que afecten a una habitación en PREPARANDO, delegado por `back-hotel-catalogo`.
- La evidencia de la parte de hotel de `OCC02`, `OCC03` y `CLN01–CLN03`.

**Fuera de alcance:**

- El `room_gate`, el bloqueo durable `POST /internal/habitaciones/{id}/bloqueos` y la liberación del gate al consumir `HabitacionLista`/`HabitacionHabilitada` → `back-reception-operacion`; aquí solo se llama y se emite la correlación.
- El cierre de estadía, su cobro y la emisión de `limpieza.solicitada` → `back-reception-cierre` (F4); este plan implementa el consumidor y la prueba end-to-end se completa en `qa-matriz-aceptacion`.
- Sedes, pisos, categorías, habitaciones, estados físicos, catálogo público, rutas internas, `HabitacionCreada`/`HabitacionSnapshot` de catálogo y el consumidor `hotel-ocupacion-v1` → `back-hotel-catalogo`; los consumidores `reception-gates-v1` y `reporting-proyeccion-v1` de este topic → sus planes.
- El panel de limpieza y mantenimiento → `front-admin-web-recepcion`.
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.

## 3. Contexto

- Este plan cubre la parte de hotel del criterio de salida de F3 ([norma §2](README.md#2-fases): «mantenimiento 200/202 y limpieza visibles en el panel»). Depende de `back-reception-operacion` (gate, bloqueo por `blockId` y semántica de limpieza); para ejecutar, esa dependencia debe estar `cerrado` ([norma §6.2](README.md#6-contrato-con-la-ejecutora)).
- La spec fija y este plan no redefine: rutas, accesos, entradas y semántica 200/202/409 ([`hotel/api.md` §Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico) y [`hotel.yaml`](../contracts/openapi/hotel.yaml)); tablas, DDL, procedimientos y locks ([`hotel/base-de-datos.md`](../especificacion/microservicios/hotel/base-de-datos.md)); los pasos de los flujos de [mantenimiento y baja](../especificacion/04-flujos.md#6-mantenimiento-y-baja-de-habitación) y [limpieza](../especificacion/04-flujos.md#5-limpieza-sin-ventana-de-doble-ingreso), y la correlación de [§7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden); el envelope, la key por agregado y la cola con reintentos y DLQ ([`05`](../especificacion/05-mensajeria.md)); la identidad por JWT validado —nunca cabeceras `X-User-Id`, `X-Role` ni `X-Hotel-Id`—, idempotencia, If-Match/ETag y el commit de los rechazos durables antes de responder 409 ([`03` §Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas) y [§Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal)); y los códigos y mensajes de [`errors.md`](../contracts/errors.md).
- El consumidor de `limpieza.solicitada` se implementa antes que su productor real: `back-reception-cierre` (F4) emite el comando recién al cerrar la estadía; la prueba con el productor real y los consumidores de `HabitacionLista`/`HabitacionHabilitada` se completa en `qa-matriz-aceptacion` ([catalogo.md §2](catalogo.md#2-mensajería)).
- Decisiones de este plan (la spec no las fija): el 409 del gate deja la operación RECHAZADA con commit antes de responder (es el resultado terminal persistido tras crear la operación durable); si el proceso cae antes de esa marca, el job del paso 13 la reconcilia según [RB3](../especificacion/infraestructura/runbooks.md#rb3--operación-de-habitación-en-preparando); la periodicidad y el backoff del job se fijan al ejecutar como propiedades propias del servicio y se anotan en el PR, dentro de `attempts`/`next_attempt_at` y sin liberar por timeout; los rechazos de estas rutas sin código propio usan el genérico más cercano de [`errors.md`](../contracts/errors.md), como ya aceptó `back-hotel-catalogo`; el mantenimiento aplicado no emite snapshot propio (no es cambio de catálogo): su visibilidad es el gate de reception y el catálogo local, mientras la baja sí conserva el snapshot completo con estado=false según el schema; y los nombres de la migración V2 y de las propiedades del job se fijan al ejecutar y quedan en el PR.
- Sin secretos en Git: credenciales, claves y contraseñas solo por variables de entorno ([norma §6.10](README.md#6-contrato-con-la-ejecutora)); los valores de demo se configuran al ejecutar.

## 4. Pasos

- [x] 1. Migración V2 de `hotel_db` con las tablas limpieza y operacion_habitacion y sus índices `uq_limpieza_pendiente`, `ix_limpieza_habitacion_estado`, `uq_operacion_vigente` e `ix_operacion_recuperacion`, tal como el [DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia), incluidos los checks de estado y de completed_at con tarea completada. Verificación: la migración aplica sobre el esquema de F2; pruebas SQL demuestran que dos limpiezas PENDIENTE de la misma habitación fallan, que una segunda operación PREPARANDO/APLICADA de la misma habitación falla por el índice vigente y que un `blockId` repetido falla por PK.
- [x] 2. Procedimientos de limpieza: `sp_limpieza_solicitar` (inbox + upsert del ciclo, valida la tupla, compara y actualiza `last_cleaning_room_version`, deja EN_LIMPIEZA; duplicados y ciclos viejos no reabren ciclos completos) y `sp_limpieza_completar` (lock habitación → tarea, valida ciclo/habitación/sede y version de la tarea; PENDIENTE → COMPLETADA con completed_at, físico LISTA e incremento de versiones). Verificación: pruebas SQL demuestran que reenviar el mismo ciclo no crea otra tarea, que un ciclo con `room_version` menor al guardado no reemplaza el actual ni reabre uno completado, que completar con version de tarea distinta falla, y que tras completar la tarea queda COMPLETADA con completed_at y la habitación LISTA.
- [x] 3. Procedimientos de operaciones: `sp_habitacion_aplicar_bloqueo` (solo tras Feign exitoso; CAS PREPARANDO → APLICADA; MANTENIMIENTO cambia el físico y BAJA deja la baja lógica; LIBERADA/RECHAZADA no reaplican) y `sp_habitacion_habilitar` (exige operación aplicada; deja estado=true y físico LISTA y libera la operación para su habilitación correlacionada). Verificación: pruebas SQL del CAS y de que una operación LIBERADA o RECHAZADA no vuelve a aplicar; habilitar una habitación en baja deja estado=true y LISTA y no permite reaplicar la baja.
- [x] 4. Consumidor Rabbit de `limpieza.solicitada`: cola durable `limpieza.solicitada` con `limpieza.solicitada.retry.5s`, `.retry.30s`, `.retry.120s` y `limpieza.solicitada.dlq` ([§Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit)), validación del envelope y del [schema](../contracts/events/limpieza.solicitada.LimpiezaSolicitada.v1.schema.json), inbox y efecto en la misma transacción y ack tras el commit. Verificación: publicar el mismo comando dos veces deja una sola tarea y se confirma sin error; el mismo ciclo con otra habitación o recepción termina en `limpieza.solicitada.dlq` sin efecto; un schema inválido va directo a DLQ; el offset solo se confirma después del commit local.
- [x] 5. `GET /api/habitacion/limpiezas` (A/E): `idHotel` obligatorio y E limitado a sus sedes asignadas, filtro `estado=PENDIENTE/COMPLETADA` opcional, paginación con page/size y orden estable; cada `Limpieza` incluye `roomVersion` y `version` (la version es el If-Match de la tarea). Verificación: 200 con data y meta de paginación; el filtro devuelve solo el estado pedido; una sede no asignada no expone datos; el orden es estable entre llamadas.
- [x] 6. `PUT /api/habitacion/{id}/limpieza-completada` (A/E, V = versión de la tarea, Idempotency-Key): valida ciclo/habitación/sede y la version de la tarea, pasa PENDIENTE → COMPLETADA y devuelve `{limpieza,habitacion}` con ETag de la tarea terminada. Verificación: 200 con estado COMPLETADA y físico LISTA; sin If-Match 428 `PRECONDITION_REQUIRED`; versión distinta 412 `PRECONDITION_FAILED`; repetir con la misma clave devuelve la misma respuesta sin efectos nuevos.
- [x] 7. `POST /api/habitacion/{id}/mantenimiento` (A, V, Idempotency-Key): persiste la operación PREPARANDO con `blockId`, tipo MANTENIMIENTO y `request` JSONB inmutable (motivo 1..300, idHotel y requestedAt según el [esquema JSONB](../especificacion/microservicios/hotel/base-de-datos.md#esquemas-jsonb)); V valida la versión antes de crear la operación y el índice vigente protege contra operaciones paralelas. Verificación: dos POST concurrentes con claves distintas dejan una sola operación vigente y un 409 `ROOM_OPERATION_IN_PROGRESS`; misma clave y cuerpo devuelven la respuesta original; sin If-Match 428; versión vieja 412; motivo vacío o mayor a 300 → 400 `VALIDATION_ERROR` con el campo en error.fields.
- [x] 8. Llamada saliente a reception en mantenimiento: `POST /internal/habitaciones/{id}/bloqueos` con `{blockId,idHotel,motivo}` usando token máquina con scope `bloqueos:write`, fuera de la transacción SQL y con los timeouts/circuit breaker del común; semántica 200 (gate BLOQUEADA y CAS PREPARANDO → APLICADA en el request, respuesta terminal con Habitacion y `blockId`), 202 (operación PREPARANDO porque la admisión no se confirmó: timeout, 503 o `ROOM_NOT_SYNCED`, con `blockId` + `Location` + `Retry-After`) y 409 durable (`ROOM_OCCUPIED`, `ROOM_NOT_READY` o `GATE_NOT_AVAILABLE`). Verificación: con reception disponible y gate libre, 200 con gate BLOQUEADA y operación APLICADA; con reception detenido o respuesta incierta, 202 con `Location` y `Retry-After`; con gate OCUPADA o LIMPIEZA_PENDIENTE, 409 con el código correspondiente y la operación RECHAZADA persistida; el reintento del mismo `blockId` es idempotente.
- [x] 9. `DELETE /api/habitacion/eliminar/{id}` (A, V, Idempotency-Key): misma semántica 200/202/409 con tipo BAJA; al aplicar deja la baja lógica (estado=false), conserva `blockId` hasta la reactivación y libera el número para una habitación nueva de la misma sede (el histórico se identifica por idHabitacion). Verificación: 200 con estado=false y `blockId`; 202 con `Location`/`Retry-After` cuando la admisión no se confirma; 409 durable cuando el gate lo rechaza; tras la baja, el mismo número se puede dar de alta en otra habitación activa de la sede.
- [x] 10. `PUT /api/habitacion/{id}/mantenimiento-completado` (A, V, Idempotency-Key): exige que el `blockId` sea la operación MANTENIMIENTO aplicada; deja el físico LISTA y encola la `HabitacionHabilitada` correlacionada para que reception libere solo el bloqueo coincidente. Verificación: 200 con habitación LISTA y la operación en LIBERADA; con otro `blockId` o una operación no aplicada, 409 sin liberar; sin If-Match 428; versión vieja 412; repetir con la misma clave devuelve el mismo resultado.
- [x] 11. `PUT /api/habitacion/{id}/reactivar` (A, V, Idempotency-Key): exige la operación BAJA APLICADA y sede/piso/categoría activos; deja estado=true y físico LISTA, incrementa version y encola la habilitación correlacionada. Verificación: 200 con estado=true y version+1; con un padre inactivo, 409 sin habilitar y sin habitación activa bajo padre inactivo; con la operación no aplicada, 409; repetir con la misma clave devuelve el mismo resultado.
- [x] 12. `GET /api/habitacion/operaciones/{blockId}` (solo A): devuelve `{blockId,idHabitacion,tipo,estado}` de la operación; 404 si no existe; las rutas estáticas limpiezas/operaciones tienen prioridad sobre la ruta genérica de IDs. Verificación: consulta de una operación en cada estado; UUID inexistente 404 `RESOURCE_NOT_FOUND`; `GET /api/habitacion/limpiezas` y `GET /api/habitacion/operaciones/{blockId}` no chocan con la ruta de ID.
- [x] 13. Job de recuperación de operaciones PREPARANDO ([RB3](../especificacion/infraestructura/runbooks.md#rb3--operación-de-habitación-en-preparando)): reintenta con el mismo `blockId` —si el bloqueo no existe en reception, repite el POST idempotente; si está ACTIVO, aplica el CAS PREPARANDO → APLICADA; si fue rechazado, deja la operación RECHAZADA—, respeta `attempts`/`next_attempt_at` y nunca libera por timeout ni edita el gate por SQL. Verificación: simular la caída entre persistir y confirmar la admisión (fixture F5): el job aplica o rechaza según el estado del bloqueo y no quedan mutaciones bloqueadas por una PREPARANDO; el contador de intentos avanza y la alerta de PREPARANDO > 10 min de la tabla de [runbooks](../especificacion/infraestructura/runbooks.md#alertas-y-umbrales) es visible.
- [x] 14. Rechazo durable (`OCC03`): el 409 del gate queda persistido por `blockId`; reintentar el mismo `blockId` sigue devolviendo 409 `GATE_NOT_AVAILABLE` aunque la habitación quede libre, y una nueva intención con otro `blockId` puede admitirse. Verificación: con el fixture F6, cerrar la estadía de 201 y reintentar el mismo `blockId` → 409; nueva intención con otro `blockId` → 200; la fila de `operacion_habitacion` queda RECHAZADA y no se reaplica; evidencia: respuestas y `bloqueo_habitacion` RECHAZADO vs ACTIVO.
- [x] 15. Efecto de la operación vigente sobre el catálogo: con una operación PREPARANDO/APLICADA sin liberar, las mutaciones físicas y de catálogo de esa habitación responden 409 `ROOM_OPERATION_IN_PROGRESS`, y un cambio de categoría o piso que afecte a una habitación en PREPARANDO devuelve 409 sin aplicar el cambio ([`hotel/api.md` §Mensajería](../especificacion/microservicios/hotel/api.md#mensajería) y [`base-de-datos.md` §Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos)); es el 409 delegado por `back-hotel-catalogo`. Verificación: `PUT /api/habitacion/actualizar/{id}` con operación vigente → 409; un cambio de descripción de categoría con una habitación afectada en PREPARANDO no se aplica; tras liberar la operación, las mismas mutaciones vuelven a aplicarse.
- [x] 16. Emisión por outbox en `hotel.habitacion.v1` con key `idHabitacion` y payload completo: `HabitacionLista` al completar la limpieza (con `cleaningCycleId` y `cleaningRoomVersion` de la tarea), `HabitacionHabilitada` al liberar un bloqueo (`blockId` obligatorio) y `HabitacionSnapshot` con estado=false en la baja aplicada; ninguna mutación emite desde la respuesta HTTP: todo va a la fila del outbox en su transacción. Verificación: cada mutación deja exactamente una fila por evento y el mensaje valida contra [`HabitacionLista`](../contracts/events/hotel.habitacion.v1.HabitacionLista.schema.json), [`HabitacionHabilitada`](../contracts/events/hotel.habitacion.v1.HabitacionHabilitada.schema.json) y [`HabitacionSnapshot`](../contracts/events/hotel.habitacion.v1.HabitacionSnapshot.schema.json); los eventos de limpieza y habilitación conservan la correlación de ciclo/bloqueo aunque la versión de la habitación sea antigua (no se descarta un efecto de liberación válido); el reenvío conserva `eventId` y key.
- [x] 17. Concurrencia `OCC02`: check-in y `POST /api/habitacion/{id}/mantenimiento` (habitación 102 del caso) concurrentes sobre la misma habitación: solo una admisión es válida, el gate final queda OCUPADA o BLOQUEADA —nunca ambas— y el perdedor recibe 409. Verificación: ejecutar los dos requests simultáneos; comprobar `room_gate` y `operacion_habitacion`; el perdedor no deja efectos (sin bloqueo ni cambio físico).
- [x] 18. Limpieza de punta a punta (`CLN01–CLN03`) en su parte de hotel: con el fixture F8 y Rabbit pausado, reanudar el broker entrega el comando; sin completar el ciclo correcto el check-in sigue recibiendo 409 `ROOM_NOT_READY`, y `PUT /api/habitacion/{id}/limpieza-completada` sobre la habitación 101 con el ciclo correcto completa la tarea y emite `HabitacionLista` (reception libera el gate y el check-in pasa a 201); reenviar el comando de un ciclo ya completado no reabre la tarea ni libera el ciclo actual (un solo registro por ciclo); el `HabitacionLista` de C1 entregado antes de la salida o después de un check-in nuevo conserva su `cleaningRoomVersion` y no libera una ocupación posterior. Verificación: consola Rabbit + `limpieza` + gate de reception + proyecciones de reporting; la prueba con el productor real (F4) y los consumidores se cierra en `qa-matriz-aceptacion`.
- [x] 19. Cierre: correr los comandos canónicos de la sección «Verificación» del `README.md` raíz (build, pruebas y Compose con PostgreSQL y brokers) y `python scripts/validar-planes.py` desde la raíz; adjuntar al PR la evidencia. Verificación: build y pruebas en verde; las respuestas y consultas de los pasos 1 a 18 quedan adjuntas como evidencia de `OCC02`, `OCC03` y `CLN01–CLN03` en su parte de hotel; 0 errores del validador.

## 5. Contratos afectados

- OpenAPI (`contracts/openapi/hotel.yaml`): este plan implementa las rutas ya declaradas `DELETE /api/habitacion/eliminar/{id}`, `POST /api/habitacion/{id}/mantenimiento`, `PUT /api/habitacion/{id}/mantenimiento-completado`, `PUT /api/habitacion/{id}/reactivar`, `GET /api/habitacion/operaciones/{blockId}`, `GET /api/habitacion/limpiezas` y `PUT /api/habitacion/{id}/limpieza-completada`, con sus respuestas 200/202 (`AcceptedOp` con `Location` y `Retry-After`), 409 `RoomOperationConflict`, ETag/If-Match y los schemas `Habitacion`, `Limpieza`, `LimpiezaResultado` y `OperacionHabitacion`; además activa el 409 `ROOM_OPERATION_IN_PROGRESS` ya descrito en `PUT /api/habitacion/actualizar/{id}`. Si al ejecutar aparece una discrepancia con la spec, se corrige en ese mismo YAML.
- Eventos y comandos: produce `hotel.habitacion.v1` por outbox (tipos `HabitacionLista`, `HabitacionHabilitada` y `HabitacionSnapshot` de baja) con los schemas [`HabitacionLista`](../contracts/events/hotel.habitacion.v1.HabitacionLista.schema.json), [`HabitacionHabilitada`](../contracts/events/hotel.habitacion.v1.HabitacionHabilitada.schema.json) y [`HabitacionSnapshot`](../contracts/events/hotel.habitacion.v1.HabitacionSnapshot.schema.json); consume el comando Rabbit `limpieza.solicitada` con la cola y sus derivadas ([`05` §Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit)) y valida contra [`LimpiezaSolicitada`](../contracts/events/limpieza.solicitada.LimpiezaSolicitada.v1.schema.json). Llama por Feign `POST /internal/habitaciones/{id}/bloqueos` (reception, scope `bloqueos:write`), que no entra por el Gateway.
- Persistencia: migración V2 de `hotel_db` con limpieza y operacion_habitacion (checks, índices parciales y de recuperación) derivada del [DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia); procedimientos `sp_limpieza_solicitar`, `sp_limpieza_completar`, `sp_habitacion_aplicar_bloqueo` y `sp_habitacion_habilitar`; se reutiliza `last_cleaning_room_version` de habitacion. Las tablas técnicas (api_idempotency, outbox e inbox) ya existen por los planes `back-plataforma-*`.
- Errores: `ROOM_OCCUPIED`, `ROOM_NOT_READY`, `GATE_NOT_AVAILABLE`, `ROOM_OPERATION_IN_PROGRESS`, `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `IDEMPOTENCY_CONFLICT`, `PRECONDITION_REQUIRED`, `PRECONDITION_FAILED`, `UNAUTHENTICATED` y `FORBIDDEN`; los rechazos sin código propio usan el genérico más cercano de [`errors.md`](../contracts/errors.md) (decisión del §3).

## 6. Pruebas

- Caso `OCC02` (parte de hotel): check-in y `POST /api/habitacion/{id}/mantenimiento` sobre la habitación 102 concurrentes → una sola admisión, gate final OCUPADA o BLOQUEADA y perdedor 409; evidencia: respuestas + `room_gate` y `operacion_habitacion`.
- Caso `OCC03` (parte de hotel): con el fixture F6, cerrar la estadía de 201 y reintentar el mismo `blockId` → 409 `GATE_NOT_AVAILABLE`; nueva intención con otro `blockId` → 200; evidencia: respuestas + `bloqueo_habitacion` RECHAZADO vs ACTIVO.
- Caso `CLN01` (parte de hotel): con el fixture F8 y Rabbit pausado, el check-in reintentado recibe 409 `ROOM_NOT_READY`; al reanudar, `PUT /api/habitacion/{id}/limpieza-completada` sobre la habitación 101 con el ciclo correcto deja la limpieza COMPLETADA y el gate liberado (reception), con `HabitacionLista` emitido; evidencia: respuestas + `limpieza` + gate.
- Caso `CLN02` (parte de hotel): reenviar `limpieza.solicitada` del ciclo C1 completado y entregar un `HabitacionLista` viejo tras un nuevo check-in no reabre la tarea ni libera el ciclo actual; un solo registro por ciclo; evidencia: consola Rabbit + `limpieza` + gate.
- Caso `CLN03` (parte de hotel): el `HabitacionLista` de C1 entregado antes de la salida o después del check-in nuevo conserva el ciclo y no libera la ocupación posterior; el evento lleva el `cleaningRoomVersion` de la tarea; evidencia: `gate_projection` y `limpieza_completada_projection` de reporting + gate. La prueba con el productor real (F4) y los consumidores se completa en `qa-matriz-aceptacion`.
- Comandos: los de la sección «Verificación» del `README.md` raíz (build, pruebas y Compose) y `python scripts/validar-planes.py`; evidencia: salidas adjuntas al PR de ejecución.

### Evidencia de ejecución (2026-10-20)

Salida literal de las pruebas SQL y de los endpoints ejecutados contra PostgreSQL, Kafka y RabbitMQ
del Compose local (`hotel-postgres-1`, `hotel-kafka-1`, `hotel-rabbitmq-1`), con identity, reception y
hotel arrancados desde este árbol.

**Pasos 1–3 (migración y procedimientos).** `psql` sobre `hotel_db`; la migración quedó aplicada
como `5 | hotel operaciones | t` en `flyway_schema_history`.

```
=== step1: indices ===
ERROR:  duplicate key value violates unique constraint "uq_limpieza_pendiente"
DETAIL:  Key (id_habitacion)=(303) already exists.
ERROR:  duplicate key value violates unique constraint "uq_operacion_vigente"
DETAIL:  Key (id_habitacion)=(303) already exists.
ERROR:  duplicate key value violates unique constraint "operacion_habitacion_pkey"
DETAIL:  Key (block_id)=(00000000-0000-4000-8000-000000000021) already exists.

=== step2: procedimientos limpieza ===
primera_solicitud       {"ok": true, "estado": "PENDIENTE", "created": true, "version": 1, ...}
ciclo_viejo_ignorado    {"ok": true, "created": false, "ignored": true, "roomVersion": 1, ...}
duplicado_sin_efecto    {"ok": true, "created": false, "duplicate": true, ...}
tareas_tras_duplicado   1
version_incorrecta      {"ok": false, "code": "PRECONDITION_FAILED", "version": 1}
completar_correcto      {"ok": true, "estado": "COMPLETADA", "version": 2, "roomVersion": 2, "estadoFisico": "LISTA", "habitacionVersion": 11}
duplicado_de_completado {"ok": true, "created": false, "duplicate": true, ...}
ciclo_viejo_no_reabre   {"ok": true, "created": false, "ignored": true, ...}
tareas_totales          1

=== step3: procedimientos operaciones ===
aplicar_mantenimiento   {"ok": true, "tipo": "MANTENIMIENTO", "estado": "APLICADA", "estadoFisico": "MANTENIMIENTO", "habitacionVersion": 21}
reaplicar_mantenimiento {"ok": true, "estado": "APLICADA", "alreadyApplied": true, ...}
habilitar_mantenimiento {"ok": true, "estado": "LIBERADA", "estadoFisico": "LISTA", "habitacionVersion": 22}
aplicar_liberada        {"ok": false, "code": "OPERATION_NOT_APPLICABLE", "estado": "LIBERADA"}
aplicar_baja            {"ok": true, "tipo": "BAJA", "estado": "APLICADA", "idHabitacion": 304, "habitacionVersion": 23}
habilitar_baja          {"ok": true, "estado": "LIBERADA", "estadoFisico": "LISTA", "habitacionVersion": 24}
reaplicar_baja          {"ok": false, "code": "OPERATION_NOT_APPLICABLE", "estado": "LIBERADA"}
aplicar_rechazada       {"ok": false, "code": "OPERATION_NOT_APPLICABLE", "estado": "RECHAZADA"}
habilitar_con_padre_inactivo {"ok": false, "code": "PARENT_INACTIVE"}
```

**Caso OCC02 (paso 17).** Dos requests concurrentes sobre la habitación 102 (gate `LIBRE`,
habitación `LISTA`), con claves de idempotencia distintas:

```
checkin HTTP: 409
{"success":false,"message":"La habitación no admite ingreso todavía (limpieza o mantenimiento).","error":{"code":"ROOM_NOT_READY","fields":{"idHabitacion":"102"}}}
mantenimiento HTTP: 200
{"success":true,"data":{"idHabitacion":102,"idEstadoHabitacion":3,...}}
room_gate:  BLOQUEADA | block_id=7af91d65-3b4a-40cb-80f0-1ddb63268e23
operacion_habitacion: 7af91d65-3b4a-40cb-80f0-1ddb63268e23 | MANTENIMIENTO | APLICADA
```

**Caso OCC03 (paso 14).** Gate `OCUPADA` en la habitación 102 → 409 durable; se libera la habitación
con el fixture y se reintenta el mismo `blockId` contra reception → 409; nueva intención (otro
`blockId`) → 200:

```
POST /api/habitacion/{id}/mantenimiento (If-Match 1) -> HTTP 409 GATE_NOT_AVAILABLE
operacion_habitacion: 8cc6f2b4-ebb1-42df-b76b-fe04394a7cf9 | MANTENIMIENTO | RECHAZADA
POST /internal/habitaciones/102/bloqueos (mismo blockId) -> HTTP 409 GATE_NOT_AVAILABLE
bloqueo_habitacion: 8cc6f2b4... | RECHAZADO | GATE_NOT_AVAILABLE
POST /api/habitacion/{id}/mantenimiento (otro blockId) -> HTTP 200, operación e9f70007... APLICADA
bloqueo_habitacion: e9f70007... | ACTIVO
```

**Caso CLN01 (paso 18).** Fixture F8: gate 101 `LIMPIEZA_PENDIENTE` (ciclo
`24dc23d8-1e3c-4426-bec6-103432086e0f`, `roomVersion=9`). Check-in → 409; comando publicado con el
hotel detenido (cola `limpieza.solicitada` con 1 mensaje y 0 consumidores) y consumido al arrancar;
completado correcto:

```
POST /api/recepcion/registrar (101) -> HTTP 409 ROOM_NOT_READY
limpieza_solicitada_procesada ... ciclo=24dc23d8-1e3c-4426-bec6-103432086e0f duplicado=false
PUT /api/habitacion/{id}/limpieza-completada (If-Match 1) -> HTTP 200, ETag "2"
limpieza: COMPLETADA | room_version=9 | version=2 | completed_at != null
habitacion: id_estado_habitacion=1 | version=3
outbox: HabitacionLista | aggregate_id=101 | cleaningCycleId=24dc23d8... | cleaningRoomVersion=9
room_gate: LIBRE | room_version=10
POST /api/recepcion/registrar (101) -> HTTP 201 (idRecepcion 8)
```

**Casos CLN02 y CLN03 (paso 18).** Reenvío del mismo `commandId` → duplicado sin segunda tarea;
`HabitacionLista` viejo de C1 publicado después del check-in nuevo → reception lo procesa pero el
gate sigue `OCUPADA`:

```
limpieza_solicitada_procesada ... duplicado=true
select count(*) from limpieza where cleaning_cycle_id='24dc23d8...'; -> 1
HabitacionLista event: cleaningCycleId=24dc23d8... | cleaningRoomVersion=9
inbox reception-gates-v1: message_id=e634908c-6f8e-4290-b609-2230d5fa0745
room_gate (tras el evento viejo): OCUPADA | id_recepcion=8
```

**Pasos 5–16 (rutas y outbox).** Salidas literales de los endpoints principales:

```
GET /api/habitacion/limpiezas?idHotel=1&estado=PENDIENTE -> HTTP 200,
  data=[{cleaningCycleId,idHabitacion:101,idRecepcion:1001,estado:PENDIENTE,roomVersion:2,version:1}],
  meta={page:0,size:20,totalElements:1}
GET /limpiezas?idHotel=2 con EMPLEADO de Lima -> HTTP 403 FORBIDDEN
PUT /api/habitacion/{id}/limpieza-completada sin If-Match -> HTTP 428
PUT .../limpieza-completada con If-Match 99 -> HTTP 412
PUT .../limpieza-completada correcto -> HTTP 200, ETag "2"
POST /api/habitacion/{id}/mantenimiento sin If-Match -> HTTP 428
POST .../mantenimiento con If-Match 99 -> HTTP 412
POST .../mantenimiento con motivo vacío/301 -> HTTP 400 VALIDATION_ERROR (fields.motivo)
POST /api/habitacion/{id}/mantenimiento If-Match 2 -> HTTP 200, blockId=2c03d31c...
  room_gate BLOQUEADA; operacion APLICADA; habitacion version 3
PUT /api/habitacion/{id}/mantenimiento-completado sin If-Match -> HTTP 428
PUT .../mantenimiento-completado con blockId ajeno -> HTTP 409 GATE_NOT_AVAILABLE
PUT .../mantenimiento-completado correcto -> HTTP 200; operacion LIBERADA; room_gate LIBRE
POST /api/habitacion/{id}/mantenimiento concurrente (dos claves, If-Match 10)
  -> A HTTP 200, B HTTP 409 ROOM_OPERATION_IN_PROGRESS; una sola operacion APLICADA
DELETE /api/habitacion/eliminar/{id} If-Match 2 -> HTTP 200 estado=false, blockId=4123638e...
POST /api/habitacion/registrar numero E2105 -> HTTP 201 (idHabitacion 307, número reutilizado)
PUT /api/habitacion/{id}/reactivar If-Match 3 -> HTTP 200 estado=true, version=4; repetir la clave -> 200
DELETE /api/habitacion/eliminar/{id} If-Match 4 -> HTTP 200 estado=false, blockId=1fbae0a1...
PUT /api/habitacion/{id}/reactivar con la categoría inactiva -> HTTP 409 GATE_NOT_AVAILABLE;
  habitacion sigue estado=false y la operación sigue APLICADA
PUT /api/habitacion/{id}/reactivar con padres activos -> HTTP 200
GET /api/habitacion/operaciones/4bcd... -> HTTP 200 {estado:APLICADA}
GET /api/habitacion/operaciones/2c03... -> HTTP 200 {estado:LIBERADA}
GET /api/habitacion/operaciones/8cc6... -> HTTP 200 {estado:RECHAZADA}
GET /api/habitacion/operaciones/40000000-0000-4000-8000-000000000001 (fixture) -> HTTP 200 {estado:PREPARANDO}
GET /api/habitacion/operaciones/{uuid inexistente} -> HTTP 404 RESOURCE_NOT_FOUND
PUT /api/habitacion/actualizar/{id} con operación APLICADA -> HTTP 409 ROOM_OPERATION_IN_PROGRESS
PUT /api/categoria/actualizar/{id} y /api/piso/actualizar/{id} con 306 en PREPARANDO -> HTTP 409
  (sin cambios en categoria/piso)
```

**Paso 13 (job RB3).** Con reception detenido, `POST /api/habitacion/{id}/mantenimiento` → 202 con
`Location` y `Retry-After: 2`; el job reintentó con el mismo `blockId` y aplicó el CAS; la alerta de
más de 10 minutos quedó visible:

```
HTTP/1.1 202
Location: /api/habitacion/operaciones/b0b404c1-e9be-43b3-898c-27623d8ecd8a
Retry-After: 2
operacion_reintento blockId=b0b404c1... attempts=3 error=No fallback available.
operacion_recuperada blockId=b0b404c1... idHabitacion=306 estado=APLICADA
operacion_habitacion: b0b404c1... | APLICADA | attempts=7
operacion_preparando_alerta pendientes=1 minutos=10
```

**Paso 16 (outbox contra JSON Schemas).** Validación con `jsonschema 4.26.0` y los schemas de
`docs/contracts/events/`:

```
HabitacionLista VALID aggregateId 101 aggregateVersion 3 payload.version 3
HabitacionHabilitada VALID aggregateId 102 aggregateVersion 3 payload.version 3
HabitacionSnapshot baja VALID aggregateId 305 version 3 estado False blockId 4123638e...
```

**Paso 19 (cierre).**

```
bash scripts/mvn.sh -f services/pom.xml verify
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0   (hotel-service)
[INFO] BUILD SUCCESS
python scripts/validar-planes.py
30 plan(es) revisado(s); 30 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).
python scripts/validar-contratos.py --base origin/main
Los contratos solo se han ampliado. Correcto.
docker compose -f infra/compose.yaml --env-file .env config -> EXIT=0
openspec validate back-hotel-operaciones --strict -> Change 'back-hotel-operaciones' is valid
```

La parte de consumidores de reporting (`gate_projection`, `limpieza_completada_projection`) no
existe todavía en este árbol; el plan la difiere expresamente a `qa-matriz-aceptacion` (sección 3).
La evidencia de hotel es el evento con `cleaningCycleId`/`cleaningRoomVersion` y el gate que no se
libera con un evento viejo.


## 7. Preguntas abiertas

Ninguna (resuelto por IA: migración `V5__hotel_operaciones.sql` y propiedades `hotel.operaciones.*`, ver `IA-back-hotel-operaciones-01.md`).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Mantenimiento, baja y reactivación con `blockId` y rechazos durables (200/202/409) probados (OCC02 y OCC03 en su parte de hotel).
- [x] Ciclo de limpieza completo: consumidor de `limpieza.solicitada`, listado y completado con V de la tarea, y `HabitacionLista`/`HabitacionHabilitada` emitidos por outbox contra los JSON Schemas (CLN01–CLN03 en su parte de hotel).
- [x] Una habitación solo se libera por el ciclo o bloqueo correlacionado; ninguna PREPARANDO queda sin recuperación y ninguna mutación de catálogo pasa con una operación vigente.
- [x] Evidencia de la sección 6 adjunta al PR de ejecución y `python scripts/validar-planes.py` sin errores.
- [x] El plan pasa a `cerrado` en el último PR del plan ([norma §6.9](README.md#6-contrato-con-la-ejecutora)).
- [x] Sin preguntas abiertas.
