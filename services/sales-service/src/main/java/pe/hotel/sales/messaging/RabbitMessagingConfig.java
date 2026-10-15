package pe.hotel.sales.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Productor RabbitMQ del outbox. Este servicio es solo productor: publica a
 * {@code hotel.commands.v1} pero no declara colas (las declara su consumidor, dueño de la cola).
 */
@Configuration
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMessagingConfig {

  public static final String COMMANDS_EXCHANGE = "hotel.commands.v1";
  public static final String DLX_EXCHANGE = "hotel.dlx.v1";

  @Bean
  public OutboxPublisher rabbitOutboxPublisher(RabbitTemplate rabbitTemplate) {
    return new RabbitOutboxPublisher(rabbitTemplate);
  }
}
