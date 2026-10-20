# IA-front-admin-web-area-cliente-03 — Atribución del 409 de stock en el carrito

- **Plan:** `front-admin-web-area-cliente`
- **Paso:** 5 y 6 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan pide pintar los `409` de venta “en la línea afectada”. El contrato distingue dos casos:
`PRODUCT_INACTIVE` trae `error.fields.idProducto`, pero `STOCK_INSUFFICIENT` solo trae
`error.fields.cantidad` (ver `docs/contracts/errors.md` y `fn_stock_descontar` de inventory). Con
varias líneas en el carrito, la respuesta no dice cuál producto agotó el stock.

## Qué se decidió

- `PRODUCT_INACTIVE` se pinta siempre en la línea de ese `idProducto`.
- `STOCK_INSUFFICIENT` se pinta en la única línea del carrito cuando hay exactamente una; con dos o
  más líneas se muestra el mensaje y la acción del catálogo en la franja del carrito, porque
  atribuirlo a una línea concreta sería inventar qué producto falló.
- Ambos casos muestran el `correlationId` en mono con botón Copiar.

## Por qué

Es la opción menos invasiva y la única que no inventa datos: usa el campo que el contrato sí envía.
No cambia `errors.md`, no añade campos a la respuesta de sales/inventory y no toca ningún plan
cerrado. El caso de una sola línea es el más frecuente en el área C y queda resuelto en la línea.

## Alternativas descartadas

- **Pintar el stock insuficiente en todas las líneas.** Atribuiría el fallo a productos que sí
  tienen stock.
- **Elegir la línea de mayor cantidad.** Es una heurística sin respaldo en el contrato.
- **Añadir `idProducto` a `STOCK_INSUFFICIENT` en sales/inventory.** Es un cambio de contrato y de
  servicios, fuera del alcance de un plan `front`.

## A quién afecta

- `apps/admin-web`: `carrito.component.ts` y su prueba de 409.
- Sin cambios de contrato. Si sales/inventory añaden `idProducto` al error de stock, la pantalla
  puede migrar a la atribución exacta sin rediseño.
