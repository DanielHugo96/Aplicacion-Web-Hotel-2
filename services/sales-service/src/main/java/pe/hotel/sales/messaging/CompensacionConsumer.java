package pe.hotel.sales.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.sales.domain.model.OperacionRow;
import pe.hotel.sales.domain.model.VentaRow;
import pe.hotel.sales.domain.repository.SalesRepository;
import pe.hotel.sales.domain.service.SalesOutbox;

/**
 * Consumidor {@code sales-compensacion-v1} de {@code inventory.movimiento.v1} y
 * {@code reception.recepcion.v1}. El inbox y el efecto van en la misma transacción; un RESTITUIDO
 * completa la cancelación, un DESCONTADO tardío nunca confirma, y la salida limpia el carrito una
 * sola vez. Una contradicción de sede o tupla se deriva a DLT sin mover stock ajeno.
 */
@Service
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CompensacionConsumer {

  public static final String CONSUMER = "sales-compensacion-v1";
  public static final String TOPIC_MOVIMIENTO = "inventory.movimiento.v1";
  public static final String TOPIC_RECEPCION = "reception.recepcion.v1";

  private static final Logger log = LoggerFactory.getLogger(CompensacionConsumer.class);

  private final SalesRepository repo;
  private final InboxStore inbox;
  private final MessageValidator validator;
  private final SalesOutbox outbox;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;

  public CompensacionConsumer(
      SalesRepository repo,
      InboxStore inbox,
      MessageValidator validator,
      SalesOutbox outbox,
      ObjectMapper mapper,
      TransactionTemplate transactions) {
    this.repo = repo;
    this.inbox = inbox;
    this.validator = validator;
    this.outbox = outbox;
    this.mapper = mapper;
    this.transactions = transactions;
  }

  @KafkaListener(topics = {TOPIC_MOVIMIENTO, TOPIC_RECEPCION}, groupId = CONSUMER)
  public void consume(
      @Payload String body,
      @Header(name = KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
    try {
      transactions.executeWithoutResult(status -> process(body, topic, key));
    } catch (Exception error) {
      log.error("compensacion_error topic={} key={} error={}", topic, key, error.getMessage(), error);
      throw error;
    }
  }

  private void process(String body, String topic, String key) {
    JsonNode envelope;
    try {
      envelope = mapper.readTree(body);
    } catch (Exception invalid) {
      throw new MessageInvalidException("payload no es JSON válido", invalid);
    }
    String type = envelope.path("type").asText();
    validator.validate(
        new OutboxMessage(
            UUID.randomUUID(),
            Destination.KAFKA,
            topic,
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
    int idHotel = envelope.path("idHotel").asInt();
    // Los topics son compartidos: sales solo reacciona a los hechos que el catálogo le asigna y
    // descarta el resto sin efecto (CheckInRegistrado, RecepcionActualizada, snapshots ajenos).
    if ("MovimientoStockResuelto".equals(type)) {
      movimiento(payload, idHotel);
    } else if ("SalidaRegistrada".equals(type)) {
      salida(payload);
    }
  }

  private void movimiento(JsonNode payload, int idHotel) {
    int idVenta = payload.path("idVenta").asInt();
    long version = payload.path("version").asLong();
    String estado = payload.path("estado").asText();
    VentaRow venta = repo.venta(idVenta).orElse(null);
    if (venta == null) {
      log.warn("movimiento_venta_desconocida idVenta={} estado={}", idVenta, estado);
      return;
    }
    if (venta.idHotel() != idHotel || payload.path("idHotel").asInt() != idHotel) {
      throw new MessageInvalidException("contradicción de idHotel en movimiento " + idVenta);
    }
    switch (estado) {
      case "RESTITUIDO" -> {
        if ("CANCELACION_PENDIENTE".equals(venta.estadoOperacion())) {
          OperacionRow operacion = repo.operacionAbierta(idVenta).orElse(null);
          boolean completada = repo.completarCancelacion(idVenta, version);
          if (completada) {
            if (operacion != null) {
              repo.terminarOperacionSiCoincide(operacion.operationId(), idVenta);
            }
            VentaRow actual = repo.venta(idVenta).orElseThrow();
            outbox.emitirVenta(actual, repo.detalles(idVenta));
            outbox.emitirFinalizar(
                operacion == null ? UUID.nameUUIDFromBytes(("venta:" + idVenta).getBytes()) : operacion.operationId(),
                actual.idHotel(),
                actual.idRecepcion(),
                actual.id(),
                operacion == null ? "VENTA" : operacion.tipo(),
                "CANCELADA",
                actual.version());
          }
        } else if (!"CANCELADA".equals(venta.estadoOperacion())) {
          log.warn("restituido_sin_cancelacion idVenta={} estadoOperacion={}", idVenta, venta.estadoOperacion());
        }
      }
      case "DESCONTADO" -> {
        if ("CANCELACION_PENDIENTE".equals(venta.estadoOperacion())
            || "CANCELADA".equals(venta.estadoOperacion())) {
          log.error("alerta_descontado_tardio idVenta={} estadoOperacion={}", idVenta, venta.estadoOperacion());
        }
      }
      case "RECHAZADO" -> log.info("movimiento_rechazado idVenta={}", idVenta);
      default -> throw new MessageInvalidException("estado de movimiento no soportado: " + estado);
    }
  }

  private void salida(JsonNode payload) {
    int idRecepcion = payload.path("idRecepcion").asInt();
    int borradas = repo.limpiarCarrito(idRecepcion);
    log.info("carrito_limpiado_por_salida idRecepcion={} lineas={}", idRecepcion, borradas);
  }
}
