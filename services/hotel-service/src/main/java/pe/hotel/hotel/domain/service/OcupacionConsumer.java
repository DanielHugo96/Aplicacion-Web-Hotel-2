package pe.hotel.hotel.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Consume {@code reception.recepcion.v1} con el grupo {@code hotel-ocupacion-v1} y actualiza
 * {@code ocupacion_projection} (solo versiones mayores; inbox en la misma transaccion del efecto).
 * Payloads invalidos van a la DLT {@code reception.recepcion.v1.hotel-ocupacion-v1.dlt}.
 */
@Service
public class OcupacionConsumer {

  private static final String CONSUMER = "hotel-ocupacion-v1";
  private static final String TOPIC = "reception.recepcion.v1";
  private static final String DLT = TOPIC + "." + CONSUMER + ".dlt";

  @Autowired(required = false)
  private JdbcTemplate jdbcTemplate;
  @Autowired(required = false)
  private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired(required = false)
  private ObjectMapper mapper;

  @KafkaListener(topics = TOPIC, groupId = CONSUMER)
  @Transactional
  public void consume(
      ConsumerRecord<String, String> record, Acknowledgment acknowledgment, @Header Map<String, Object> headers) {
    if (jdbcTemplate == null || mapper == null || kafkaTemplate == null) {
      acknowledgment.acknowledge();
      return;
    }
    try {
      JsonNode envelope = mapper.readTree(record.value());
      String eventId = envelope.path("eventId").asText();
      String type = envelope.path("type").asText();
      JsonNode payload = envelope.path("payload");

      Integer yaProcesado =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM inbox WHERE consumer = ? AND message_id = ?",
              Integer.class,
              CONSUMER,
              UUID.fromString(eventId));
      if (yaProcesado > 0) {
        acknowledgment.acknowledge();
        return;
      }

      Integer idHabitacion = payload.path("idHabitacion").asInt();
      Long roomVersion = envelope.path("aggregateVersion").asLong();
      String estadoEstadia = determinarEstado(type, payload);
      Integer idRecepcion = payload.path("idRecepcion") == null ? null : payload.path("idRecepcion").asInt();

      Integer actualizados =
          jdbcTemplate.update(
              "UPDATE ocupacion_projection SET estado_estadia = ?, id_recepcion = ?, room_version = ?, updated_at = ? "
                  + "WHERE id_habitacion = ? AND ? > room_version",
              estadoEstadia,
              idRecepcion,
              roomVersion,
              Instant.now(),
              idHabitacion,
              roomVersion);
      if (actualizados == 0) {
        Integer existente =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ocupacion_projection WHERE id_habitacion = ?",
                Integer.class,
                idHabitacion);
        if (existente == 0) {
          jdbcTemplate.update(
              "INSERT INTO ocupacion_projection (id_habitacion, estado_estadia, id_recepcion, room_version, updated_at) "
                  + "VALUES (?, ?, ?, ?, ?)",
              idHabitacion,
              estadoEstadia,
              idRecepcion,
              roomVersion,
              Instant.now());
        }
      }

      jdbcTemplate.update(
          "INSERT INTO inbox (consumer, message_id, processed_at) VALUES (?, ?, ?)",
          CONSUMER,
          UUID.fromString(eventId),
          Instant.now());

      acknowledgment.acknowledge();
    } catch (Exception ex) {
      try {
        kafkaTemplate.send(DLT, record.key(), record.value()).get();
      } catch (Exception sendEx) {
        // no bloquear el consumer por un fallo de DLT
      }
      acknowledgment.acknowledge();
    }
  }

  private String determinarEstado(String type, JsonNode payload) {
    switch (type) {
      case "CheckInRegistrado":
        return "OCUPADA";
      case "RecepcionActualizada":
        return payload.has("estadoEstadia") && !payload.path("estadoEstadia").isNull()
            ? payload.path("estadoEstadia").asText()
            : "OCUPADA";
      case "SalidaRegistrada":
        return null;
      default:
        return "DESCONOCIDA";
    }
  }
}
