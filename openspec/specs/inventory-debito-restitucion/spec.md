# inventory-debito-restitucion Specification

## Purpose
Cubrir el descuento atómico de stock por venta y su restitución idempotente en el `inventory-service`, con locks ordenados, idempotencia por venta y lápidas que bloquean débitos tardíos.

## Requirements

### Requirement: Descuento atómico todo o nada

El sistema SHALL descontar todos los ítems de un débito o ninguno, en una sola transacción: bloquea primero el registro del movimiento por `idVenta` y después las filas de stock en orden ascendente de `idProducto`, verifica producto y sede activos y cantidad suficiente, calcula precios vigentes autoritativos y persiste el resultado inmutable con `request_hash`.

#### Scenario: Débito válido

- **WHEN** sales envía `POST /internal/stock/descontar` con `idVenta`, `idHotel` e ítems de cantidades entre 1 y 9999 (máximo 50 productos distintos, sin duplicados)
- **THEN** el sistema responde 200 con estado `DESCONTADO`, precios y nombres autoritativos, total y versión, y emite `MovimientoStockResuelto` por outbox

#### Scenario: Concurrencia sobre el mismo stock

- **WHEN** dos ventas de 15 unidades compiten sobre un stock de 20 con claves distintas
- **THEN** solo una descuenta, la otra recibe 409 `STOCK_INSUFFICIENT` y el stock final es 5 con un único movimiento `DESCONTADO`

#### Scenario: Línea insuficiente en venta multítem

- **WHEN** una venta multítem tiene al menos una línea sin stock suficiente
- **THEN** el sistema responde 409 `STOCK_INSUFFICIENT` y todas las cantidades quedan intactas, sin descuentos parciales

#### Scenario: Reintento con la misma venta y el mismo hash

- **WHEN** se repite el débito con el mismo `idVenta` y el mismo hash
- **THEN** el sistema devuelve el mismo resultado guardado aunque el catálogo haya cambiado; con otro hash responde 409 `IDEMPOTENCY_CONFLICT`

#### Scenario: Ítems duplicados en el pedido

- **WHEN** el pedido trae el mismo `idProducto` dos veces
- **THEN** el sistema responde 400 `VALIDATION_ERROR` antes de ejecutar SQL, sin sumar líneas ocultamente

### Requirement: Rechazos durables sin descuento

El sistema SHALL persistir los rechazos por stock insuficiente o producto o sede inactivos como movimiento `RECHAZADO` mediante commit antes de responder 409, sin dejar descuentos parciales.

#### Scenario: Producto o sede inactivos

- **WHEN** alguna línea apunta a un producto o sede inactivos
- **THEN** el sistema responde 409 `PRODUCT_INACTIVE` sin mover unidades

### Requirement: Restitución idempotente con lápida

El sistema SHALL restituir exactamente las cantidades originales una sola vez; si el movimiento no descontó, crea una lápida `RESTITUIDO` sin detalles ni hash que prohíbe descuentos tardíos de esa venta; el resultado de un `DESCONTADO` se conserva al restituir para auditoría y nunca se borra ni se reescribe.

#### Scenario: Doble restitución

- **WHEN** se restituye dos veces la misma venta
- **THEN** el stock solo se devuelve una vez y ambas respuestas observan el mismo estado terminal

#### Scenario: Restitución antes del descuento

- **WHEN** la restitución llega antes que el débito de esa venta
- **THEN** el sistema crea la lápida `RESTITUIDO` y el débito tardío responde 409 `DEBIT_CANCELLED` sin mover stock

#### Scenario: Sede distinta a la del movimiento

- **WHEN** el comando de restitución trae un `idHotel` distinto al del movimiento existente
- **THEN** el sistema no mueve stock ajeno y deriva el mensaje a DLQ con alerta

### Requirement: Seguridad de las rutas internas de stock

El sistema SHALL exigir token máquina con scope `stock:debit` en `POST /internal/stock/descontar` y `stock:read` en la consulta por venta; un token de usuario nunca basta, y el débito confía solo en sales autenticado sin precios del emisor.

#### Scenario: Fallo transitorio

- **WHEN** ocurre un fallo transitorio tras persistir un resultado incierto
- **THEN** el sistema responde 503 `DEPENDENCY_UNAVAILABLE` y el resultado se recupera por consulta o compensación sin doble efecto
