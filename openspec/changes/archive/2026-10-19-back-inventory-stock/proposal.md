# Proposal

## Why

Las ventas con consumo (F3) necesitan descontar existencias por sede de forma atómica y compensable: sin un dueño único del stock con descuento todo-o-nada, idempotencia por venta y restitución con lápida, dos ventas concurrentes pueden vender por encima del disponible o una anulación puede duplicar unidades. Este change propone el `inventory-service` como único dueño del stock antes de que `back-sales-ventas` lo invoque.

## What Changes

- Nuevo `inventory-service` con CRUD de productos y existencias por sede (`/api/producto/**`), ajustes con motivo y consulta de movimientos.
- Descuento interno `POST /internal/stock/descontar` atómico (todo o nada) con locks ordenados por `idProducto` e idempotencia por `idVenta` más hash del pedido.
- Restitución idempotente: devuelve las cantidades originales una sola vez o crea lápida `RESTITUIDO` que bloquea débitos tardíos con `DEBIT_CANCELLED`.
- Catálogo interno (`GET /internal/productos`, `GET /internal/productos/{id}`) y consulta de movimiento por venta para sales, solo con token máquina y scopes `stock:read` / `stock:debit`.
- Emisión por outbox de `inventory.stock.v1` (`ProductoStockSnapshot`) e `inventory.movimiento.v1` (`MovimientoStockResuelto`); consumo del comando `stock.restituir` con inbox.
- Seeds deterministas (3 productos × 3 sedes) y bootstrap de 9 snapshots iniciales.

## Capabilities

### New Capabilities

- `inventory-productos`: catálogo de productos y existencias por sede, ajustes con motivo, consulta de movimientos y catálogo interno para sales.
- `inventory-debito-restitucion`: descuento atómico con locks e idempotencia, rechazos durables y restitución idempotente con lápida.
- `inventory-mensajeria`: emisión de `inventory.stock.v1` e `inventory.movimiento.v1` y consumo del comando `stock.restituir`.

### Modified Capabilities

Ninguna: `openspec/specs/` está vacío y ningún comportamiento existente cambia.

## Impact

- Nuevo código en `services/inventory-service/` y nueva base `inventory_db` (tablas `producto`, `producto_sede`, `movimiento_venta`, `movimiento_detalle`, `ajuste_stock` más las técnicas comunes).
- Sales pasa a depender de inventory para el débito y la restitución (contratos Feign `stock:read` / `stock:debit`); reporting consume los dos topics nuevos.
- Sin cambios en otros servicios ni en el Gateway salvo el enrutado ya previsto por prefijo.
