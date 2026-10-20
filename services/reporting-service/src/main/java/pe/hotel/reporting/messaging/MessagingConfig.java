package pe.hotel.reporting.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cablea las piezas de mensajería que usa el read model: inbox (deduplicación) y validación de
 * esquemas. Se desactiva con {@code hotel.messaging.enabled=false} en las pruebas sin brokers ni
 * base de datos.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MessagingConfig {

  @Bean
  public InboxStore inboxStore(JdbcTemplate jdbcTemplate) {
    return new InboxStore(jdbcTemplate);
  }

  @Bean
  public MessageValidator messageValidator(MessagingProperties properties) {
    return new MessageValidator(properties.getMessaging().getSchemasLocation());
  }

  @Bean
  public InboxRetentionJob inboxRetentionJob(InboxStore inboxStore, MessagingProperties properties) {
    return new InboxRetentionJob(inboxStore, properties);
  }
}
