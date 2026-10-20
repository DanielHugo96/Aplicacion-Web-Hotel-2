# Spec Delta

## Purpose

Contrato de comportamiento del cierre de estadía en reception: cotización informativa, cierre `VALIDANDO` → `CONFIRMANDO` → `COMPLETADO` con cálculo congelado, penalidad y `montoRecibido`, recuperación cada 30 s, anulación correctiva de check-in, archivado y salidas por outbox (`SalidaRegistrada` y `RecepcionActualizada` de anulación, `limpieza.solicitada` y `notificacion.checkout`). Los detalles de contrato viven en [`reception/api.md`](../../../../../../docs/especificacion/microservicios/reception/api.md) y [`reception/base-de-datos.md`](../../../../../../docs/especificacion/microservicios/reception/base-de-datos.md); esta capacidad describe el comportamiento exigible.

## ADDED Requirements

### Requirement: Identidad y autorización del cierre

reception SHALL validar el JWT en cada ruta privada (algoritmo RS256, issuer, audiencia y exp) y SHALL derivar de él el rol y las sedes; nunca SHALL autorizar con headers `X-User-Id`, `X-Role` ni `X-Hotel-Id`. `GET /api/recepcion/{id}/cotizacion-salida` y `POST /api/recepcion/registrar-salida` SHALL exigir A/E; `POST /api/recepcion/{id}/anular-checkin` y `DELETE /api/recepcion/eliminar/{id}` SHALL exigir A. Los recursos de otra sede SHALL tratarse como inexistentes (404) y un cliente SHALL recibir 403 en estas rutas.

#### Scenario: Ruta de cierre sin token

- **WHEN** se invoca cualquier ruta de este plan sin `Authorization`
- **THEN** responde 401 `UNAUTHENTICATED` sin datos ni enumeración

#### Scenario: Cliente intentando cerrar

- **WHEN** un token C invoca `POST /api/recepcion/registrar-salida`
- **THEN** responde 403 `FORBIDDEN` sin crear cierre

#### Scenario: Empleado fuera de su sede

- **WHEN** un token E de la sede 1 cotiza o cierra una estadía de la sede 2
- **THEN** responde 404 `RESOURCE_NOT_FOUND`

### Requirement: Cotización informativa de salida

reception SHALL exponer `GET /api/recepcion/{id}/cotizacion-salida` (A/E) con query `costoPenalidad` decimal mayor o igual que 0 y valor 0 por defecto. La cotización SHALL calcularse con redondeo HALF_UP a 2 decimales por componente, SHALL incluir `alojamiento`, `adelanto`, `penalidad`, `saldoAlojamiento`, `consumosPagados`, `consumosPendientes`, `cobroAhora`, `moneda` PEN y `calculadoEn`, y SHALL NOT crear cierres ni mutar la estadía. Una penalidad mayor que `precioInicial` SHALL responder 400 `VALIDATION_ERROR`.

#### Scenario: Cotización del ejemplo

- **WHEN** A/E cotiza la estadía demo (alojamiento 70, adelanto 20, pendientes 7) sin penalidad
- **THEN** responde 200 con `saldoAlojamiento=50` y `cobroAhora=57`, sin filas nuevas en `cierre`

#### Scenario: Penalidad sobre el precio

- **WHEN** se cotiza con `costoPenalidad=71` sobre `precioInicial=70`
- **THEN** responde 400 `VALIDATION_ERROR` sin crear cierre

### Requirement: Inicio del cierre con alma de idempotencia

reception SHALL exponer `POST /api/recepcion/registrar-salida` (A/E, `Idempotency-Key`) con `Salida` (`idRecepcion`, `idHabitacion`, `costoPenalidad`, `montoRecibido`, `medioPago`) y `motivoPenalidad` (1..300) obligatorio cuando `costoPenalidad > 0`; `costoPenalidad` SHALL cumplir `0 <= costoPenalidad <= precioInicial` y el `idHabitacion` SHALL validarse contra la recepción. `closureId` SHALL fijarse a la `Idempotency-Key`. En transacción con lock de la recepción: si hay operaciones `ABIERTAS` SHALL responder 409 `OPERATIONS_IN_PROGRESS` sin cambiar la estadía; si hay un cierre vigente con otra clave SHALL responder 409 `CLOSURE_IN_PROGRESS`; la misma clave SHALL reanudar el cierre vigente; en otro caso SHALL pasar `ACTIVA` → `CERRANDO` e insertar el cierre `VALIDANDO` con la solicitud inmutable (`sp_cierre_iniciar`).

#### Scenario: Cierre con venta en vuelo (OUT01)

- **WHEN** se inicia el cierre de una estadía con una venta admitida no terminal
- **THEN** responde 409 `OPERATIONS_IN_PROGRESS` y ninguna venta se admite después del intento

#### Scenario: Doble cierre concurrente

- **WHEN** dos claves distintas intentan cerrar la misma estadía `CERRANDO`
- **THEN** la segunda responde 409 `CLOSURE_IN_PROGRESS` y la primera reanuda con su propia clave

### Requirement: Cálculo congelado y rechazo exacto

reception SHALL obtener por Feign `GET /internal/cierres/{idRecepcion}/resumen` (scope `cierres:write`) los consumos estables, SHALL calcular `alojamientoTotal=precioInicial+penalidad`, `saldoAlojamiento=alojamientoTotal-adelanto`, `consumosTotal=consumosPagadosAntes+consumosPendientes`, `cobroAhora=saldoAlojamiento+consumosPendientes`, `totalGeneral=alojamientoTotal+consumosTotal` y `expectedPendingTotal=consumosPendientes` con HALF_UP, y SHALL comparar `montoRecibido` de forma exacta contra `cobroAhora`. Si discrepa, en transacción SHALL pasar `VALIDANDO` → `RECHAZADO` y `CERRANDO` → `ACTIVA` (`sp_cierre_rechazar_monto`), SHALL conservar el registro y SHALL responder 409 `AMOUNT_MISMATCH` con la nueva cotización y el `closureId`; un nuevo intento SHALL exigir una clave nueva y ningún pago remoto SHALL haberse invocado.

#### Scenario: Monto con un centavo de diferencia (OUT03)

- **WHEN** se cierra la estadía demo (cobro 57) con `montoRecibido=56.99`
- **THEN** responde 409 `AMOUNT_MISMATCH` con cotización, el cierre queda `RECHAZADO` y la estadía vuelve a `ACTIVA`

#### Scenario: Cierre sin ventas (OUT04)

- **WHEN** se cierra una estadía sin ventas con `montoRecibido=50.00`
- **THEN** el recibo contiene `totalConsumos=0.00`, `cobroAhora=50.00` y la salida es normal

### Requirement: Confirmación idempotente y salida

Si el monto coincide, reception SHALL persistir el cálculo completo y SHALL hacer CAS `VALIDANDO` → `CONFIRMANDO` con commit antes de invocar a sales (`sp_cierre_preparar_confirmacion`); este punto SHALL impedir recalcular un cobro ya posiblemente aplicado. SHALL invocar `POST /internal/cierres/confirmar` siempre con el payload idéntico (`closureId`, `idRecepcion`, `idHotel`, `expectedPendingTotal`, `medioPago`), SHALL validar el recibo contra el cierre, el `idHotel` y el importe congelado, y en una transacción SHALL pasar `CONFIRMANDO` → `COMPLETADO` y `CERRANDO` → `CERRADA`, SHALL guardar fecha y desglose, SHALL dejar `room_gate` en `LIMPIEZA_PENDIENTE` con `cleaningCycleId` e incrementar `roomVersion`, y SHALL producir por outbox `SalidaRegistrada`, `limpieza.solicitada` y `notificacion.checkout` (`sp_cierre_completar`), respondiendo 200. Un timeout después de aceptar el cierre SHALL responder 202 con `Location` (GET de la recepción), `Retry-After` y fase, sin indicar que se reinicie el cobro.

#### Scenario: Cierre recuperado tras caída (OUT02)

- **WHEN** sales confirmó pero reception cayó antes de completar y se reintenta con el mismo `closureId`
- **THEN** queda un solo recibo persistido, una sola salida y las ventas en `PAGADO` una sola vez

#### Scenario: Timeout tras aceptar

- **WHEN** el cierre queda aceptado pero la confirmación remota no responde a tiempo
- **THEN** responde 202 con `Location`, `Retry-After` y fase `CONFIRMANDO`, y el reintento con la misma clave no duplica el cobro

### Requirement: Recuperación cada 30 s por fase

reception SHALL ejecutar cada 30 s un recuperador sobre `cierre` por (`estado`, `next_attempt_at`) con CAS bajo lock: `VALIDANDO` SHALL repetir lectura del resumen y cálculo; `CONFIRMANDO` SHALL repetir únicamente la confirmación con el payload congelado; `COMPLETADO` SHALL devolver el resultado guardado; `RECHAZADO` SHALL devolver su 409 original. Nunca SHALL borrar un cierre para recuperarlo ni SHALL recalcular en `CONFIRMANDO`. Los errores permanentes en `CONFIRMANDO` SHALL conservar la estadía en `CERRANDO` con `lastErrorCode` para revisión y SHALL NOT revertir a `ACTIVA` ni abrir otra cuenta.

#### Scenario: Caída en cada fase (OUT05)

- **WHEN** se mata el proceso en `VALIDANDO`, en `CONFIRMANDO` y después del recibo, y se reinicia
- **THEN** `VALIDANDO` recalcula, `CONFIRMANDO` reenvía el payload congelado, hay un solo cobro y una sola salida, y `COMPLETADO` no recalcula

### Requirement: Anulación correctiva de check-in

reception SHALL exponer `POST /api/recepcion/{id}/anular-checkin` (A, `If-Match` e `Idempotency-Key`) con `motivo` de 1..300, admisible solo sobre estadía `ACTIVA` sin ventas admitidas ni cierre en curso. SHALL hacer CAS `ACTIVA` → `CERRADA` con `archivada=true`, SHALL liberar `room_gate` a `LIBRE` con `roomVersion++` sin limpieza ni notificación de checkout, SHALL publicar `RecepcionActualizada` y SHALL auditar en `audit_log` (`sp_recepcion_anular_checkin`), sin recibo ni cobro. Con operaciones `ABIERTAS` SHALL responder 409 `OPERATIONS_IN_PROGRESS`; con cierre vigente 409 `CLOSURE_IN_PROGRESS`; fuera de `ACTIVA` 409 `STAY_NOT_ACTIVE`; sin `If-Match` 428 `PRECONDITION_REQUIRED` y con versión distinta 412 `PRECONDITION_FAILED`.

#### Scenario: Anulación limpia

- **WHEN** A anula un check-in `ACTIVA` sin ventas ni cierre, con ETag vigente
- **THEN** responde 200 con estadía `CERRADA` archivada, gate `LIBRE` y `RecepcionActualizada` con ocupación `LIBRE`

#### Scenario: Anulación con consumos en proceso

- **WHEN** la estadía tiene operaciones `ABIERTAS` o un cierre vigente
- **THEN** responde 409 `OPERATIONS_IN_PROGRESS` o 409 `CLOSURE_IN_PROGRESS` sin cambiar nada

### Requirement: Archivado de cerradas

reception SHALL exponer `DELETE /api/recepcion/eliminar/{id}` (A, `If-Match` e `Idempotency-Key`) solo sobre estadía `CERRADA` (`sp_recepcion_archivar`): SHALL marcar `archivada=true` y SHALL conservar importes y snapshots. Fuera de `CERRADA` SHALL responder 409 `STAY_NOT_ACTIVE`. El `DELETE` repetido con la misma clave SHALL devolver el resultado original aunque el recurso cambie de estado.

#### Scenario: Archivar una cerrada

- **WHEN** A archiva una estadía `CERRADA` con ETag vigente
- **THEN** responde 200 con `archivada=true` e importes intactos

#### Scenario: Archivar una activa

- **WHEN** A intenta archivar una estadía `ACTIVA`
- **THEN** responde 409 `STAY_NOT_ACTIVE` sin archivar nada

### Requirement: Regresión de edición bajo adelanto

reception SHALL mantener el rechazo de `PUT /api/recepcion/actualizar/{id}` (implementado por `back-reception-operacion`) cuando la nueva `fechaSalida` dejaría `precioInicial` por debajo del adelanto: SHALL responder 409 `ADVANCE_EXCEEDS_NEW_TOTAL` sin cambios de importes ni versión.

#### Scenario: Acortar bajo el adelanto (OUT06)

- **WHEN** se edita la salida dejando `precioInicial` por debajo del adelanto
- **THEN** responde 409 `ADVANCE_EXCEEDS_NEW_TOTAL` con precio y adelanto intactos

### Requirement: Mensajería de salida por outbox

reception SHALL publicar por outbox, en la misma transacción del cambio de estadía, `SalidaRegistrada` en `reception.recepcion.v1` con key `idHabitacion` y `aggregateVersion=roomVersion` (payload snapshot con `cierre` final: `alojamientoTotal`, `consumosPagadosAntes`, `consumosPagadosAlCerrar`, `consumosTotal`, `cobroAhora`, `totalGeneral`, `medioPago`, `closedAt`; `observacion=null`), SHALL encolar `limpieza.solicitada` (`idHabitacion`, `idRecepcion`, `cleaningCycleId`, `roomVersion`) y SHALL encolar `notificacion.checkout` (`idPersona`, `idRecepcion`, `destinatario`, `nombre`, `hotel`, `totalAlojamiento`, `totalConsumos`, `templateVersion=1`). La anulación de check-in SHALL publicar `RecepcionActualizada` con `estadoEstadia=CERRADA`, `archivada=true` y ocupación `LIBRE`, sin `limpieza.solicitada` ni `notificacion.checkout`. La caída de broker o SMTP SHALL NOT revertir el cierre.

#### Scenario: Evento tras el cierre

- **WHEN** se confirma un cierre
- **THEN** existe una fila de outbox por destino que valida contra sus JSON Schemas y se publica con key y versión correctas

#### Scenario: Reintento sin duplicar efectos

- **WHEN** se reenvía `SalidaRegistrada` o los comandos
- **THEN** los consumidores no duplican efectos (inbox y `commandId`/`cleaningCycleId`)

### Requirement: Envelope, idempotencia y errores

reception SHALL responder con el envelope `success/message/data/meta` (con `correlationId`), SHALL exigir `Idempotency-Key` en el cierre, la anulación y el archivado (misma clave y cuerpo devuelven el mismo resultado; cuerpo distinto 409 `IDEMPOTENCY_CONFLICT`; retención mínima 30 días), SHALL exigir `If-Match` en la anulación y el archivado (ausente 428 `PRECONDITION_REQUIRED`; distinto 412 `PRECONDITION_FAILED`) con orden autenticar → idempotencia → `If-Match`, y SHALL usar únicamente los códigos y mensajes de [`errors.md`](../../../../../../docs/contracts/errors.md), sin stacktraces ni SQL. Antes de crear una operación durable con dependencias caídas SHALL responder 503 `DEPENDENCY_UNAVAILABLE`; una vez aceptado el cierre, un fallo recuperable SHALL responder 202 con fase y `Location`.

#### Scenario: Misma clave con otro cuerpo

- **WHEN** se reutiliza la clave de cierre con un cuerpo distinto
- **THEN** responde 409 `IDEMPOTENCY_CONFLICT`

### Requirement: Salud, dependencias y degradación

reception SHALL exponer `GET /actuator/health/liveness` (200 con el proceso vivo, sin consultar dependencias) y `GET /actuator/health/readiness` (200 solo con PostgreSQL arriba). Kafka, RabbitMQ, sales e identity SHALL reflejarse como degradación visible sin bloquear la operación en curso, pero ninguna operación de negocio que necesite una dependencia caída SHALL habilitarse por defecto. Las rutas internas SHALL NOT exponerse por el Gateway.

#### Scenario: Sales caído antes de iniciar

- **WHEN** sales no responde durante el resumen previo al cierre
- **THEN** responde 503 `DEPENDENCY_UNAVAILABLE` sin filas nuevas en `cierre`
