# inventory-productos Specification

## Purpose
Cubrir el catálogo de productos y existencias por sede del `inventory-service`: altas, edición y baja lógica por sede, ajustes con motivo auditado, consulta de movimientos y catálogo interno para sales.

## Requirements

### Requirement: Productos y existencias por sede

El sistema SHALL gestionar productos globales con existencia, precio, umbral y estado propios de cada sede (clave compuesta `idProducto` más `idHotel`), con validación de sede contra hotel al crear asignaciones o activar.

#### Scenario: Alta de producto con asignación inicial

- **WHEN** un ADMIN envía `POST /api/producto/registrar` con `sku`, `nombre`, `idHotel`, `precio` mayor que 0 y `cantidadInicial` mayor o igual que 0
- **THEN** el sistema responde 201 con el producto y su asignación en esa sede, y emite `ProductoStockSnapshot` por outbox

#### Scenario: SKU duplicado

- **WHEN** se registra un `sku` ya existente
- **THEN** el sistema responde 409 `DUPLICATE_RESOURCE` sin crear filas

#### Scenario: Edición con versión

- **WHEN** un ADMIN envía `PUT /api/producto/actualizar/{id}` sin header `If-Match`
- **THEN** el sistema responde 428 `PRECONDITION_REQUIRED`; con versión distinta responde 412 `PRECONDITION_FAILED`

#### Scenario: La edición nunca cambia cantidad

- **WHEN** un `ProductoUpdate` incluye cantidad o se omite `productoVersion`
- **THEN** el sistema responde 400 `VALIDATION_ERROR` e indica el campo en `error.fields`

#### Scenario: Baja lógica por sede conserva historia

- **WHEN** un ADMIN envía `DELETE /api/producto/eliminar/{id}` con `idHotel`
- **THEN** el sistema responde 200, deja `estado` en falso solo en esa sede, conserva cantidad y movimientos, y la sede inactiva rechaza nuevos débitos con `PRODUCT_INACTIVE` pero sigue admitiendo restituciones de ventas previas

### Requirement: Ajustes de stock con motivo

El sistema SHALL aplicar ajustes por delta (distinto de 0) con motivo de 1 a 300 caracteres, bajo lock de la fila de stock, registrando el saldo resultante; el saldo nunca queda negativo.

#### Scenario: Ajuste válido

- **WHEN** un ADMIN envía `POST /api/producto/{id}/ajustes` con `idHotel`, `delta` y `motivo`
- **THEN** el sistema responde 201 con `ajusteId`, cantidad resultante y versión, y emite `ProductoStockSnapshot` por outbox

#### Scenario: Ajuste que dejaría saldo negativo

- **WHEN** el delta supera en negativo la cantidad disponible
- **THEN** el sistema rechaza sin mover unidades ni registrar ajuste

### Requirement: Catálogo interno para sales

El sistema SHALL exponer `GET /internal/productos` y `GET /internal/productos/{id}` solo a sales con token máquina y scope `stock:read`, exigiendo siempre `idHotel`.

#### Scenario: Acceso con token humano

- **WHEN** una identidad humana (incluido ADMIN) llama a una ruta `/internal/**` de inventory
- **THEN** el sistema responde 403 `FORBIDDEN` y el stock queda intacto

### Requirement: Consulta de movimientos

El sistema SHALL exponer `GET /api/producto/{id}/movimientos` (unión discriminada `VENTA` con su estado actual o `AJUSTE`) con filtros de sede, paginación e intervalo opcional sobre `createdAt`, y `GET /internal/stock/movimientos/{idVenta}` para sales, que responde 404 si no hay registro.

#### Scenario: Movimiento sin débito previo

- **WHEN** se consulta por producto una venta rechazada antes del débito
- **THEN** esa venta no aparece en la ruta por producto; sí aparece en la consulta interna por `idVenta` si existe registro

### Requirement: Seeds deterministas y bootstrap

El sistema SHALL sembrar 3 productos (`AGUA-500`, `SNACK-01`, `ASEO-01`) activos en las sedes 1 a 3 con precios 3.50, 8.00 y 5.00, cantidades 20, 10 y 5, `umbral_bajo` 5 y versión 1, y publicar 9 `ProductoStockSnapshot` de bootstrap de forma idempotente.

#### Scenario: Reejecución de seeds

- **WHEN** los seeds se ejecutan de nuevo con ventas ya realizadas
- **THEN** no se duplican filas ni se reponen las cantidades vendidas
