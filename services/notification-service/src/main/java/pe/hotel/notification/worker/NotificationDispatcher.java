package pe.hotel.notification.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Despacho con lease (docs/especificacion/microservicios/notification/api.md § RabbitMQ y
 * base-de-datos.md § Procedimientos): toma un job por worker con SKIP LOCKED, envía el correo
 * fuera de la transacción y registra el resultado por CAS. El correo es un efecto secundario: su
 * caída no revierte el negocio que originó el comando.
 */
public class NotificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int JOBS_POR_CICLO = 5;

  private final NotificationJobStore jobs;
  private final TemplateStore templates;
  private final SmtpMailer mailer;
  private final NotificationProperties properties;

  public NotificationDispatcher(
      NotificationJobStore jobs,
      TemplateStore templates,
      SmtpMailer mailer,
      NotificationProperties properties) {
    this.jobs = jobs;
    this.templates = templates;
    this.mailer = mailer;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${hotel.notification.dispatch-interval-ms:2000}")
  public void despachar() {
    for (int i = 0; i < JOBS_POR_CICLO; i++) {
      Optional<NotificationJob> tomado;
      try {
        tomado = jobs.tomar(Instant.now(), properties.getLeaseSeconds());
      } catch (Exception ex) {
        log.warn("notification_dispatch_error fase=toma error={}", ex.getMessage());
        return;
      }
      if (tomado.isEmpty()) {
        return;
      }
      despacharUno(tomado.get());
    }
  }

  private void despacharUno(NotificationJob job) {
    try {
      TemplateStore.Template plantilla =
          templates
              .buscar(job.tipo(), job.templateVersion())
              .orElseThrow(
                  () -> new NotificationPermanentException("plantilla desconocida: " + job.tipo()));
      String cuerpo = TemplateRenderer.render(plantilla.bodyText(), job.tipo(), variables(job));
      mailer.enviar(job.destinatario(), plantilla.subject(), cuerpo, job.messageId());
      jobs.resultado(
          job.commandId(), job.leaseToken(), true, null, properties.getMaxAttempts());
      log.info(
          "notification_sent commandId={} tipo={} attempts={} destinatario={} messageId={}",
          job.commandId(),
          job.tipo(),
          job.attempts(),
          Privacy.maskEmail(job.destinatario()),
          job.messageId());
    } catch (NotificationPermanentException permanente) {
      jobs.marcarError(job.commandId(), job.leaseToken(), "PAYLOAD_INVALIDO");
      log.error(
          "notification_permanent_error commandId={} tipo={} error={}",
          job.commandId(),
          job.tipo(),
          permanente.getMessage());
    } catch (Exception ex) {
      jobs.resultado(
          job.commandId(),
          job.leaseToken(),
          false,
          "SMTP_ERROR",
          properties.getMaxAttempts());
      log.warn(
          "notification_send_error commandId={} tipo={} attempts={} destinatario={} error={}",
          job.commandId(),
          job.tipo(),
          job.attempts(),
          Privacy.maskEmail(job.destinatario()),
          ex.getMessage());
    }
  }

  /** Variables del job a mapa escalar; un valor no escalar es error permanente. */
  static Map<String, Object> variables(NotificationJob job) {
    Map<String, Object> valores = new LinkedHashMap<>();
    try {
      JsonNode nodo = MAPPER.readTree(job.variables() == null ? "{}" : job.variables());
      nodo.fields()
          .forEachRemaining(
              entrada -> valores.put(entrada.getKey(), escalar(entrada.getValue())));
    } catch (Exception ex) {
      throw new NotificationPermanentException("variables no son JSON válido");
    }
    return valores;
  }

  private static Object escalar(JsonNode valor) {
    if (valor.isObject() || valor.isArray() || valor.isNull()) {
      throw new NotificationPermanentException("valor no escalar en variables");
    }
    if (valor.isNumber()) {
      return valor.decimalValue().toPlainString();
    }
    return valor.asText();
  }
}