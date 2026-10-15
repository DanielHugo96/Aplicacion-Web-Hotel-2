# Design

## Context

Ver `proposal.md - Why`. La especificación ya fija las rutas, tablas, procedimientos con CAS, transiciones de `04-flujos.md §2` y `§3`, cierres de `§4`, y los contratos de `05-mensajeria.md` con sus JSON Schemas. Las convenciones de JWT, envelope, idempotencia, outbox e inbox llegan con `back-plataforma-comunes` y `back-plataforma-mensajeria`. Reception admite operaciones e inventory descuenta; sales orquesta sin transacción distribuida.

## Goals / Non-Goals

**Goals:**

- Dejar la venta como máquina de estados observable con admisión, débito, confirmación o compensación, sin doble débito ni confirmación tras compensar.
- Hacer cada timeout recuperable cada 30 s con el mismo CAS que la respuesta HTTP, sin compensar una CONFIRMADA por antigüedad.
- Emitir snapshots y comandos por outbox con clave estable y consumir compensaciones de forma idempotente.

**Non-Goals:**

- Implementar el débito de inventory, la admisión de reception, el cierre de estadía, las proyecciones de reporting o los correos.
- Añadir topics, comandos, grupos, rutas, tablas o códigos fuera de los asignados por el catálogo.

## Decisions

- Orquestación por `operacion_venta` con un solo registro no terminal por venta y CAS en cada transición, frente a máquina solo en memoria: el crash no pierde la intención y el reconciliador compite con la respuesta HTTP bajo el mismo lock.
- Venta técnica PENDIENTE más operación PREPARANDO antes de cualquier Feign, y paso a DEBITO_SOLICITADO solo con CAS ganador antes del HTTP a inventory, frente a descontar y luego persistir: si se pierde el CAS está prohibido invocar inventory y un éxito tardío no confirma.
- Resultado remoto incierto hacia CANCELACION_PENDIENTE con `stock.restituir` en la misma transacción y respuesta 202, frente a reintentar el débito a ciegas: evita doble descuento y deja la restitución como comando durable.
- Finalización de reception siempre por comando durable tras el commit terminal, incluida RECHAZADA, frente a llamada HTTP directa: si finalizar llega antes que registrar deja lápida y el registro tardío devuelve 409 sin débito.
- Cierre de consumos con recibo único por `closure_id` y bloqueo por recepción, buscando el recibo antes de recalcular, frente a recalcular en cada reintento: un retry tras confirmar devuelve el mismo recibo sin volver a pagar ventas ya pagadas.
- Consumidor único `sales-compensacion-v1` para `inventory.movimiento.v1` y `reception.recepcion.v1` con inbox y regla de versión mayor más estado, frente a dos consumidores: una sola transacción decide CANCELADA solo ante RESTITUIDO y la limpieza de carrito ante SalidaRegistrada es idempotente.
- Recuperador con `@Scheduled(fixedRate=30000)` que revisa `operacion_venta` además de `venta`, con reintentos de PAGO por la misma admisión y sin compensar inventario por timeout de pago, frente a barrer solo por edad: respeta la restricción local y el orden de locks.
- Clave de outbox estable por agregado y versión para snapshots y comandos, frente a identificador aleatorio por reintento: el reenvío conserva identidad y el consumidor deduplica.

## Risks / Trade-offs

- [Timeout entre débito y confirmación con éxito real en inventory] → Mitigación con CANCELACION_PENDIENTE y restitución; si ya descontó devuelve las cantidades originales y si no descontó deja lápida.
- [Respuesta de admisión tardía tras RECHAZADA terminal] → Mitigación con CAS terminal que impide continuar al débito aunque llegue tarde la admisión.
- [DESCONTADO tardío tras compensar] → Mitigación con regla de no confirmar por evento: solo RESTITUIDO finaliza CANCELACION_PENDIENTE y un DESCONTADO tardío registra inconsistencia con alerta.
- [Doble pago o doble anulación concurrente] → Mitigación con índice único parcial de operación en curso por venta y CAS por tipo.
- [Cierre concurrente sobre la misma recepción] → Mitigación con bloqueo por recepción y recibo único; otro cierre devuelve 409 sin mutar.
- [Dependencia caída antes de operación durable] → Respuesta 503 sin estado técnico; tras operación durable el estado queda visible y recuperable.

## Migration Plan

- Migraciones Flyway nuevas sobre `sales_db` vacía con las cinco tablas de dominio más las técnicas ya provistas por plataforma; ninguna migración ya aplicada se edita.
- Seeds vacíos de carrito, ventas, operaciones y cierres; la fixture de integración se crea por API con ids devueltos como variables.
- Despliegue tras `back-reception-operacion`, `back-inventory-stock` y `back-plataforma-mensajeria` cerrados; sin rollback de datos más allá de recompilar una base efímera de prueba.

## Open Questions

Ninguna. Lo que la especificación no decide se registra en el plan como pregunta abierta y bloquea la ejecución.
