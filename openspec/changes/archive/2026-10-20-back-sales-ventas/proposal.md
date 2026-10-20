# Proposal

## Why

Las ventas de consumo tocan dinero, stock y estadías a la vez: sin una máquina de estados con admisión, débito idempotente y compensación observable, un timeout puede cobrar dos veces, descontar sin venta o dejar una estadía bloqueada.

## What Changes

- Implementa en `sales-service` el carrito por estadía y el catálogo privado de la sede, sin descontar ni reservar stock desde el carrito.
- Implementa el alta de venta PENDIENTE con admisión en reception, débito en inventory por Feign, confirmación o rechazo con CAS, y respuestas 201 o 202 con polling.
- Implementa el pago manual de ventas CONFIRMADAS y la anulación de impagadas con comando durable de restitución, sin borrar historial.
- Implementa la recuperación cada 30 s de operaciones PENDIENTE y CANCELACION_PENDIENTE y la compensación ante resultado remoto incierto.
- Produce `sales.venta.v1` por outbox y los comandos `stock.restituir` y `recepcion.operacion-finalizar` por outbox con clave estable.
- Consume con el grupo `sales-compensacion-v1` los topics `inventory.movimiento.v1` y `reception.recepcion.v1` para compensar y limpiar el carrito, con DLT por topic y grupo.
- Expone el resumen y la confirmación de cierre de consumos para reception con recibo único idempotente.
- No implementa débito, admisión, bloqueos, reportes, correos ni pantallas; esos pertenecen a sus planes.

## Capabilities

### New Capabilities

- `sales-ventas`: carrito, catálogo privado, ciclo de venta con pago y anulación, operaciones con recuperación y compensación, emisión de snapshots y comandos, y cierre de consumos para reception en `sales-service`.

### Modified Capabilities

- Ninguna. No existe capacidad previa en `openspec/specs/`; este es el primer change de ventas.

## Impact

Afecta solo a `services/sales-service/` (REST de carrito, venta, operaciones y cierres internos, persistencia de `sales_db`, Feign hacia reception e inventory, productores y consumidor asignados). Depende de `back-reception-operacion`, `back-inventory-stock` y `back-plataforma-mensajeria`, que deben estar cerrados para ejecutar. Habilita `back-reception-cierre`, `back-reporting-proyecciones` y los planes front de ventas y área de cliente. Sin cambios en la especificación ni en otros servicios.
