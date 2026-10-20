package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.reception.messaging.Destination;
import pe.hotel.reception.messaging.InboxStore;
import pe.hotel.reception.messaging.MessageInvalidException;
import pe.hotel.reception.messaging.MessageValidator;
import pe.hotel.reception.messaging.OutboxMessage;

/**
 * Consumidor de recepcion.operacion-finalizar (sales → reception). Inbox + lápida en la misma
 * transacción; errores permanentes o contradicciones van a la DLQ sin desbloquear otra venta. El
 * listener nunca consulta stock ni dinero. Ver docs/especificacion/05-mensajeria.md.
 */
@Service
@ConditionalOnProperty(prefix = "hotel.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperacionConsumer {

  public static final String QUEUE = "recepcion.operacion-finalizar";
  public static final String CONSUMER = "reception-operacion-finalizar-v1";

  private final ReceptionRepository repository;
  private final InboxStore inbox;
  private final MessageValidator validator;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;

  public OperacionConsumer(
      ReceptionRepository repository,
      InboxStore inbox,
      MessageValidator validator,
      ObjectMapper mapper,
      TransactionTemplate transactions) {
    this.repository = repository;
    this.inbox = inbox;
    this.validator = validator;
    this.mapper = mapper;
    this.transactions = transactions;
  }

  @RabbitListener(queues = QUEUE)
  public void consume(String body) {
    JsonNode envelope;
    try {
      envelope = mapper.readTree(body);
      String type = envelope.path("type").asText();
      validator.validate(
          new OutboxMessage(
              UUID.randomUUID(),
              Destination.RABBIT,
              QUEUE,
              null,
              QUEUE,
              0,
              type,
              body,
              Instant.now(),
              0,
              Instant.now()));
    } catch (Exception invalid) {
      throw new AmqpRejectAndDontRequeueException("comando inválido", invalid);
    }

    UUID commandId = UUID.fromString(envelope.path("commandId").asText());
    int idHotel = envelope.path("idHotel").asInt();
    JsonNode payload = envelope.path("payload");
    UUID operationId = UUID.fromString(payload.path("operationId").asText());
    int idRecepcion = payload.path("idRecepcion").asInt();
    int idVenta = payload.path("idVenta").asInt();
    String tipo = payload.path("tipo").asText();
    String resultado = payload.path("resultado").asText();

    Integer idHotelReal =
        repository.findById(idRecepcion).map(view -> view.idHotel()).orElse(null);
    if (idHotelReal == null || idHotelReal != idHotel) {
      throw new AmqpRejectAndDontRequeueException("tupla o idHotel contradictorio");
    }

    String error =
        transactions.execute(
            status -> {
              if (!inbox.claim(CONSUMER, commandId)) {
                return null;
              }
              return (String)
                  repository
                      .finalizar(operationId, idRecepcion, idVenta, tipo, resultado)
                      .get("code");
            });
    if (error != null) {
      // INVALID_RESULT, TUPLE_MISMATCH o CONTRADICTION: no cambia nada y se deriva a DLQ.
      throw new AmqpRejectAndDontRequeueException("finalización rechazada: " + error);
    }
  }
}
