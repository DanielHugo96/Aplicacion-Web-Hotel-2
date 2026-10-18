package pe.hotel.notification.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cablea el worker de notification: consumo idempotente de las tres colas, despacho con lease y
 * purga por retención. Se apaga con {@code hotel.messaging.enabled=false} (pruebas sin brokers ni
 * base de datos) y no se cablea en modo CLI ({@code hotel.notification.admin=true}): el proceso del
 * operador no consume, no despacha y no compite por los jobs.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificationWorkerConfig {

  @Bean
  public NotificationJobStore notificationJobStore(JdbcTemplate jdbcTemplate) {
    return new NotificationJobStore(jdbcTemplate);
  }

  @Bean
  public TemplateStore templateStore(JdbcTemplate jdbcTemplate) {
    return new TemplateStore(jdbcTemplate);
  }

  @Bean
  public AuditLogStore auditLogStore(JdbcTemplate jdbcTemplate) {
    return new AuditLogStore(jdbcTemplate);
  }

  @Bean
  public SmtpMailer smtpMailer(JavaMailSender mailSender) {
    return new SmtpMailer(mailSender);
  }

  /**
   * El despacho solo existe si el envío está habilitado: en producción, sin proveedor
   * configurado, no se intenta ningún envío y los jobs permanecen en PENDIENTE.
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "hotel.notification",
      name = "smtp-enabled",
      havingValue = "true")
  public NotificationDispatcher notificationDispatcher(
      NotificationJobStore jobs,
      TemplateStore templates,
      SmtpMailer mailer,
      NotificationProperties properties) {
    return new NotificationDispatcher(jobs, templates, mailer, properties);
  }

  @Bean
  public SchemaValidator rabbitSchemaValidator(
      pe.hotel.notification.messaging.MessageValidator validator) {
    return new RabbitSchemaValidator(validator);
  }

  @Bean
  public NotificationListener notificationListener(
      NotificationJobStore jobs,
      SchemaValidator validator,
      org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
    return new NotificationListener(jobs, validator, rabbitTemplate);
  }

  @Bean
  public SmtpHealthIndicator smtpHealthIndicator(
      @Value("${spring.mail.host}") String host,
      @Value("${spring.mail.port}") int port,
      NotificationProperties properties) {
    return new SmtpHealthIndicator(host, port, (int) properties.getSmtpTimeoutMs());
  }
}