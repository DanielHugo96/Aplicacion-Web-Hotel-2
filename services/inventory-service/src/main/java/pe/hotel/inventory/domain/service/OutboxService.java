package pe.hotel.inventory.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.messaging.Destination;
import pe.hotel.inventory.messaging.OutboxStore;

/**
 * Emite eventos de dominio al {@code outbox} en la misma transacción local de la mutación de
 * stock/catálogo. La plataforma (OutboxPoller + KafkaOutboxPublisher) se encarga de publicarlos al
 * broker.
 */
@Service
public class OutboxService {

  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

  @Autowired(required = false) private OutboxStore outboxStore;
  @Autowired(required = false) private ObjectMapper mapper;

  @Transactional
  public void emitirProductoStockSnapshot(
      Integer idHotel, Integer idProducto, String sku, String nombreProducto,
      Double precio, Integer cantidad, Integer umbralBajo, boolean estado, long version) {
    if (outboxStore == null || mapper == null) {
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idProducto", idProducto);
      payload.put("idHotel", idHotel);
      payload.put("sku", sku);
      payload.put("nombreProducto", nombreProducto);
      payload.put("precio", precio);
      payload.put("cantidad", cantidad);
      payload.put("umbralBajo", umbralBajo);
      payload.put("estado", estado);
      payload.put("version", version);

      Map<String, Object> envelope = sobre(
          "ProductoStockSnapshot", "producto-sede", idHotel, idProducto + ":" + idHotel, version, payload);
      outboxStore.enqueue(
          Destination.KAFKA,
          "inventory.stock.v1",
          idHotel + ":" + idProducto,
          idHotel + ":" + idProducto,
          version,
          "ProductoStockSnapshot",
          mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar ProductoStockSnapshot", ex);
    }
  }

  @Transactional
  public void emitirMovimientoStockResuelto(
      Integer idHotel, Integer idVenta, String estado, long version) {
    if (outboxStore == null || mapper == null) {
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idVenta", idVenta);
      payload.put("idHotel", idHotel);
      payload.put("estado", estado);
      payload.put("version", version);

      Map<String, Object> envelope =
          sobre("MovimientoStockResuelto", "movimiento-stock", idHotel, String.valueOf(idVenta), version, payload);
      outboxStore.enqueue(
          Destination.KAFKA,
          "inventory.movimiento.v1",
          String.valueOf(idVenta),
          String.valueOf(idVenta),
          version,
          "MovimientoStockResuelto",
          mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar MovimientoStockResuelto", ex);
    }
  }

  private Map<String, Object> sobre(
      String type, String aggregateType, Integer idHotel, String aggregateId, long aggregateVersion,
      Map<String, Object> payload) {
    String eventId = UUID.randomUUID().toString();
    String now = TS.format(Instant.now());
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", eventId);
    envelope.put("type", type);
    envelope.put("schemaVersion", 1);
    envelope.put("aggregateType", aggregateType);
    envelope.put("aggregateId", aggregateId);
    envelope.put("aggregateVersion", aggregateVersion);
    envelope.put("idHotel", idHotel);
    envelope.put("occurredAt", now);
    envelope.put("correlationId", eventId);
    envelope.put("payload", payload);
    return envelope;
  }

  public static String eventIdBootstrap(String aggregateType, Integer aggregateId) {
    return UUID.nameUUIDFromBytes(
            ("bootstrap:" + aggregateType + ":" + aggregateId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }
}
