# Proposal

## Why

La fase F4 del primer incremento liquida la estadía y mueve dinero real: sin `back-reception-cierre` no hay cobro de cierre, ni limpieza posterior, ni correo de checkout. El cierre es el flujo con más riesgo del proyecto: máquina de estados persistida (`VALIDANDO` → `CONFIRMANDO` → `COMPLETADO`), cálculo congelado con comparación exacta al centavo, doble broker (Kafka y RabbitMQ), concurrencia entre cierres y recuperación cada 30 s tras caídas. Un error aquí produce dobles cobros, ventas marcadas dos veces o habitaciones bloqueadas en `CERRANDO`. Por eso este plan se ejecuta en modo `openspec` ([norma §3](../../../docs/plans/README.md)), y su propuesta acompaña al plan [`docs/plans/back-reception-cierre.md`](../../../docs/plans/back-reception-cierre.md).

## What Changes

- Tabla `cierre` en `reception_db` (migración V2 derivada del [DDL de referencia](../../../docs/especificacion/microservicios/reception/base-de-datos.md#ddl-de-referencia)): estados `VALIDANDO`/`CONFIRMANDO`/`COMPLETADO`/`RECHAZADO`, `request` inmutable, `calculo` congelado obligatorio desde `CONFIRMANDO`, `receipt_sales`, `cleaning_cycle_id`, índice único parcial `uq_cierre_vigente` y procedimientos `sp_cierre_iniciar`, `sp_cierre_rechazar_monto`, `sp_cierre_preparar_confirmacion` y `sp_cierre_completar`.
- Cotización informativa: `GET /api/recepcion/{id}/cotizacion-salida` (penalidad simulada 0 por defecto, desglose HALF_UP, sin mutar nada).
- Cierre seguro: `POST /api/recepcion/registrar-salida` con `costoPenalidad`, `motivoPenalidad`, `montoRecibido` y `medioPago`; `closureId` fijado a la `Idempotency-Key`; `ACTIVA` → `CERRANDO` con cierre `VALIDANDO`; resumen por Feign a sales; comparación exacta con 409 `AMOUNT_MISMATCH`; congelamiento del cálculo antes de llamar a sales; confirmación idempotente con recibo; `CERRANDO` → `CERRADA` con gate a `LIMPIEZA_PENDIENTE`; 202 con `Location` y `Retry-After` ante timeout; recuperador cada 30 s.
- Anulación correctiva de check-in: `POST /api/recepcion/{id}/anular-checkin` (A, `If-Match`, motivo 1..300) con CAS `ACTIVA` → `CERRADA` archivada, gate a `LIBRE` con `roomVersion++`, evento `RecepcionActualizada` y auditoría, sin limpieza, recibo ni cobro.
- Archivado: `DELETE /api/recepcion/eliminar/{id}` (A, `If-Match`) solo sobre `CERRADA`, con `archivada=true` y conservación de importes y snapshots.
- Salidas por outbox en la misma transacción del cierre: `SalidaRegistrada` en `reception.recepcion.v1` y comandos `limpieza.solicitada` y `notificacion.checkout`.

Sin cambios de ruptura: `reception-service`, su esquema V1 y sus rutas de operación ya existen por `back-reception-operacion`; las rutas de este plan ya están declaradas en [`reception.yaml`](../../../docs/contracts/openapi/reception.yaml).

## Capabilities

### New Capabilities

- `reception/cierre`: liquidación de la estadía — cotización informativa, cierre con cálculo congelado y confirmación idempotente en sales, recuperación cada 30 s, anulación correctiva de check-in, archivado y salidas por outbox (`SalidaRegistrada`, `limpieza.solicitada`, `notificacion.checkout`).

### Modified Capabilities

Ninguna: `openspec/specs/` no contiene una capacidad previa de cierre que cambie.

## Impact

- **Código**: `services/reception-service/**` (migración V2, endpoints de cierre/anulación/archivado, Feign a sales con scope `cierres:write`, recuperador cada 30 s); `contracts/openapi/reception.yaml` solo se corrige si al ejecutar aparece una discrepancia con la spec.
- **Datos**: tabla `cierre` nueva en `reception_db`; sin seeds de cierres activos; sin reset de gates ni recepciones.
- **Dependencias**: sales (`GET /internal/cierres/{idRecepcion}/resumen` y `POST /internal/cierres/confirmar`, scope `cierres:write`), Kafka (`reception.recepcion.v1`) y RabbitMQ (`limpieza.solicitada`, `notificacion.checkout`).
- **Casos**: OUT01–OUT06 de [`06-migracion-y-pruebas.md`](../../../docs/especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional), con su evidencia en el PR de ejecución.
- **Otros planes**: `back-reception-operacion` aporta gates, check-in y operaciones (este plan no los reimplementa; OUT06 verifica su `PUT /api/recepcion/actualizar/{id}` sin cambios); `back-sales-ventas` implementa el resumen y la confirmación que este plan consume; `back-hotel-operaciones` consume `limpieza.solicitada`; `back-notification-worker` consume `notificacion.checkout`; los reportes consumen `SalidaRegistrada`.
