# plataforma-mensajeria Specification

## Purpose
Provee entrega al menos una vez con efectos idempotentes para los siete servicios, mediante outbox/inbox, orden por agregado, reintentos acotados y destinos de fallo observables.

## Requirements

### Requirement: Tablas outbox e inbox por servicio

Cada servicio SHALL persistir `outbox` e `inbox` según el DDL de referencia, con una fila por destino y la escritura de `outbox` en la misma transacción que la mutación.

#### Scenario: Mutación deja fila pendiente

- **WHEN** un servicio confirma una mutación con el broker detenido
- **THEN** existe una fila en `outbox` con `published_at` nulo y la mutación permanece visible

#### Scenario: Consumidor deduplica por inbox

- **WHEN** el mismo identificador de mensaje se entrega dos veces al mismo consumidor
- **THEN** el efecto se aplica una sola vez y la segunda entrega se descarta por `inbox`

### Requirement: Poller con backoff y alertas

El poller de cada servicio SHALL leer pendientes cada 5 s en lotes de 50, posponer `next_attempt_at` con backoff exponencial con tope de 5 min y alertar si una fila supera 15 min pendiente o 10 intentos.

#### Scenario: Reintento con backoff

- **WHEN** la publicación falla con el broker detenido
- **THEN** la fila incrementa `attempts` y pospone `next_attempt_at` sin marcarse como publicada

#### Scenario: Alerta por atraso

- **WHEN** una fila supera 15 min pendiente o 10 intentos
- **THEN** el sistema emite la alerta operativa correspondiente

### Requirement: Orden por agregado

El publicador SHALL NOT publicar la versión N+1 de un agregado si existe una N pendiente en el mismo destino, y el consumidor SHALL aplicar solo versiones mayores del mismo agregado además de `inbox`.

#### Scenario: Versión vieja no sobrescribe

- **WHEN** llega un evento con versión menor a la ya aplicada para el mismo agregado
- **THEN** el consumidor lo descarta sin cambiar la proyección ni el gate

### Requirement: Topología Kafka y RabbitMQ declarada

El sistema SHALL declarar los exchanges hotel.commands.v1 y hotel.dlx.v1, las seis colas principales con sus reintentos y DLQ, y los cuatro grupos de consumidor con sus DLT y reintentos, con los nombres exactos de la especificación.

#### Scenario: Fallo agotado va a destino de fallo

- **WHEN** un mensaje Kafka agota sus 5 reintentos o un comando RabbitMQ agota sus 3 reintentos
- **THEN** el mensaje queda en su DLT o DLQ con identificador y clave originales conservados

#### Scenario: Reintento conserva identidad

- **WHEN** un comando se reintenta por TTL o un evento se reenvía desde DLT
- **THEN** el identificador original se conserva y el consumidor lo procesa idempotentemente

### Requirement: Validación contra JSON Schemas v1

Cada mensaje SHALL validarse contra su esquema v1 antes de publicar y al consumir; los errores de esquema permanentes SHALL ir directo a DLT/DLQ sin reintento infinito.

#### Scenario: Mensaje inválido va directo a fallo

- **WHEN** un mensaje no valida contra su esquema v1
- **THEN** el sistema lo deriva a DLT/DLQ sin reintentos adicionales

#### Scenario: Esquema válido se publica

- **WHEN** un mensaje valida contra su esquema v1 y el broker confirma
- **THEN** la fila se marca como publicada y el consumidor puede procesarla
