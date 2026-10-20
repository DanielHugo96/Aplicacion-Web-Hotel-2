package pe.hotel.reporting.projection;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Los seis listeners del grupo {@code reporting-proyeccion-v1}
 * (docs/especificacion/05-mensajeria.md § Topics y asignación exacta). No existe listener para
 * {@code inventory.movimiento.v1}: la compensación de ventas no se proyecta aquí.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProjectionConsumer {

  private final ProjectionService service;
  private final ProjectionStore store;

  public ProjectionConsumer(ProjectionService service, ProjectionStore store) {
    this.service = service;
    this.store = store;
  }

  @KafkaListener(
      topics = ReportingTopics.PERSONA,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void persona(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.PERSONA,
        record.partition(),
        record.offset(),
        record.value(),
        (envelope, version) -> store.upsertPersona(envelope.path("payload"), version));
  }

  @KafkaListener(
      topics = ReportingTopics.SEDE,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void sede(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.SEDE,
        record.partition(),
        record.offset(),
        record.value(),
        (envelope, version) -> store.upsertHotel(envelope.path("payload"), version));
  }

  @KafkaListener(
      topics = ReportingTopics.HABITACION,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void habitacion(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.HABITACION,
        record.partition(),
        record.offset(),
        record.value(),
        this::applyHabitacion);
  }

  private void applyHabitacion(JsonNode envelope, long version) {
    JsonNode payload = envelope.path("payload");
    String type = envelope.path("type").asText();
    switch (type) {
      case "HabitacionCreada", "HabitacionSnapshot" -> {
        store.upsertHabitacion(payload, version);
        store.initGateFromHotel(payload, type);
      }
      case "HabitacionLista" -> store.applyLimpiezaCompletada(payload);
      case "HabitacionHabilitada" -> store.applyHabilitacion(payload);
      default -> {
        // El esquema ya garantiza que el tipo pertenece al topic.
      }
    }
  }

  @KafkaListener(
      topics = ReportingTopics.RECEPCION,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void recepcion(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.RECEPCION,
        record.partition(),
        record.offset(),
        record.value(),
        (envelope, version) -> {
          JsonNode payload = envelope.path("payload");
          // recepcion_projection.source_version es la versión de la estadía (payload.version), no
          // el roomVersion del envelope (docs/especificacion/microservicios/reporting/base-de-datos.md
          // § Tablas de proyección). El gate sí se aplica por roomVersion.
          store.upsertRecepcion(payload, payload.path("version").asLong());
          store.applyGateFromRecepcion(payload);
        });
  }

  @KafkaListener(
      topics = ReportingTopics.STOCK,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void stock(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.STOCK,
        record.partition(),
        record.offset(),
        record.value(),
        (envelope, version) -> store.upsertStock(envelope.path("payload"), version));
  }

  @KafkaListener(
      topics = ReportingTopics.VENTA,
      groupId = "${hotel.messaging.kafka.consumer-group:reporting-proyeccion-v1}")
  public void venta(ConsumerRecord<String, String> record) {
    service.handle(
        ReportingTopics.VENTA,
        record.partition(),
        record.offset(),
        record.value(),
        (envelope, version) -> store.upsertVenta(envelope.path("payload"), version));
  }
}
