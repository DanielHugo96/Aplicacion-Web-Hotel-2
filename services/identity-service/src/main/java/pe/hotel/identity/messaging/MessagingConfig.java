package pe.hotel.identity.messaging;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cablea la plataforma de mensajería: outbox, poller, inbox y su purga. Se desactiva con
 * {@code hotel.messaging.enabled=false} (pruebas sin brokers ni base de datos).
 */
@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
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
  public OutboxPoller outboxPoller(
      OutboxStore outboxStore,
      List<OutboxPublisher> publishers,
      MessageValidator validator,
      MessagingProperties properties) {
    return new OutboxPoller(outboxStore, publishers, validator, properties);
  }

  @Bean
  public InboxRetentionJob inboxRetentionJob(
      InboxStore inboxStore, MessagingProperties properties) {
    return new InboxRetentionJob(inboxStore, properties);
  }
}
