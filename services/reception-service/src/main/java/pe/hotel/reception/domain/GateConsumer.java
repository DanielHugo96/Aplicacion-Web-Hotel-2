package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.reception.messaging.Destination;
import pe.hotel.reception.messaging.InboxStore;
import pe.hotel.reception.messaging.MessageInvalidException;
import pe.hotel.reception.messaging.MessageValidator;
import pe.hotel.reception.messaging.OutboxMessage;

/**
 * Consumidor reception-gates-v1 de hotel.habitacion.v1: inicializa gates, actualiza hotel_version y
 * libera solo con correlación de ciclo/bloqueo. El inbox y el efecto van en la misma transacción;
 * el ack se confirma después del commit. Ver docs/especificacion/05-mensajeria.md.
 */
@Service
@ConditionalOnProperty(prefix = "hotel.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GateConsumer {

  public static final String CONSUMER = "reception-gates-v1";
  public static final String TOPIC = "hotel.habitacion.v1";
  public static final String DLT = TOPIC + "." + CONSUMER + ".dlt";

  private static final Logger log = LoggerFactory.getLogger(GateConsumer.class);

  private final ReceptionRepository repository;
  private final InboxStore inbox;
  private final MessageValidator validator;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;

  public GateConsumer(
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

  @KafkaListener(topics = TOPIC, groupId = CONSUMER)
  public void consume(
      @Payload String body,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
    try {
      transactions.executeWithoutResult(status -> process(body, key));
    } catch (Exception error) {
      log.error("gate_consumer_error key={} error={}", key, error.getMessage(), error);
      throw error;
    }
  }

  private void process(String body, String key) {
    JsonNode envelope;
    try {
      envelope = mapper.readTree(body);
    } catch (Exception e) {
      throw new MessageInvalidException("payload no es JSON válido", e);
    }
    String type = envelope.path("type").asText();
    validator.validate(
        new OutboxMessage(
            UUID.randomUUID(),
            Destination.KAFKA,
            TOPIC,
            key,
            key,
            envelope.path("aggregateVersion").asLong(),
            type,
            body,
            Instant.now(),
            0,
            Instant.now()));

    UUID eventId = UUID.fromString(envelope.path("eventId").asText());
    if (!inbox.claim(CONSUMER, eventId)) {
      return;
    }

    JsonNode payload = envelope.path("payload");
    int idHabitacion = payload.path("idHabitacion").asInt();
    int idHotel = payload.path("idHotel").asInt();
    long hotelVersion = payload.path("version").asLong();

    switch (type) {
      case "HabitacionCreada" -> {
        boolean admisible =
            payload.path("estado").asBoolean(false)
                && "LISTA".equals(payload.path("estadoFisico").asText());
        if (admisible) {
          repository.inicializarGate(idHabitacion, idHotel, hotelVersion);
        } else {
          UUID blockId =
              UUID.nameUUIDFromBytes(
                  ("bootstrap:habitacion:" + idHabitacion).getBytes(StandardCharsets.UTF_8));
          repository.bootstrapBloqueado(
              idHabitacion,
              idHotel,
              hotelVersion,
              blockId,
              ReceptionRepository.hash("bootstrap:" + idHabitacion));
        }
      }
      case "HabitacionSnapshot" -> repository.snapshotGate(idHabitacion, idHotel, hotelVersion);
      case "HabitacionLista" -> {
        String ciclo = payload.path("cleaningCycleId").asText("");
        if (ciclo.isBlank()) {
          throw new MessageInvalidException("HabitacionLista sin cleaningCycleId");
        }
        repository.liberarCiclo(idHabitacion, UUID.fromString(ciclo), hotelVersion);
      }
      case "HabitacionHabilitada" -> {
        String block = payload.path("blockId").asText("");
        if (block.isBlank()) {
          throw new MessageInvalidException("HabitacionHabilitada sin blockId");
        }
        repository.liberarBloqueo(idHabitacion, UUID.fromString(block), hotelVersion);
      }
      default -> throw new MessageInvalidException("tipo no soportado: " + type);
    }
  }
}
