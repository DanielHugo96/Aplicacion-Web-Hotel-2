# reception/operacion Specification

## Purpose
Contrato de comportamiento de la operación de recepción: ocupación por habitación (`room_gate`) sincronizada desde hotel, check-in inmediato con adelanto y medio de pago, edición de salida, lecturas por estadía con el campo derivado `vencida`, bloqueos administrativos durables y operaciones de consumo con lápidas. Los detalles de contrato viven en [`reception/api.md`](../../../../../../docs/especificacion/microservicios/reception/api.md) y [`reception/base-de-datos.md`](../../../../../../docs/especificacion/microservicios/reception/base-de-datos.md); esta capacidad describe el comportamiento exigible.

## Requirements

### Requirement: Identidad y autorización de recepción

reception SHALL validar el JWT en cada ruta privada (algoritmo RS256, issuer, audiencia y exp) y SHALL derivar de él el rol, el `sub` y las sedes; nunca SHALL autorizar con headers `X-User-Id`, `X-Role` ni `X-Hotel-Id`. Las rutas de personal (A global; E solo sus sedes) SHALL tratar los recursos de otra sede como inexistentes (404), y las rutas de cliente SHALL operar solo sobre la estadía con `idCliente` igual a `sub` (404 en la ajena). Las rutas `/internal/**` SHALL exigir token máquina con el scope indicado y SHALL rechazar tokens humanos.

#### Scenario: Ruta privada sin token

- **WHEN** se invoca cualquier ruta de recepción sin `Authorization`
- **THEN** responde 401 `UNAUTHENTICATED` sin datos ni enumeración

#### Scenario: Empleado fuera de su sede

- **WHEN** un token E de la sede 1 consulta una estadía de la sede 2
- **THEN** responde 404 `RESOURCE_NOT_FOUND`

#### Scenario: Cliente sobre estadía ajena

- **WHEN** un token C consulta `GET /api/recepcion/{id}` de otra persona
- **THEN** responde 404 y no revela existencia

#### Scenario: Ruta interna con token humano o sin scope

- **WHEN** se invoca una ruta `/internal/**` con token humano o con token máquina sin el scope requerido
- **THEN** responde 403 `FORBIDDEN`

### Requirement: Sincronización de gates desde hotel

reception SHALL consumir `hotel.habitacion.v1` con el grupo `reception-gates-v1`, aplicando cada evento una sola vez (inbox en la misma transacción del efecto) y resolviendo la liberación por estado y correlación de ciclo o bloqueo, no solo por versión. HabitacionCreada SHALL inicializar el gate ausente en LIBRE solo si la habitación es LISTA y activa, sin sobreescribir un gate existente; HabitacionSnapshot SHALL actualizar el seguimiento del catálogo sin liberar nada; HabitacionLista SHALL liberar a LIBRE solo un gate LIMPIEZA_PENDIENTE con `cleaningCycleId` coincidente; HabitacionHabilitada SHALL liberar solo un gate BLOQUEADA con `blockId` coincidente; toda liberación SHALL incrementar `roomVersion`. Un evento no procesable tras los reintentos SHALL terminar en `hotel.habitacion.v1.reception-gates-v1.dlt`.

#### Scenario: Alta de habitación LISTA

- **WHEN** llega HabitacionCreada de una habitación LISTA y activa
- **THEN** existe un gate LIBRE para esa habitación y un evento duplicado no crea ni altera otro

#### Scenario: Snapshot genérico con física LISTA

- **WHEN** llega HabitacionSnapshot con `estadoFisico=LISTA` sobre un gate LIMPIEZA_PENDIENTE o BLOQUEADA
- **THEN** el gate no se libera

#### Scenario: HabitacionLista con ciclo viejo

- **WHEN** llega HabitacionLista de un ciclo anterior al vigente del gate
- **THEN** el ciclo vigente no se libera

#### Scenario: HabitacionHabilitada coincidente

- **WHEN** llega HabitacionHabilitada con el `blockId` del gate BLOQUEADA
- **THEN** el gate pasa a LIBRE con `roomVersion` incrementado

### Requirement: Check-in inmediato

reception SHALL registrar el check-in en `POST /api/recepcion/registrar` (A/E, `Idempotency-Key`) fijando `fechaEntrada` en hoy America/Lima y rechazando con 400 los campos calculados o una `fechaEntrada` enviada. `fechaSalida` SHALL ser hoy o posterior, con máximo 365 noches y mínimo de cobro una noche; `adelanto` SHALL estar entre 0 y el alojamiento y `medioPago` (EFECTIVO/TARJETA/OTRO) SHALL ser obligatorio cuando `adelanto > 0`. Antes de la transacción SHALL validar por Feign cliente activo de tipo Cliente y habitación activa, de sede activa y física LISTA, guardando snapshots inmutables y la tarifa congelada. La transacción SHALL bloquear el gate, exigir LIBRE y sincronizado, insertar la estadía ACTIVA, ocupar el gate con `idRecepcion`, incrementar `roomVersion` y encolar los eventos por outbox; un gate OCUPADA SHALL responder 409 `ROOM_OCCUPIED`, uno LIMPIEZA_PENDIENTE o físico no LISTA 409 `ROOM_NOT_READY`, y un gate ausente 409 `ROOM_NOT_SYNCED` (pudiendo 503 transitorio). Una dependencia caída antes de crear la estadía SHALL responder 503 `DEPENDENCY_UNAVAILABLE` sin efectos, y el índice único parcial por habitación SHALL ser la segunda defensa: nunca se cierra otra estadía automáticamente.

#### Scenario: Check-in exitoso

- **WHEN** A/E registra un check-in válido sobre un gate LIBRE
- **THEN** responde 201 con la estadía ACTIVA, filas persistidas, gate OCUPADA y `roomVersion` incrementado

#### Scenario: Dos check-ins simultáneos (OCC01)

- **WHEN** dos `POST /api/recepcion/registrar` concurrentes apuntan a la misma habitación
- **THEN** uno responde 201 y el otro 409 `ROOM_OCCUPIED`, con una sola fila ACTIVA por habitación

#### Scenario: Gate en limpieza

- **WHEN** el check-in apunta a un gate LIMPIEZA_PENDIENTE
- **THEN** responde 409 `ROOM_NOT_READY` sin ocupar la habitación

#### Scenario: Reintento con la misma clave

- **WHEN** se repite el check-in con la misma `Idempotency-Key`
- **THEN** devuelve la misma estadía y no duplica filas ni eventos

### Requirement: Edición de salida

reception SHALL permitir `PUT /api/recepcion/actualizar/{id}` (A/E, `If-Match` e `Idempotency-Key`) solo sobre una estadía ACTIVA, con `fechaSalida` hoy o posterior y máximo 365 noches desde `fechaEntrada`, y `observacion` opcional. SHALL recalcular `precioInicial` con la tarifa congelada sin alterar `adelanto` ni pagos, e incrementar `version`; una estadía CERRANDO/CERRADA SHALL responder 409 `STAY_NOT_ACTIVE`, una nueva fecha que deje `precioInicial` por debajo del adelanto 409 `ADVANCE_EXCEEDS_NEW_TOTAL` sin cambios, y operaciones ABIERTAS 409 `OPERATIONS_IN_PROGRESS`. Cada cambio confirmado SHALL emitir `RecepcionActualizada` por outbox.

#### Scenario: Cambio de fecha exitoso

- **WHEN** A/E extiende la salida de una estadía ACTIVA con el ETag vigente
- **THEN** responde 200 con `version` incrementada y un `RecepcionActualizada` encolado

#### Scenario: Acortar bajo el adelanto

- **WHEN** la nueva `fechaSalida` dejaría `precioInicial` por debajo del adelanto
- **THEN** responde 409 `ADVANCE_EXCEEDS_NEW_TOTAL` sin cambios de importes ni versión

#### Scenario: Estadía no activa o sin precondición

- **WHEN** se edita una estadía CERRADA o falta `If-Match`, o la versión no coincide
- **THEN** responde 409 `STAY_NOT_ACTIVE` o 428 `PRECONDITION_REQUIRED` o 412 `PRECONDITION_FAILED` respectivamente

### Requirement: Lecturas por estadía con campo vencida

reception SHALL exponer `GET /api/recepcion/listar` (idHotel obligatorio, filtro de estado, paginación y orden por ID ascendente), `GET /api/recepcion/{id}`, `GET /api/recepcion/cliente/{idCliente}` (C solo su `sub`; E con idHotel) y `GET /api/recepcion/habitacion-activa/{idHabitacion}` (estadía ACTIVA/CERRANDO o `data=null`, validando sede aun sin estadía). Cada estadía SHALL incluir los snapshots, los importes y el campo derivado `vencida` (`true` solo si está ACTIVA y `fechaSalida` < hoy America/Lima), y SHALL mantener `estado` legacy (`true` para ACTIVA/CERRANDO, `false` para CERRADA). Una estadía vencida SHALL seguir ocupando la habitación: nada se libera ni cierra automáticamente.

#### Scenario: Estadía vencida

- **WHEN** se lee una estadía ACTIVA cuya `fechaSalida` es anterior a hoy en America/Lima
- **THEN** `vencida=true` y el gate permanece OCUPADA

#### Scenario: Estadía futura o cerrada

- **WHEN** se leen una estadía ACTIVA con salida de hoy o posterior y una estadía CERRADA
- **THEN** `vencida=false` en ambas

#### Scenario: Habitación sin estadía

- **WHEN** se consulta `habitacion-activa` de una habitación libre
- **THEN** responde 200 con `data=null`

### Requirement: Bloqueos administrativos durables por blockId

reception SHALL exponer `POST /internal/habitaciones/{id}/bloqueos` (token máquina, scope `bloqueos:write`, `Idempotency-Key` estable derivada de `blockId`) con `{blockId,idHotel,motivo}`. Con el gate LIBRE SHALL persistir el bloqueo ACTIVO y dejar el gate BLOQUEADA (200); si el gate está OCUPADA o LIMPIEZA_PENDIENTE SHALL persistir RECHAZADO y responder 409 `GATE_NOT_AVAILABLE`. La decisión SHALL quedar fijada por `blockId` con hash de request: el mismo `blockId` conserva su decisión aunque el gate se libere después, un payload distinto con el mismo `blockId` SHALL rechazarse, y el bloqueo solo SHALL liberarse con la HabitacionHabilitada de ese `blockId` (gate LIBRE y `roomVersion++`).

#### Scenario: Bloqueo sobre gate libre

- **WHEN** hotel llama con un `blockId` nuevo sobre un gate LIBRE
- **THEN** responde 200 con estado BLOQUEADA y la fila del bloqueo ACTIVO

#### Scenario: Rechazo durable (OCC03)

- **WHEN** se rechaza un bloqueo por gate OCUPADA, se libera luego el gate y se reintenta el mismo `blockId`
- **THEN** el mismo `blockId` sigue respondiendo 409 `GATE_NOT_AVAILABLE` y un `blockId` nuevo responde 200

### Requirement: Admisión de operaciones de consumo

reception SHALL exponer `POST /internal/recepciones/{id}/operaciones` (token máquina, scope `operaciones:write`, `Idempotency-Key` estable derivada de `operationId`) con `{operationId,idVenta,idHotel,tipo,actorId,actorRol}`, y `GET /internal/recepciones/{id}` (scope `recepciones:read`) con el resumen mínimo sin documento ni correo. La admisión SHALL validar la tupla e `idHotel`; con `actorRol=CLIENTE` SHALL exigir `idCliente=actorId` y `tipo=VENTA`; SHALL exigir estadía ACTIVA (409 `STAY_NOT_ACTIVE` si CERRANDO/CERRADA) y SHALL bloquear recepción antes que operación, dejando la operación ABIERTA única por `operationId` y venta. Un rechazo tardío SHALL ser resultado durable y nunca SHALL revivir una lápida FINALIZADA.

#### Scenario: Admisión válida

- **WHEN** sales admite un `operationId` nuevo sobre una estadía ACTIVA
- **THEN** responde 200 con estado ABIERTA y la fila persistida

#### Scenario: Admisión sobre estadía cerrada

- **WHEN** la estadía está CERRANDO o CERRADA
- **THEN** responde 409 `STAY_NOT_ACTIVE` con decisión durable

#### Scenario: Cliente operando una estadía ajena

- **WHEN** `actorRol=CLIENTE` pero `idCliente` no coincide con `actorId`, o el tipo no es VENTA
- **THEN** responde 403 `FORBIDDEN` sin crear la operación

### Requirement: Finalización de operaciones de consumo con lápidas

reception SHALL consumir la cola `recepcion.operacion-finalizar` con inbox en la misma transacción del efecto, aceptando las combinaciones VENTA → CONFIRMADA/RECHAZADA/CANCELADA, PAGO → PAGO_REGISTRADO/RECHAZADA y ANULACION → CANCELADA/RECHAZADA. SHALL verificar tupla de IDs e `idHotel` del envelope antes de liberar la operación, SHALL crear la lápida FINALIZADA completa (con `tipo`) incluso si el comando llega antes de admitir, SHALL rechazar un resultado contradictorio sin desbloquear otra venta y nunca SHALL revivir una operación FINALIZADA; el listener SHALL NOT consultar stock. Los mensajes no procesables SHALL agotar los reintentos del contrato y terminar en `recepcion.operacion-finalizar.dlq`.

#### Scenario: Comando antes de admitir (STK06)

- **WHEN** llega `RecepcionOperacionFinalizar` con resultado RECHAZADA antes de la respuesta de admisión y luego llega la admisión tardía
- **THEN** queda la lápida FINALIZADA con tupla completa y la admisión tardía responde 409

#### Scenario: Comando duplicado

- **WHEN** se reentrega el mismo comando
- **THEN** no cambia el resultado ni duplica efectos (inbox)

#### Scenario: Combinación contradictoria

- **WHEN** el `tipo` y el `resultado` no son compatibles o `idHotel` no corresponde
- **THEN** el mensaje termina en `recepcion.operacion-finalizar.dlq` sin liberar otra venta

### Requirement: Mensajería de salida por outbox

reception SHALL publicar por outbox, en la misma transacción del cambio de estadía, `CheckInRegistrado` y `RecepcionActualizada` en `reception.recepcion.v1` con key `idHabitacion` y `aggregateVersion=roomVersion`, con el payload snapshot del contrato (sin documento, correo ni observaciones personales: `observacion=null`), y SHALL encolar el comando `notificacion.checkin` (envelope RabbitMQ, `templateVersion=1`) tras confirmar el check-in. La caída de broker o SMTP SHALL NOT revertir el check-in: los pendientes quedan en el outbox y la entrega es al-menos-una-vez con efectos idempotentes (inbox y `commandId`).

#### Scenario: Evento tras el check-in

- **WHEN** se confirma un check-in
- **THEN** existe una fila de outbox del `CheckInRegistrado` que valida contra el JSON Schema y se publica con key y versión correctas

#### Scenario: Reintento sin duplicar efectos

- **WHEN** se reenvía el mismo evento o comando
- **THEN** los consumidores no duplican efectos (inbox) y el comando conserva su `commandId`

### Requirement: Envelope, idempotencia y errores

reception SHALL responder con el envelope `success/message/data/meta` (con `correlationId`) y listas con meta de paginación, SHALL exigir `Idempotency-Key` en POST/PUT de negocio (misma clave y cuerpo devuelven el mismo resultado; cuerpo distinto 409 `IDEMPOTENCY_CONFLICT`; retención mínima 30 días) y SHALL usar únicamente los códigos y mensajes de [`errors.md`](../../../../../../docs/contracts/errors.md), sin stacktraces ni SQL. Un rechazo de negocio posterior a una operación durable SHALL persistirse y hacerse commit antes de responder 409.

#### Scenario: Reintento con la misma clave

- **WHEN** se repite un POST/PUT de negocio con la misma `Idempotency-Key` y el mismo cuerpo
- **THEN** devuelve el mismo status y cuerpo sin repetir efectos

#### Scenario: Misma clave con otro cuerpo

- **WHEN** se reutiliza la clave con un cuerpo distinto
- **THEN** responde 409 `IDEMPOTENCY_CONFLICT`

### Requirement: Salud, dependencias y degradación

reception SHALL exponer `GET /actuator/health/liveness` (200 con el proceso vivo, sin consultar dependencias) y `GET /actuator/health/readiness` (200 solo con PostgreSQL arriba). Kafka, RabbitMQ e identity SHALL reflejarse como degradación visible sin bloquear la operación en curso, pero ninguna operación de negocio que necesite una dependencia caída SHALL habilitarse por defecto; antes de crear una operación durable SHALL responder 503 `DEPENDENCY_UNAVAILABLE`.

#### Scenario: Readiness con base arriba

- **WHEN** PostgreSQL responde y Kafka o RabbitMQ están caídos
- **THEN** readiness responde 200 y la degradación es visible al operador

#### Scenario: Feign caído antes de la transacción

- **WHEN** identity o hotel no responden durante la validación del check-in
- **THEN** responde 503 `DEPENDENCY_UNAVAILABLE` sin crear estadía
