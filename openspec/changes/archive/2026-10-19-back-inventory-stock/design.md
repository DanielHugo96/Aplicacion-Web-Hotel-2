# Design

## Context

Ver `proposal.md` (Why). El `inventory-service` es el único dueño del stock (`inventory_db`); sales orquesta la venta y solo invoca el débito y la restitución. Rigen el [contrato común](../../docs/especificacion/03-contrato-comun.md) (idempotencia, outbox/inbox, envelope), el [flujo de venta y stock](../../docs/especificacion/04-flujos.md#2-venta-y-stock) y la [mensajería](../../docs/especificacion/05-mensajeria.md). Las convenciones transversales (seguridad JWT, outbox/inbox, poller) llegan con los planes `back-plataforma-*` ya previstos.

## Goals / Non-Goals

- Goals: descuento concurrente seguro, restitución exactamente-una-vez observable, rechazos y lápidas durables, eventos ordenados por agregado.
- Non-Goals: lógica de carrito, cobro o cierre (de sales/reception); reportes (de reporting); reposición automática o alertas por correo.

## Decisions

- **Locks en orden fijo más UPDATE condicional**: `fn_stock_descontar` bloquea el movimiento por `idVenta` y luego las filas de `producto_sede` en `idProducto` ascendente; la deducción usa `cantidad >= solicitada` y verifica filas afectadas. Alternativa (SELECT seguido de UPDATE) descartada: pierde carreras bajo concurrencia.
- **Doble idempotencia**: `UNIQUE` sobre `id_venta` más comparación de `request_hash` en la misma transacción, además de `Idempotency-Key` HTTP en rutas humanas. Alternativa (solo clave HTTP) descartada: no cubre reintentos de sales con otra clave para la misma venta.
- **Lápida `RESTITUIDO` previa al débito**: `sp_stock_restituir` crea el movimiento terminal si no existe. Alternativa (rechazar la restitución huérfana) descartada: dejaría la venta en estado incierto y permitiría un débito tardío.
- **Lógica de stock en funciones SQL** (`fn_stock_descontar`, `sp_stock_restituir`, `fn_stock_ajustar`, `sp_producto_sede_crear`) con JPA solo para catálogo y precios. Alternativa (todo en JPA) descartada: es más difícil garantizar atomicidad y orden de locks.
- **Dos eventos Kafka separados**: `inventory.stock.v1` por `producto_sede` y `inventory.movimiento.v1` por venta, cada uno con su key y versión. Alternativa (un solo topic) descartada: mezclaría granularidades y orden por agregado distintos.
- **Bootstrap con `eventId` estable**: los 9 snapshots de seeds se encolan una sola vez y su reenvío conserva `eventId` y key para que el inbox de reporting los deduplique.

## Risks / Trade-offs

- [Risk] Dos ventas agotan el stock al límite → una recibe 409 aunque el usuario ya eligió productos. Mitigación: el catálogo de sales muestra stock informativo y la UI propone crear una venta nueva.
- [Risk] Restitución en DLQ deja la venta pendiente de operador. Mitigación: alerta y reenvío manual conservando `commandId`; el stock nunca se da por restituido sin efecto aplicado.
- [Risk] Cambio de precio entre débito y lectura posterior. Mitigación: el resultado persiste precios autoritativos y reintentos con el mismo hash devuelven ese resultado, no el catálogo actual.

## Migration Plan

- Migración Flyway V1 con el DDL de referencia más tablas técnicas comunes; seeds de dev idempotentes (3 productos × 3 sedes).
- Sin datos que migrar del monolito en este plan (los seeds son limpios); despliegue sin rollback de datos: repetir seeds no repone ventas.
- Rollback de versión: anterior sin inventory; sales no debe invocarlo hasta que este plan esté `cerrado`.

## Open Questions

Ninguna que cambie specs, enfoque o tareas.
