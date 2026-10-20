# Spec Delta

## Purpose

Gobierna el ciclo de vida operativo de una habitación en hotel: mantenimiento, baja y reactivación coordinados con el gate de reception por `blockId`, la consulta de operaciones administrativas y el ciclo de limpieza con los eventos correlacionados que liberan la habitación.

## ADDED Requirements

### Requirement: Mantenimiento y baja con bloqueo durable

hotel SHALL ejecutar el mantenimiento (`POST /api/habitacion/{id}/mantenimiento`) y la baja (`DELETE /api/habitacion/eliminar/{id}`) persistiendo una operación con `blockId`, motivo y actor, y solicitando a reception el bloqueo durable antes de aplicar el cambio local. La respuesta MUST distinguir 200, 202 y 409. La operación MUST aplicar solo por CAS PREPARANDO → APLICADA y una operación LIBERADA o RECHAZADA no MUST reaplicarse.

#### Scenario: Mantenimiento aplicado en el request

- **WHEN** A llama con If-Match e Idempotency-Key válidos sobre una habitación libre y reception admite el bloqueo
- **THEN** responde 200 con la Habitacion en físico MANTENIMIENTO y el `blockId`, y la operación queda APLICADA en la misma transacción local

#### Scenario: Admisión no confirmada

- **WHEN** la llamada a reception no confirma la admisión (timeout, 503 o `ROOM_NOT_SYNCED`)
- **THEN** responde 202 con `blockId`, `Location` y `Retry-After`, sin afirmar que la habitación está bloqueada, y un job reintenta la misma operación con el mismo `blockId`

#### Scenario: Rechazo durable del gate

- **WHEN** reception responde rechazo porque el gate está OCUPADA o con limpieza pendiente
- **THEN** responde 409 con el código de rechazo y la operación queda RECHAZADA por ese `blockId`; reintentar la misma llave sigue devolviendo el rechazo aunque la habitación quede libre y una nueva intención exige otro `blockId`

#### Scenario: Operación vigente

- **WHEN** la habitación ya tiene una operación PREPARANDO o APLICADA sin liberar
- **THEN** la nueva petición responde 409 `ROOM_OPERATION_IN_PROGRESS` y no crea una segunda operación vigente

### Requirement: Reactivación de habitación

hotel SHALL reactivar (`PUT /api/habitacion/{id}/reactivar`) solo la habitación con una operación de BAJA APLICADA y sede, piso y categoría activos, dejándola activa y en físico LISTA con version incrementada, y liberando el bloqueo de forma correlacionada. Sin la operación aplicada o con un padre inactivo MUST responder 409 sin habilitar la habitación.

#### Scenario: Reactivación exitosa

- **WHEN** A llama con If-Match, Idempotency-Key y el `blockId` de la baja aplicada, y los padres están activos
- **THEN** responde 200 con la habitación activa y LISTA, version + 1 y la habilitación correlacionada encolada

#### Scenario: Padre inactivo

- **WHEN** la sede, el piso o la categoría están inactivos
- **THEN** responde 409 sin dejar la habitación activa y sin encolar la habilitación

### Requirement: Consulta de operaciones administrativas

hotel SHALL exponer `GET /api/habitacion/operaciones/{blockId}` para que A consulte la operación creada por mantenimiento o baja, con su tipo y su estado PREPARANDO, APLICADA, LIBERADA o RECHAZADA; debe responder 404 cuando el `blockId` no existe. Las rutas estáticas `limpiezas` y `operaciones` MUST tener prioridad sobre la ruta genérica de IDs.

#### Scenario: Polling de una operación

- **WHEN** A consulta el `blockId` de una operación existente
- **THEN** responde 200 con `{blockId, idHabitacion, tipo, estado}`

#### Scenario: Operación inexistente

- **WHEN** A consulta un `blockId` que no existe
- **THEN** responde 404 `RESOURCE_NOT_FOUND`

### Requirement: Ciclo de limpieza desde limpieza.solicitada

hotel SHALL consumir el comando RabbitMQ `limpieza.solicitada` validando la tupla `{idHabitacion, idRecepcion, cleaningCycleId, roomVersion}` contra el schema y crear la tarea de limpieza en estado PENDIENTE dejando la habitación en físico EN_LIMPIEZA, comparando el `roomVersion` con el último aplicado. Un duplicado MUST devolver éxito sin reabrir una tarea completada, un ciclo viejo MUST NOT reemplazar al vigente, y un ciclo con otra habitación o recepción MUST terminar en la DLQ sin efecto.

#### Scenario: Solicitud válida

- **WHEN** llega el comando con la tupla válida y la habitación no tiene ciclo pendiente
- **THEN** se registra la tarea PENDIENTE, la habitación queda EN_LIMPIEZA y el mensaje se confirma solo después del commit local

#### Scenario: Duplicado o ciclo viejo

- **WHEN** llega el mismo `cleaningCycleId` ya procesado o un ciclo con `roomVersion` anterior al vigente
- **THEN** responde éxito sin crear otra tarea, sin reabrir una completada y sin reemplazar el ciclo actual

#### Scenario: Contradicción permanente

- **WHEN** el mismo ciclo llega con otra habitación u otra recepción, o el mensaje no valida contra el schema
- **THEN** el mensaje va a `limpieza.solicitada.dlq` sin efecto sobre la limpieza ni sobre la habitación

### Requirement: Listado de limpiezas por sede

hotel SHALL exponer `GET /api/habitacion/limpiezas` para A/E con `idHotel` obligatorio, filtro opcional `estado=PENDIENTE/COMPLETADA`, paginación y orden estable; E MUST recibir solo limpiezas de sus sedes asignadas. Cada registro MUST incluir `cleaningCycleId`, `idHabitacion`, `idRecepcion`, `estado`, `roomVersion` y `version`, siendo `version` el If-Match para completar la tarea.

#### Scenario: Listado con filtro

- **WHEN** A o E lista con `idHotel` y un `estado` concreto
- **THEN** responde 200 con solo las limpiezas de ese estado y la meta de paginación

#### Scenario: Aislamiento por sede

- **WHEN** E pide una sede que no tiene asignada
- **THEN** la respuesta no expone limpiezas de esa sede

### Requirement: Completado de limpieza

hotel SHALL completar la limpieza con `PUT /api/habitacion/{id}/limpieza-completada` (A/E) usando como If-Match la version de la tarea y validando ciclo, habitación y sede. La transición PENDIENTE → COMPLETADA MUST registrar `completed_at`, dejar el físico LISTA e incrementar las versiones, devolviendo `{limpieza, habitacion}` con el ETag de la tarea. La misma Idempotency-Key MUST devolver el mismo resultado sin repetir efectos.

#### Scenario: Limpieza completada

- **WHEN** A/E llama con el `cleaningCycleId` correcto, If-Match de la tarea e Idempotency-Key
- **THEN** responde 200 con la tarea COMPLETADA y la habitación LISTA, y el ETag corresponde a la tarea terminada

#### Scenario: Versión incorrecta

- **WHEN** falta If-Match o la version enviada no es la vigente de la tarea
- **THEN** responde 428 `PRECONDITION_REQUIRED` o 412 `PRECONDITION_FAILED`, respectivamente, sin completar la tarea

### Requirement: Eventos de habilitación correlacionados

hotel SHALL emitir por outbox, en la transacción del cambio, `HabitacionLista` al completar una limpieza con el ciclo y `cleaningRoomVersion` de la tarea, `HabitacionHabilitada` al liberar un bloqueo con su `blockId`, y `HabitacionSnapshot` con estado=false al aplicar una baja. La key MUST ser `idHabitacion` y el payload MUST validar contra los JSON Schemas. Un evento posterior no MUST liberar un ciclo o bloqueo distinto.

#### Scenario: Evento de limpieza

- **WHEN** se completa una limpieza
- **THEN** queda una fila de outbox con `HabitacionLista` que incluye el ciclo y el `cleaningRoomVersion` de la tarea, válida contra su schema

#### Scenario: Evento de habilitación

- **WHEN** se completa un mantenimiento o se reactiva una habitación
- **THEN** queda una fila de outbox con `HabitacionHabilitada` que incluye el `blockId` liberado, válida contra su schema

#### Scenario: Snapshot de baja

- **WHEN** se aplica la baja de una habitación
- **THEN** el evento `HabitacionSnapshot` conserva el snapshot completo con estado=false y su version

### Requirement: Recuperación de operaciones PREPARANDO

hotel SHALL recuperar las operaciones que queden PREPARANDO con un job que reintente con el mismo `blockId`: repetir el POST idempotente si el bloqueo no existe en reception, aplicar el CAS PREPARANDO → APLICADA si el bloqueo está ACTIVO, y marcar RECHAZADA si el bloqueo fue rechazado. El job MUST NOT liberar por timeout ni modificar el gate de reception por SQL, y sus intentos e instantes MUST quedar visibles para la alerta de PREPARANDO prolongado.

#### Scenario: Bloqueo admitido y cambio pendiente

- **WHEN** el job encuentra un bloqueo ACTIVO con la operación aún PREPARANDO
- **THEN** aplica el cambio local por CAS y deja la operación APLICADA

#### Scenario: Bloqueo rechazado

- **WHEN** el job encuentra el bloqueo rechazado en reception
- **THEN** deja la operación hotel RECHAZADA y no la reintenta

### Requirement: Idempotencia, versión y operación vigente

hotel SHALL aplicar Idempotency-Key y If-Match a las mutaciones: la misma clave y cuerpo devuelven la respuesta persistida, un cuerpo distinto con la misma clave da 409 `IDEMPOTENCY_CONFLICT` y una version distinta da 412. Un rechazo posterior a la operación durable MUST persistirse con commit antes del 409. Con una operación PREPARANDO o APLICADA, las demás mutaciones físicas y de catálogo de la habitación MUST dar 409 `ROOM_OPERATION_IN_PROGRESS`; los cambios de categoría o piso que la afecten MUST rechazarse sin aplicar.

#### Scenario: Reintento válido

- **WHEN** el cliente repite la misma llamada con la misma Idempotency-Key tras un 202 o un 200
- **THEN** recibe la misma respuesta y no se repiten efectos ni se falla por la version que el primer intento ya incrementó

#### Scenario: Mutación con operación vigente

- **WHEN** se intenta editar el catálogo o el físico de una habitación con operación PREPARANDO o APLICADA
- **THEN** responde 409 `ROOM_OPERATION_IN_PROGRESS` sin aplicar la mutación

### Requirement: Seguridad de las rutas de operación y limpieza

hotel SHALL validar JWT RS256 (algoritmo, issuer, audiencia y exp) y autorizar por rol y sede: A global, E solo sus sedes asignadas y C fuera de estas rutas; la identidad MUST NOT tomarse de cabeceras `X-User-Id`, `X-Role` ni `X-Hotel-Id`. La llamada interna a reception MUST usar token máquina con scope `bloqueos:write` y no expone las rutas internas por el Gateway.

#### Scenario: Sin token

- **WHEN** llega una petición sin Bearer válido a cualquier ruta del ciclo
- **THEN** responde 401 `UNAUTHENTICATED` sin efectos

#### Scenario: Alcance de empleado

- **WHEN** un E opera sobre una habitación o limpieza de una sede no asignada
- **THEN** la respuesta no expone ni modifica recursos de esa sede
