package pe.hotel.identity.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Productor RabbitMQ del outbox. Publica persistentes y {@code mandatory} al exchange
 * {@code hotel.commands.v1} con {@code routing key = channel} y espera la confirmación del broker
 * antes de dar la publicación por buena.
 */
public class RabbitOutboxPublisher implements OutboxPublisher {

  private static final long CONFIRM_TIMEOUT_MS = 10_000;

  private final RabbitTemplate rabbitTemplate;

  public RabbitOutboxPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public Destination destination() {
    return Destination.RABBIT;
  }

  @Override
  public void publish(OutboxMessage message) {
    CorrelationData correlation = new CorrelationData(message.id().toString());
    rabbitTemplate.convertAndSend(
        RabbitMessagingConfig.COMMANDS_EXCHANGE,
        message.channel(),
        message.payload(),
        m -> {
          m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          return m;
        },
        correlation);
    try {
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (!confirm.isAck()) {
        throw new IllegalStateException("el broker no confirmo el comando: " + confirm.getReason());
      }
      if (correlation.getReturned() != null) {
        throw new IllegalStateException("comando no ruteable (mandatory)");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("publicacion interrumpida", ex);
    } catch (ExecutionException | TimeoutException ex) {
      throw new IllegalStateException("sin confirmacion del broker", ex);
    }
  }

  @Override
  public void publishToDeadLetter(OutboxMessage message, String reason) {
    rabbitTemplate.convertAndSend(
        RabbitMessagingConfig.DLX_EXCHANGE,
        message.channel() + ".dlq",
        message.payload(),
        m -> {
          m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          return m;
        });
  }
}
