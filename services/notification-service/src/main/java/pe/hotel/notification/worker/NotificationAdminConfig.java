package pe.hotel.notification.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Modo CLI del operador ({@code --admin}): sin puerto, sin Gateway y sin worker. Solo cablea la
 * herramienta local contra {@code notification_db} con la credencial de entorno.
 */
@Configuration
@ConditionalOnProperty(prefix = "hotel.notification", name = "admin", havingValue = "true")
public class NotificationAdminConfig {

  @Bean
  public NotificationJobStore notificationJobStore(JdbcTemplate jdbcTemplate) {
    return new NotificationJobStore(jdbcTemplate);
  }

  @Bean
  public AuditLogStore auditLogStore(JdbcTemplate jdbcTemplate) {
    return new AuditLogStore(jdbcTemplate);
  }

  @Bean
  public AdminCli adminCli(
      NotificationJobStore jobs, AuditLogStore audit, NotificationProperties properties) {
    return new AdminCli(jobs, audit, properties);
  }
}