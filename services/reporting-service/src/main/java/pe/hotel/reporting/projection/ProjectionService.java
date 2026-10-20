package pe.hotel.reporting.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.reporting.messaging.InboxStore;
import pe.hotel.reporting.messaging.MessageInvalidException;
import pe.hotel.reporting.messaging.MessageValidator;

/**
 * Aplica un evento en una única transacción local: inbox (deduplicación) + efecto + checkpoint.
 * El offset de Kafka se confirma después del commit local (ack-mode record).
 */
@Service
public class ProjectionService {

  /** Nombre del grupo de consumidor; el inbox deduplica por (consumer, eventId). */
  public static final String CONSUMER = "reporting-proyeccion-v1";

  private final InboxStore inbox;
  private final ProjectionStore store;
  private final MessageValidator validator;

  public ProjectionService(InboxStore inbox, ProjectionStore store, MessageValidator validator) {
    this.inbox = inbox;
    this.store = store;
    this.validator = validator;
  }

  @FunctionalInterface
  public interface Effect {
    void apply(JsonNode envelope, long version);
  }

  @Transactional
  public void handle(
      String topic, int partition, long offset, String value, Effect effect) {
    JsonNode envelope = validator.validate(topic, value);
    UUID eventId;
    try {
      eventId = UUID.fromString(envelope.path("eventId").asText());
    } catch (IllegalArgumentException invalid) {
      throw new MessageInvalidException("eventId no es un UUID");
    }
    long version = envelope.path("aggregateVersion").asLong(0);
    if (inbox.claim(CONSUMER, eventId)) {
      effect.apply(envelope, version);
    }
    store.touchCheckpoint(topic, partition, offset);
  }
}
