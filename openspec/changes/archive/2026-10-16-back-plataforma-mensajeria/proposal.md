# Proposal

## Why

Los siete servicios necesitan la misma base de entrega fiable antes de implementar negocio: sin outbox e inbox comunes, un fallo entre el commit y el broker pierde eventos o duplica efectos (stock, cobros, limpieza).

## What Changes

- Aplica las tablas `outbox` e `inbox` del DDL de referencia en cada una de las siete bases, con migraciones Flyway propias por servicio.
- Aplica el poller común cada 5 s con lote de 50, backoff exponencial con tope de 5 min, alertas por más de 15 min pendiente o más de 10 intentos, y orden por agregado.
- Declara los exchanges hotel.commands.v1 y hotel.dlx.v1, las seis colas principales, sus reintentos con TTL y sus DLQ, y los cuatro grupos Kafka con sus DLT y reintentos.
- Valida cada mensaje contra sus JSON Schemas v1 antes de publicar y al consumir, con errores permanentes directo a DLT/DLQ.
- No implementa lógica de negocio de ningún servicio ni crea topics, comandos, grupos, rutas o códigos nuevos.

## Capabilities

### New Capabilities

- `plataforma-mensajeria`: entrega transaccional común con outbox/inbox, poller, orden por agregado, reintentos, DLT/DLQ y validación de esquemas en los siete servicios.

### Modified Capabilities

- Ninguna. No existe capacidad previa en `openspec/specs/`; este es el primer change de plataforma.

## Impact

Afecta a `services/*-service` solo para la convención (tablas, poller, configuración de brokers y validación). Depende de `infra-compose` (PostgreSQL, Kafka, RabbitMQ sanos) y de `back-plataforma-comunes` (JWT, envelope, salud y logs). Habilita los planes de dominio que producen y consumen mensajes. Sin cambios en frontends ni en la especificación.
