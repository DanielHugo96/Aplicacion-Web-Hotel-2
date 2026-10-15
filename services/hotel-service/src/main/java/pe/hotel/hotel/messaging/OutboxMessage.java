package pe.hotel.hotel.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del outbox leída por el poller. {@code payload} es el envelope JSON completo
 * (evento Kafka o comando RabbitMQ) tal como se publica.
 */
public record OutboxMessage(
    UUID id,
    Destination destination,
    String channel,
    String partitionKey,
    String aggregateId,
    long aggregateVersion,
    String type,
    String payload,
    Instant createdAt,
    int attempts,
    Instant nextAttemptAt) {}
