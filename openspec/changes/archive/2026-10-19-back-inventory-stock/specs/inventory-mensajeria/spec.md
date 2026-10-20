# Spec Delta

## Purpose

Cubrir la mensajería del `inventory-service`: emisión de snapshots de stock y de resoluciones de movimiento por Kafka, y consumo del comando de restitución por RabbitMQ.

## ADDED Requirements

### Requirement: Emisión de snapshots de stock

El sistema SHALL publicar `ProductoStockSnapshot` en el topic `inventory.stock.v1` con key `idHotel:idProducto` y `aggregateVersion` igual a la versión de `producto_sede`, por outbox en la misma transacción de la mutación, con el estado efectivo (`producto.estado` AND `producto_sede.estado`).

#### Scenario: Cambio de catálogo con snapshot publicado

- **WHEN** una actualización de producto cambia el snapshot publicado
- **THEN** el sistema incrementa la versión de cada `producto_sede` afectado y emite un snapshot por sede

### Requirement: Emisión de resoluciones de movimiento

El sistema SHALL publicar `MovimientoStockResuelto` en el topic `inventory.movimiento.v1` con key `idVenta` y la versión del movimiento, con estado `DESCONTADO`, `RECHAZADO` o `RESTITUIDO`, por outbox en la misma transacción que la decisión.

#### Scenario: Orden por agregado

- **WHEN** hay una versión pendiente del mismo agregado en el mismo destino
- **THEN** el sistema no publica la versión siguiente hasta confirmar la anterior

### Requirement: Consumo del comando de restitución

El sistema SHALL consumir la cola `stock.restituir` (payload `idVenta`, `idRecepcion`, `motivo` e `idHotel` del envelope, sin cantidades ni precios del emisor) con inbox, efecto sobre el stock y evento de resolución en la misma transacción antes del ack, con reintentos con TTL y DLQ final.

#### Scenario: Comando duplicado

- **WHEN** el mismo `commandId` se entrega dos veces
- **THEN** el efecto se aplica una sola vez gracias al inbox

#### Scenario: Agotamiento de reintentos

- **WHEN** el comando falla de forma permanente o agota los reintentos
- **THEN** el mensaje termina en `stock.restituir.dlq`, la venta queda pendiente de operador y el stock no se da por restituido
