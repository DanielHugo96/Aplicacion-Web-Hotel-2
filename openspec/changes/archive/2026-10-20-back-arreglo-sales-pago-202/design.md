# Design

## Context

Ver `proposal.md - Why`. La especificación de [`04-flujos.md` §2](../../../docs/especificacion/04-flujos.md#2-venta-y-stock) ya fija la recuperación: revisa también `operacion_venta`, una operación `PAGO` en `PREPARANDO` o `ADMITIDA` reintenta la misma admisión y el pago local, un timeout de pago no compensa inventario, y un rechazo de admisión termina la operación sin tocar dinero ni stock. [`sales/api.md` §REST: ventas](../../../docs/especificacion/microservicios/sales/api.md#rest-ventas) fija el `202` con `Location` y `Retry-After: 2`, y [`sales/base-de-datos.md` §Tablas](../../../docs/especificacion/microservicios/sales/base-de-datos.md#tablas) ya tiene el estado `PREPARANDO` y la columna `next_attempt_at`. No hay una regla nueva que decidir: el código se alinea con lo publicado.

## Goals / Non-Goals

**Goals:**

- Que un `202` de pago deje una operación `PAGO` viva y recuperable, y que al volver reception la venta termine `PAGADO` dentro del sondeo de 90 s que usa el panel.
- Que la recuperación de `PAGO` y `ANULACION` en `PREPARANDO` use `next_attempt_at` y el mismo CAS que la respuesta HTTP, sin compensar inventario por un timeout de pago.
- Mantener intacto el rechazo de la venta alta `PENDIENTE` con operación `VENTA` en `PREPARANDO` de más de 120 s.

**Non-Goals:**

- Cambiar rutas, estados, códigos, tablas, migraciones, eventos o contratos.
- Reintentar un pago con una operación nueva: se reutiliza el mismo `operationId` y la misma petición.
- Compensar stock por un pago incierto; el pago no mueve inventario.
- Tocar `reception-service`, `inventory-service`, el Gateway o `apps/admin-web`.

## Decisions

- **Un fallo de transporte no es un rechazo.** Solo `409`, `403` y `404` de reception son rechazos que terminan la operación; `503`, timeout y errores de red dejan la operación en `PREPARANDO` y responden `202`. Se descarta marcar `TERMINAL` en el `catch` genérico porque, como demuestra la reproducción, el `202` promete una operación durable que ya no existe.
- **`next_attempt_at` para `PAGO` y `ANULACION`; `created_at` de 120 s para `VENTA`.** La edad de 120 s es la regla de la venta alta que puede haber quedado sin admisión; aplicarla al pago retrasaría la primera recuperación por detrás de los 90 s de sondeo del panel. La columna `next_attempt_at` ya existe y el plan alto la usa para posponer reintentos, así que es la fuente correcta para las operaciones de pago y anulación.
- **Sin compensación de inventario por el pago.** `aplicarPago` solo marca `PAGADO` con `paid_at` y `medio_pago` y emite el snapshot y el finalizar; no llama a inventory. Un timeout de pago no crea `stock.restituir`.
- **Finalizar una sola vez.** El comando `recepcion.operacion-finalizar` con `tipo=PAGO` y `resultado=PAGO_REGISTRADO` se emite en la misma transacción del CAS de pago; la recuperación no vuelve a emitirlo si la operación ya es terminal.

## Risks / Trade-offs

- [Reintento mientras reception sigue caído] → `reintentarAdmision` pospone 60 s cuando el fallo no es de negocio; el job de 30 s no martillea la dependencia.
- [Doble pago por reintento concurrente] → `pagarVenta` es CAS por `version`, `estado_operacion='CONFIRMADA'` y `estado='PENDIENTE'`; el segundo intento no actualiza y pospone o termina.
- [Operación `PAGO` en `PREPARANDO` sobre una venta ya pagada por el cierre] → el guard de `reintentarAdmision` termina la operación y asegura el finalizar, sin volver a admitir ni a cobrar.
- [La venta alta cambia de comportamiento] → la rama `VENTA` conserva la consulta y el rechazo actuales; las pruebas de regresión lo fijan.

## Migration Plan

Ninguna migración ni cambio de esquema. La base ya tiene `operacion_venta.estado`, `next_attempt_at` y `attempts`, y `venta.current_operation_id`. El arreglo se despliega con el servicio; las operaciones que ya quedaron `TERMINAL` por el defecto no se reabren automáticamente (son un registro del fallo anterior) y la verificación usa una venta nueva.

## Open Questions

Ninguna.
