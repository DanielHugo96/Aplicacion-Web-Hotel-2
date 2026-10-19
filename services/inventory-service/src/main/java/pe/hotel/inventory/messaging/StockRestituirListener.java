package pe.hotel.inventory.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pe.hotel.inventory.domain.service.SedeMismatchException;

/**
 * Consumo de {@code stock.restituir} (docs/especificacion/05-mensajeria.md § Reintentos y DLQ
 * Rabbit): valida el envelope contra su JSON Schema, registra el {@code commandId} en el inbox en
 * la misma transacción que el efecto sobre el stock y solo entonces confirma. Los errores anteriores
 * a la persistencia usan los reintentos TTL de Rabbit y luego la DLQ; los permanentes van directo a
 * la DLQ.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class StockRestituirListener {

  private static final Logger log = LoggerFactory.getLogger(StockRestituirListener.class);

  /** Nombre de la cola consumida (routing key en el exchange {@code hotel.commands.v1}). */
  public static final String COLA = "stock.restituir";

  /** Contador propio de intentos del mensaje (cabecera), independiente de {@code x-death}. */
  static final String CABECERA_INTENTOS = "x-inventory-attempts";

  private final StockRestituirHandler handler;
  private final MessageValidator validator;
  private final RabbitTemplate rabbitTemplate;

  public StockRestituirListener(
      StockRestituirHandler handler,
      MessageValidator validator,
      RabbitTemplate rabbitTemplate) {
    this.handler = handler;
    this.validator = validator;
    this.rabbitTemplate = rabbitTemplate;
  }

  @RabbitListener(queues = COLA, ackMode = "MANUAL")
  public void restituir(Message message, Channel channel) throws IOException {
    long entrega = message.getMessageProperties().getDeliveryTag();
    String cuerpo = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      StockRestituirCommand comando = StockRestituirCommand.parse(cuerpo);
      // Valida el envelope completo contra el schema v1 antes de tocar la base.
      OutboxMessage paraValidar =
          new OutboxMessage(
              UUID.randomUUID(),
              Destination.RABBIT,
              COLA,
              comando.commandId().toString(),
              comando.commandId().toString(),
              1L,
              "StockRestituir",
              cuerpo,
              java.time.Instant.now(),
              0,
              java.time.Instant.now());
      validator.validate(paraValidar);

      Integer idVenta = comando.idVenta();
      Integer idHotel = comando.idHotel();
      String motivo = comando.motivo();

      if (idHotel == null || idVenta == null || motivo == null || motivo.isBlank()) {
        throw new MessageInvalidException("comando sin idHotel, idVenta o motivo");
      }

      // Deduplicación por commandId y efecto en la misma transacción: si el efecto falla, el
      // registro del inbox revierte con él y el reintento vuelve a aplicar el comando.
      boolean aplicado = handler.aplicar(comando.commandId(), idVenta, idHotel, motivo);
      if (!aplicado) {
        log.info(
            "stock_restituir_duplicado commandId={} idVenta={} correlationId={}",
            comando.commandId(),
            idVenta,
            comando.correlationId());
        channel.basicAck(entrega, false);
        return;
      }

      channel.basicAck(entrega, false);
      log.info(
          "stock_restituido commandId={} idVenta={} idHotel={} correlationId={} motivo={}",
          comando.commandId(),
          idVenta,
          idHotel,
          comando.correlationId(),
          motivo);
    } catch (MessageInvalidException permanente) {
      log.warn(
          "stock_restituir_rechazado commandId={} error={}",
          messageIdDe(message),
          permanente.getMessage());
      aDlq(entrega, channel);
    } catch (SedeMismatchException contradiccion) {
      // Contradicción de sede con el movimiento existente: DLQ con alerta, sin mover stock ajeno.
      log.error(
          "stock_restituir_sede_mismatch commandId={} error={}",
          messageIdDe(message),
          contradiccion.getMessage());
      aDlq(entrega, channel);
    } catch (Exception transitorio) {
      log.warn(
          "stock_restituir_reintento commandId={} error={}",
          messageIdDe(message),
          transitorio.getMessage());
      reintentarODlq(message, channel, entrega);
    }
  }

  /** Rechazo sin requeue: la cola principal dead-lettera a {@code hotel.dlx.v1} → {@code <cola>.dlq}. */
  private void aDlq(long entrega, Channel channel) throws IOException {
    channel.basicNack(entrega, false, false);
  }

  private void reintentarODlq(Message message, Channel channel, long entrega) throws IOException {
    int fallidos = intentosFallidos(message);
    String reintento = RabbitRetryPolicy.colaDeReintento(COLA, fallidos);
    if (reintento == null) {
      aDlq(entrega, channel);
      return;
    }
    try {
      rabbitTemplate.send("", reintento, conIntento(message, fallidos + 1));
      channel.basicAck(entrega, false);
    } catch (Exception ex) {
      log.error("stock_restituir_reintento_publish_error error={}", ex.getMessage());
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
