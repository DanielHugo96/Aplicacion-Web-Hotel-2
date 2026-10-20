package pe.hotel.reception.messaging;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cablea la plataforma de mensajería: outbox, poller, inbox y su purga. Las piezas de persistencia
 * (outbox/inbox y validador de esquemas) existen siempre; el poller y los consumidores se activan
 * con {@code hotel.messaging.enabled=true} (las pruebas de contexto los desactivan sin brokers).
 */
@Configuration
@EnableScheduling
public class MessagingConfig {

  @Bean
  public OutboxStore outboxStore(JdbcTemplate jdbcTemplate) {
    return new OutboxStore(jdbcTemplate);
  }

  @Bean
  public InboxStore inboxStore(JdbcTemplate jdbcTemplate) {
    return new InboxStore(jdbcTemplate);
  }

  @Bean
  public MessageValidator messageValidator(MessagingProperties properties) {
    return new MessageValidator(properties.getMessaging().getSchemasLocation());
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "hotel.messaging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OutboxPoller outboxPoller(
      OutboxStore outboxStore,
      List<OutboxPublisher> publishers,
      MessageValidator validator,
      MessagingProperties properties) {
    return new OutboxPoller(outboxStore, publishers, validator, properties);
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "hotel.messaging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public InboxRetentionJob inboxRetentionJob(
      InboxStore inboxStore, MessagingProperties properties) {
    return new InboxRetentionJob(inboxStore, properties);
  }
}
