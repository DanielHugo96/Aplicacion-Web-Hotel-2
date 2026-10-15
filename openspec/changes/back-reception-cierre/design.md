# Design

## Context

Ver [`proposal.md`](proposal.md) para la motivación. El cambio se implementa sobre `services/reception-service/` ya creado por `back-reception-operacion` (esquema V1 con `room_gate`, `recepcion`, `operacion_consumo` y `bloqueo_habitacion`, seguridad JWT, idempotencia y outbox/inbox de `back-plataforma-mensajeria`); este plan añade la migración V2 con la tabla `cierre` y no migra datos del monolito. La autoridad del cobro es el cálculo congelado en reception antes de invocar a sales ([`04-flujos.md` §4](../../../../docs/especificacion/04-flujos.md#4-cierre-seguro-de-estadía)). Las constraints que no se pueden relajar: dinero `numeric(12,2)` con HALF_UP y comparación exacta; `closureId` fijado a la `Idempotency-Key`; sin transacción distribuida entre reception y sales ni entre brokers; sin transacción SQL abierta durante Feign.

## Goals / Non-Goals

**Goals:**

- Liquidar la estadía con una única transición ganadora bajo lock (`sp_cierre_iniciar`, `sp_cierre_preparar_confirmacion`, `sp_cierre_completar`, `sp_cierre_rechazar_monto`).
- Congelar el cálculo antes de cualquier pago remoto y no recalcularlo jamás en `CONFIRMANDO`.
- Confirmar los consumos en sales de forma idempotente por `closureId`, con recibo único por recepción.
- Recuperar cierres cada 30 s según su fase, sin dobles cobros ni dobles salidas.
- Anular check-ins erróneos y archivar cerradas sin generar cobros ni limpiezas fantasma.
- Producir `SalidaRegistrada`, `limpieza.solicitada` y `notificacion.checkout` por outbox, en la misma transacción del cierre.

**Non-Goals:**

- Gates, check-in, edición de salida, lecturas, bloqueos y operaciones de consumo (plan `back-reception-operacion`, F3).
- Resumen y confirmación del lado de sales, débito de stock y recibos (`back-sales-ventas` y `back-inventory-stock`).
- Ciclo de limpieza en hotel y envío de correos (planes `back-hotel-operaciones` y `back-notification-worker`).
- Proyecciones y reportes de cierre (`back-reporting-proyecciones`).
- Infraestructura de brokers, DLT/DLQ y pollers de outbox (planes `back-plataforma-*`).

## Decisions

1. **Esquema y migración.** La tabla `cierre` se crea con una migración Flyway V2 derivada del [DDL de referencia](../../../../docs/especificacion/microservicios/reception/base-de-datos.md#ddl-de-referencia), con sus CHECK (`calculo` obligatorio desde `CONFIRMANDO`; `COMPLETADO` exige `receipt_sales` y `completed_at`), el índice único parcial `uq_cierre_vigente` (varios `RECHAZADO` más un único vigente por estadía) y el índice `ix_cierre_recuperacion` por (`estado`, `next_attempt_at`). Sin seeds de cierres: las pruebas usan la demo por API (cliente 100, habitación 101, tarifa 70, adelanto 20) y guardan el `idRecepcion` devuelto. Alternativa descartada: reutilizar la V1 o sembrar cierres por SQL; el contrato exige conservar cada `RECHAZADO` y nunca borrar un cierre para recuperarlo.

2. **Cierre como transacción por fases con CAS.** Cada avance de fase es un CAS bajo lock de la recepción (`sp_cierre_iniciar`, `sp_cierre_rechazar_monto`, `sp_cierre_preparar_confirmacion`, `sp_cierre_completar`), de modo que dos workers no pueden rechazar e iniciar la confirmación a la vez. El `request` queda inmutable desde `VALIDANDO` con su `request_hash`; el `calculo` (`alojamientoTotal`, `saldoAlojamiento`, `consumosPagadosAntes`, `consumosPendientes`, `consumosTotal`, `cobroAhora`, `totalGeneral`, `expectedPendingTotal`) se persiste validado antes del HTTP a sales y no se toca después. Los rechazos que dejan evidencia (`RECHAZADO`, `GATE` intacto, respuesta 409) se confirman antes de responder, nunca con una excepción que revierta la lápida.

3. **Orden Feign y congelamiento.** La validación remota del resumen (`GET /internal/cierres/{idRecepcion}/resumen`, scope `cierres:write`) ocurre fuera de la transacción; el commit a `CONFIRMANDO` ocurre antes del `POST /internal/cierres/confirmar`, siempre con el payload idéntico (`closureId`, `idRecepcion`, `idHotel`, `expectedPendingTotal`, `medioPago`). La recepción no mantiene transacción SQL abierta durante Feign y valida el recibo contra el `idHotel` y el importe congelados. Un reintento posterior a confirmar en sales no vuelve al cálculo de saldo pendiente.

4. **Idempotencia del cierre.** `closureId` es la `Idempotency-Key` del `POST /api/recepcion/registrar-salida`. La misma clave reanuda el cierre vigente o devuelve su resultado terminal (200 o 202 o el 409 de un `RECHAZADO`); otra clave sobre una estadía `CERRANDO` responde 409 `CLOSURE_IN_PROGRESS`; un cuerpo distinto con la misma clave responde 409 `IDEMPOTENCY_CONFLICT`; un nuevo intento tras `RECHAZADO` exige una clave nueva. El `DELETE` repetido con la misma clave devuelve el resultado original aunque el recurso cambie de estado.

5. **Recuperador cada 30 s.** Un planificador con intervalo fijo de 30 s recorre `cierre` por (`estado`, `next_attempt_at`): `VALIDANDO` repite lectura del resumen y cálculo; `CONFIRMANDO` repite únicamente la confirmación con el payload congelado; `COMPLETADO` devuelve el resultado guardado; `RECHAZADO` devuelve su 409 original. Los errores permanentes en `CONFIRMANDO` conservan la estadía en `CERRANDO` con `lastErrorCode` para revisión del operador; nunca revierten a `ACTIVA` ni abren otra cuenta.

6. **Anulación y archivado sin efectos fantasma.** `sp_recepcion_anular_checkin` hace CAS `ACTIVA` → `CERRADA` con `archivada=true` solo con cero operaciones `ABIERTAS` y sin cierre vigente; libera el gate a `LIBRE` con `roomVersion++` y publica `RecepcionActualizada` con `ocupacionActual` en `LIBRE`, sin limpieza, recibo ni cobro. `sp_recepcion_archivar` solo marca `archivada=true` sobre `CERRADA` y conserva importes y snapshots. Ambas exigen `If-Match` e `Idempotency-Key` y auditan en `audit_log`.

7. **Salidas por outbox.** Una fila por destino en la misma transacción de `sp_cierre_completar`: `KAFKA`/`reception.recepcion.v1` para `SalidaRegistrada` (key `idHabitacion`, `aggregateVersion=roomVersion`, `cierre` con `consumosPagadosAlCerrar`, `medioPago` y `closedAt`), `RABBIT`/`limpieza.solicitada` (`idHabitacion`, `idRecepcion`, `cleaningCycleId`, `roomVersion`) y `RABBIT`/`notificacion.checkout` (`idPersona`, `idRecepcion`, `destinatario`, `nombre`, `hotel`, `totalAlojamiento`, `totalConsumos`, `templateVersion=1`). La caída de un broker o de SMTP no revierte el cierre; el correo nunca es confirmación autoritativa.

8. **Errores sin código propio.** Cuando la spec no fija un código, se usa el genérico más cercano de [`errors.md`](../../../../docs/contracts/errors.md) como decisión local, sin inventar códigos: p. ej. 400 `VALIDATION_ERROR` para penalidad fuera de rango o motivo ausente con penalidad mayor que 0.

9. **Pruebas con dependencias reales.** PostgreSQL, Kafka y RabbitMQ reales (Testcontainers o Compose) más sales y hotel reales; los casos OUT01–OUT06 se prueban matando y reiniciando el proceso en cada fase del cierre y simulando el resumen de sales detenido tras aceptar.

## Risks / Trade-offs

- [Timeout después de aceptar el cierre] → 202 con `Location`, `Retry-After` y fase; el panel muestra «cierre pendiente, no volver a cobrar»; la misma clave reanuda sin segundo cobro (OUT02).
- [Caída en VALIDANDO vs CONFIRMANDO vs tras el recibo] → cada fase se recupera según su regla; `CONFIRMANDO` reenvía el payload congelado y `COMPLETADO` no recalcula (OUT05).
- [Monto declarado con un centavo de diferencia] → 409 `AMOUNT_MISMATCH` con nueva cotización y cierre `RECHAZADO` conservado; ningún pago remoto se invocó (OUT03).
- [Venta admitida en vuelo durante el cierre] → 409 `OPERATIONS_IN_PROGRESS` sin cambiar la estadía; ninguna venta se admite después (OUT01).
- [Error permanente en CONFIRMANDO] → estadía `CERRANDO` con `lastErrorCode` para el operador; no se abre otra cuenta ni se revierte a `ACTIVA`.
- [Anulación con operaciones o cierre en curso] → 409 sin cambiar nada; el gate no se libera dos veces.
- [Zona horaria] → noches por diferencia de días y vencimiento en America/Lima calculados en servidor; `fechaSalidaConfirmacion` en UTC.
