# Proposal

## Why

`back-sales-ventas` dejó un pago con resultado incierto en una situación de la que no se recupera: `PUT /api/venta/{id}` responde `202` con `Location` y `Retry-After`, pero antes de responder marca la operación `PAGO` como `TERMINAL` y no la deja en `PREPARANDO`; además, el recuperador solo selecciona operaciones de ventas `PENDIENTE`. El resultado es que la venta queda `PENDIENTE` para siempre aunque reception vuelva, y el panel sondea hasta agotar el tiempo. La especificación ya exige lo contrario en [`04-flujos.md` §2](../../../docs/especificacion/04-flujos.md#2-venta-y-stock): la recuperación revisa `operacion_venta`, y una operación `PAGO` en `PREPARANDO` o `ADMITIDA` reintenta la misma admisión y el pago local, sin compensar inventario por un timeout de pago. Este change corrige la implementación para que cumpla ese contrato.

## What Changes

- `SalesService.pagar` y `SalesService.anular`: un fallo de transporte al admitir (timeout, `503` o cualquier respuesta que no sea un rechazo de negocio) deja la operación en `PREPARANDO` y responde `202`; solo `409`, `403` y `404` terminan la operación y devuelven su error. Hoy cualquier `FeignException` la marca `TERMINAL`.
- `SalesRepository.preparandoVencidas` y `RecuperadorVentas`: la recuperación distingue el tipo de operación. La venta `PENDIENTE` con operación `VENTA` en `PREPARANDO` sigue rechazándose a los 120 s; las operaciones `PAGO` y `ANULACION` en `PREPARANDO` se reintentan por `next_attempt_at`, sin esperar la edad de la venta alta, y se enrutan a la misma admisión y al mismo CAS que la respuesta HTTP.
- `RecuperadorVentas.reintentarAdmision`: si la venta ya está pagada o cancelada, termina la operación sin volver a admitirla y asegura el comando de finalización, en lugar de posponerla indefinidamente.
- Pruebas de regresión en `services/sales-service/src/test/**` para el `202` con reception caído, la recuperación de `PAGO`/`ANULACION` en `PREPARANDO`, el rechazo de la venta alta vieja y la ausencia de doble pago.
- Sin cambios en rutas, estados, códigos de error, tablas, migraciones, eventos ni contratos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `sales-ventas`: la recuperación de operaciones `PAGO` y `ANULACION` en `PREPARANDO` y el tratamiento del fallo de transporte al admitir una operación, sin cambiar la máquina de estados ni el contrato REST.

## Impact

- **Código**: solo `services/sales-service/**` (`SalesService`, `SalesRepository`, `RecuperadorVentas` y sus pruebas).
- **Datos**: sin migración. Se usan las columnas existentes de `venta` y `operacion_venta` (`estado`, `estado_operacion`, `current_operation_id`, `next_attempt_at`, `attempts`) y el índice único de operación no terminal.
- **Contratos**: ninguno. `PUT /api/venta/{id}` conserva `200`/`202` con `Location` y `Retry-After: 2`; `GET /api/venta/operaciones/{operationId}` conserva su forma; no se añaden códigos.
- **Mensajería**: `sales.venta.v1` y `recepcion.operacion-finalizar` se siguen emitiendo por outbox exactamente una vez por efecto; no se añaden topics, comandos ni grupos.
- **Otros planes**: desbloquea los pasos 19 y 20 de `front-admin-web-ventas`; no cambia la zona de `back-inventory-stock` ni de `back-reception-operacion`.
