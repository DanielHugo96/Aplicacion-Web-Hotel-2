package pe.hotel.identity.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Poller del outbox (docs/especificacion/03-contrato-comun.md § Persistencia transversal): lee
 * pendientes cada 5 s en lotes de 50, publica por destino respetando el orden por agregado, aplica
 * backoff con tope de 5 min, deriva errores de esquema a DLT/DLQ y alerta por atraso o intentos.
 */
public class OutboxPoller {

  private static final Duration LEASE = Duration.ofSeconds(30);
  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxStore outboxStore;
  private final Map<Destination, OutboxPublisher> publishers;
  private final MessageValidator validator;
  private final MessagingProperties properties;

  public OutboxPoller(
      OutboxStore outboxStore,
      List<OutboxPublisher> publishers,
      MessageValidator validator,
      MessagingProperties properties) {
    this.outboxStore = outboxStore;
    this.publishers =
        publishers.stream()
            .collect(
                Collectors.toMap(OutboxPublisher::destination, Function.identity(), (a, b) -> a));
    this.validator = validator;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${hotel.outbox.poll-interval-ms:5000}")
  public void poll() {
    for (Map.Entry<Destination, OutboxPublisher> entry : publishers.entrySet()) {
      try {
        List<OutboxMessage> batch =
            outboxStore.claim(entry.getKey(), properties.getOutbox().getBatchSize(), LEASE);
        log.debug(
            "outbox_poll destination={} claimed={} batch_size={}",
            entry.getKey(),
            batch.size(),
            properties.getOutbox().getBatchSize());
        for (OutboxMessage message : batch) {
          publishOne(entry.getValue(), message);
        }
      } catch (Exception ex) {
        log.warn("outbox_poll_error destination={} error={}", entry.getKey(), ex.getMessage());
      }
    }
    reportOverdue();
  }

  private void publishOne(OutboxPublisher publisher, OutboxMessage message) {
    try {
      validator.validate(message);
      publisher.publish(message);
      outboxStore.markPublished(message.id());
    } catch (MessageInvalidException invalid) {
      try {
        publisher.publishToDeadLetter(message, invalid.getMessage());
        outboxStore.markPublished(message.id());
      } catch (Exception ex) {
        outboxStore.markAttempt(
            message.id(), Instant.now().plus(MessagingPolicy.backoff(message.attempts() + 1)));
      }
      log.warn(
          "outbox_invalid_message id={} type={} channel={} error={}",
          message.id(), message.type(), message.channel(), invalid.getMessage());
    } catch (Exception ex) {
      int attempts = message.attempts() + 1;
      outboxStore.markAttempt(message.id(), Instant.now().plus(MessagingPolicy.backoff(attempts)));
      if (MessagingPolicy.shouldAlert(
          attempts,
          message.createdAt(),
          Instant.now(),
          properties.getOutbox().getAlertAttempts(),
          properties.getOutbox().getAlertPendingMinutes())) {
        log.error(
            "outbox_alert id={} type={} attempts={} pending_minutes={} error={}",
            message.id(),
            message.type(),
            attempts,
            MessagingPolicy.pendingMinutes(message.createdAt(), Instant.now()),
            ex.getMessage());
      }
    }
  }

  private void reportOverdue() {
    try {
      List<OutboxMessage> overdue =
          outboxStore.overdue(
              properties.getOutbox().getAlertAttempts(),
              properties.getOutbox().getAlertPendingMinutes());
      for (OutboxMessage message : overdue) {
        log.error(
            "outbox_alert id={} type={} attempts={} pending_minutes={} destination={}",
            message.id(),
            message.type(),
            message.attempts(),
            MessagingPolicy.pendingMinutes(message.createdAt(), Instant.now()),
            message.destination());
      }
    } catch (Exception ex) {
      log.warn("outbox_alert_error error={}", ex.getMessage());
    }
  }
}
