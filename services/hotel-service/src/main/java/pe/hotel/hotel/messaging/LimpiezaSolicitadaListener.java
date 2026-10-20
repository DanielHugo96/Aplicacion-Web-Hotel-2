package pe.hotel.hotel.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pe.hotel.hotel.domain.service.LimpiezaService;

/**
 * Consumidor Rabbit de {@code limpieza.solicitada} (05 § Reintentos y DLQ Rabbit): valida el
 * envelope y el schema, aplica inbox + efecto en una transacción y solo entonces confirma. Los
 * errores permanentes van directo a la DLQ; los transitorios pasan por las colas TTL de reintento.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LimpiezaSolicitadaListener {

  public static final String COLA = "limpieza.solicitada";
  public static final String DLQ = COLA + ".dlq";
  static final String CABECERA_INTENTOS = "x-hotel-limpieza-attempts";

  private static final Logger log = LoggerFactory.getLogger(LimpiezaSolicitadaListener.class);

  private final LimpiezaService service;
  private final MessageValidator validator;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public LimpiezaSolicitadaListener(
      LimpiezaService service, MessageValidator validator, RabbitTemplate rabbitTemplate) {
    this.service = service;
    this.validator = validator;
    this.rabbitTemplate = rabbitTemplate;
  }

  @RabbitListener(queues = COLA, ackMode = "MANUAL")
  public void consumir(Message message, Channel channel) throws IOException {
    long entrega = message.getMessageProperties().getDeliveryTag();
    String cuerpo = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      JsonNode envelope = mapper.readTree(cuerpo);
      validator.validate(
          new OutboxMessage(
              UUID.randomUUID(),
              Destination.RABBIT,
              COLA,
              null,
              COLA,
              0,
              envelope.path("type").asText(),
              cuerpo,
              Instant.now(),
              0,
              Instant.now()));
      if (!"LimpiezaSolicitada".equals(envelope.path("type").asText())) {
        throw new MessageInvalidException("tipo no soportado: " + envelope.path("type").asText());
      }
      UUID commandId = UUID.fromString(envelope.path("commandId").asText());
      int idHotel = envelope.path("idHotel").asInt();
      JsonNode payload = envelope.path("payload");
      int idHabitacion = payload.path("idHabitacion").asInt();
      int idRecepcion = payload.path("idRecepcion").asInt();
      UUID ciclo = UUID.fromString(payload.path("cleaningCycleId").asText());
      long roomVersion = payload.path("roomVersion").asLong();

      LimpiezaService.ResultadoSolicitud resultado =
          service.solicitar(commandId, idHotel, idHabitacion, idRecepcion, ciclo, roomVersion);
      if (resultado.code() != null) {
        // Contradicción permanente (tupla, habitación inexistente u operación vigente): DLQ.
        throw new MessageInvalidException(
            "comando rechazado: " + resultado.code() + " " + (resultado.detalle() == null ? "" : resultado.detalle()));
      }
      channel.basicAck(entrega, false);
      log.info(
          "limpieza_solicitada_procesada commandId={} idHabitacion={} ciclo={} duplicado={}",
          commandId,
          idHabitacion,
          ciclo,
          !resultado.aplicado());
    } catch (MessageInvalidException permanente) {
      log.warn(
          "limpieza_solicitada_dlq commandId={} error={}",
          messageIdDe(message),
          permanente.getMessage());
      channel.basicNack(entrega, false, false);
    } catch (IllegalArgumentException invalido) {
      log.warn(
          "limpieza_solicitada_dlq commandId={} error={}",
          messageIdDe(message),
          invalido.getMessage());
      channel.basicNack(entrega, false, false);
    } catch (Exception transitorio) {
      log.warn(
          "limpieza_solicitada_reintento commandId={} error={}",
          messageIdDe(message),
          transitorio.getMessage());
      reintentarODlq(message, channel, entrega);
    }
  }

  private void reintentarODlq(Message message, Channel channel, long entrega) throws IOException {
    int fallidos = intentosFallidos(message);
    String reintento = colaDeReintento(fallidos);
    if (reintento == null) {
      channel.basicNack(entrega, false, false);
      return;
    }
    try {
      rabbitTemplate.send("", reintento, conIntento(message, fallidos + 1));
      channel.basicAck(entrega, false);
    } catch (Exception ex) {
      log.error("limpieza_solicitada_reintento_publish_error error={}", ex.getMessage());
      channel.basicNack(entrega, false, false);
    }
  }

  static String colaDeReintento(int fallidos) {
    return switch (fallidos) {
      case 0 -> COLA + ".retry.5s";
      case 1 -> COLA + ".retry.30s";
      case 2 -> COLA + ".retry.120s";
      default -> null;
    };
  }

  static Message conIntento(Message original, int intentos) {
    MessageProperties originales = original.getMessageProperties();
    MessageProperties copia = new MessageProperties();
    copia.setContentType(originales.getContentType());
    copia.setContentEncoding(originales.getContentEncoding());
    copia.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
    copia.setMessageId(originales.getMessageId());
    copia.setCorrelationId(originales.getCorrelationId());
    copia.setHeader(CABECERA_INTENTOS, intentos);
    return new Message(original.getBody(), copia);
  }

  static int intentosFallidos(Message message) {
    Object valor = message.getMessageProperties().getHeaders().get(CABECERA_INTENTOS);
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

  static String messageIdDe(Message message) {
    Object id = message.getMessageProperties().getMessageId();
    return id != null ? String.valueOf(id) : "(sin messageId)";
  }
}
