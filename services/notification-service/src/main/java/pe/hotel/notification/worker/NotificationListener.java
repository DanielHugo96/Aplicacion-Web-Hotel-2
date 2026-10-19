package pe.hotel.notification.worker;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Consumo de las tres colas de notificación
 * (docs/especificacion/05-mensajeria.md § Reintentos y DLQ Rabbit y
 * microservicios/notification/api.md § RabbitMQ): valida contra su JSON Schema, persiste el job con
 * su inbox en la misma transacción y solo entonces confirma. Los errores anteriores a la
 * persistencia usan los reintentos TTL de Rabbit y luego la DLQ; los permanentes van directo a la
 * DLQ. Una vez persistido el job, el mensaje se confirma y los reintentos pertenecen al job.
 */
public class NotificationListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

  private final NotificationJobStore jobs;
  private final SchemaValidator validator;
  private final RabbitTemplate rabbitTemplate;

  public NotificationListener(
      NotificationJobStore jobs, SchemaValidator validator, RabbitTemplate rabbitTemplate) {
    this.jobs = jobs;
    this.validator = validator;
    this.rabbitTemplate = rabbitTemplate;
  }

  @RabbitListener(queues = "notificacion.bienvenida", ackMode = "MANUAL")
  public void bienvenida(Message message, Channel channel) throws IOException {
    atender("notificacion.bienvenida", message, channel);
  }

  @RabbitListener(queues = "notificacion.checkin", ackMode = "MANUAL")
  public void checkin(Message message, Channel channel) throws IOException {
    atender("notificacion.checkin", message, channel);
  }

  @RabbitListener(queues = "notificacion.checkout", ackMode = "MANUAL")
  public void checkout(Message message, Channel channel) throws IOException {
    atender("notificacion.checkout", message, channel);
  }

  private void atender(String cola, Message message, Channel channel) throws IOException {
    long entrega = message.getMessageProperties().getDeliveryTag();
    String cuerpo = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
    try {
      NotificationCommand comando = NotificationCommand.parse(cuerpo);
      validator.validar(cola, comando);
      int idHotel = comando.idHotel() == null ? 0 : comando.idHotel();
      jobs.encolar(comando.commandId(), comando.tipo(), cuerpo);
      channel.basicAck(entrega, false);
      log.info(
          "notification_job_persisted commandId={} tipo={} cola={} correlationId={} idHotel={}",
          comando.commandId(),
          comando.tipo(),
          cola,
          comando.correlationId(),
          idHotel);
    } catch (NotificationPermanentException permanente) {
      log.warn(
          "notification_command_rejected cola={} commandId={} error={}",
          cola,
          commandIdDe(message),
          permanente.getMessage());
      aDlq(entrega, channel);
    } catch (Exception transitorio) {
      log.warn(
          "notification_command_retry cola={} commandId={} error={}",
          cola,
          commandIdDe(message),
          transitorio.getMessage());
      reintentarODlq(cola, message, channel, entrega);
    }
  }

  /** Rechazo sin requeue: la cola principal dead-lettera a {@code hotel.dlx.v1} → {@code <cola>.dlq}. */
  private void aDlq(long entrega, Channel channel) throws IOException {
    channel.basicNack(entrega, false, false);
  }

  private void reintentarODlq(String cola, Message message, Channel channel, long entrega)
      throws IOException {
    int fallidos = intentosFallidos(message);
    String reintento = RabbitRetryPolicy.colaDeReintento(cola, fallidos);
    if (reintento == null) {
      aDlq(entrega, channel);
      return;
    }
    try {
      rabbitTemplate.send("", reintento, conIntento(message, fallidos + 1));
      channel.basicAck(entrega, false);
    } catch (Exception ex) {
      log.error("notification_retry_publish_error cola={} error={}", cola, ex.getMessage());
      channel.basicNack(entrega, false, false);
    }
  }

  static Message conIntento(Message original, int intentos) {
    MessageProperties originales = original.getMessageProperties();
    MessageProperties copia = new MessageProperties();
    copia.setContentType(originales.getContentType());
    copia.setContentEncoding(originales.getContentEncoding());
    copia.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
    copia.setMessageId(originales.getMessageId());
    copia.setCorrelationId(originales.getCorrelationId());
    copia.setHeader("x-schema-version", originales.getHeaders().get("x-schema-version"));
    copia.setHeader(RabbitRetryPolicy.CABECERA_INTENTOS, intentos);
    return new Message(original.getBody(), copia);
  }

  static int intentosFallidos(Message message) {
    Object valor = message.getMessageProperties().getHeaders().get(RabbitRetryPolicy.CABECERA_INTENTOS);
    if (valor instanceof Number numero) {
      return numero.intValue();
    }
    if (valor instanceof String texto) {
      try {
        return Integer.parseInt(texto);
      } catch (NumberFormatException ex) {
        return 0;
      }
    }
    return 0;
  }

  static String commandIdDe(Message message) {
    Object id = message.getMessageProperties().getMessageId();
    if (id != null) {
      return String.valueOf(id);
    }
    try {
      return NotificationCommand.parse(
              new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8))
          .commandId()
          .toString();
    } catch (RuntimeException ex) {
      return "(sin commandId)";
    }
  }
}