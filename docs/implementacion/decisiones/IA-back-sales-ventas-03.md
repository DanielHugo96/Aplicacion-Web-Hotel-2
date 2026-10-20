# IA-back-sales-ventas-03 — Sin snapshot de venta cuando no hay líneas confirmadas

- **Plan:** `back-sales-ventas`
- **Paso:** 12 y 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

La spec pide emitir `sales.venta.v1` «también cuando se cancela». El JSON Schema
`sales.venta.v1.VentaSnapshot.schema.json` exige `payload.detalles` con `minItems: 1` y
`precioUnitario > 0`. Una venta técnica PENDIENTE que se anula antes de que inventory confirme el
débito no tiene `detalle_venta`: emitir su snapshot produce un mensaje inválido y el poller lo
deriva a DLT (reproducido en STK04 con la venta 3).

## Qué se decidió

`SalesOutbox.emitirVenta` no encola nada si la venta no tiene líneas confirmadas. Las ventas con
snapshot autoritativo (confirmadas y pagadas, incluidas las pagadas desde el cierre) siguen
emitiendo su snapshot con la forma exacta del esquema.

## Por qué

El contrato publicado (JSON Schema) manda sobre una lectura literal que produciría mensajes
inválidos. Un consumo que nunca se confirmó no tiene líneas autoritativas que proyectar; inventar
una línea con precio sería peor. Es aditivo: no cambia el esquema ni el comportamiento de las ventas
confirmadas.

## Alternativas descartadas

- **Emitir `detalles: []`.** El poller lo rechaza contra el esquema y lo manda a DLT; es un fallo de
  contrato.
- **Inventar una línea con `precioUnitario` > 0.** Falsifica un consumo que no existió.

## A quién afecta

`sales-service` (`SalesOutbox`) y los consumidores de `sales.venta.v1` (reporting). No cambia el
esquema ni ningún contrato.
