package pe.hotel.inventory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.hotel.inventory.domain.service.OutboxService;
import pe.hotel.inventory.messaging.Destination;
import pe.hotel.inventory.messaging.OutboxStore;

/**
 * Bootstrap de dev: publica los 9 {@code ProductoStockSnapshot} iniciales (uno por producto/sede
 * sembrada) de forma idempotente, con {@code eventId} estable para que el inbox de reporting los
 * deduplique ante reenvíos. Solo en perfil {@code dev}/{@code demo}.
 */
@Component
@Profile({"dev", "demo"})
public class BootstrapRunner implements ApplicationRunner {

  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

  @Autowired(required = false) private JdbcTemplate jdbc;
  @Autowired(required = false) private OutboxStore outboxStore;
  @Autowired(required = false) private ObjectMapper mapper;

  @Override
  public void run(ApplicationArguments args) {
    if (jdbc == null || outboxStore == null || mapper == null) {
      return;
    }
    String now = TS.format(Instant.now());
    List<Map<String, Object>> sedes =
        jdbc.queryForList(
            "SELECT id_producto, id_hotel, sku, precio, cantidad, umbral_bajo, estado, version "
                + "FROM producto_sede ORDER BY id_producto, id_hotel");
    for (Map<String, Object> s : sedes) {
      Integer idProducto = ((Number) s.get("id_producto")).intValue();
      Integer idHotel = ((Number) s.get("id_hotel")).intValue();
      String aggregateId = idHotel + ":" + idProducto;
      String eventId =
          UUID.nameUUIDFromBytes(
                  ("bootstrap:producto-sede:" + aggregateId)
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString();
      Boolean yaExiste =
          jdbc.queryForObject(
              "SELECT count(*) > 0 FROM outbox WHERE channel = ? AND destination = ? AND aggregate_id = ? AND type = ?",
              Boolean.class,
              "inventory.stock.v1",
              Destination.KAFKA.name(),
              aggregateId,
              "ProductoStockSnapshot");
      if (Boolean.TRUE.equals(yaExiste)) {
        continue;
      }
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("idProducto", idProducto);
      payload.put("idHotel", idHotel);
      payload.put("sku", s.get("sku"));
      payload.put("nombreProducto", s.get("sku"));
      payload.put("precio", new java.math.BigDecimal(s.get("precio").toString()).doubleValue());
      payload.put("cantidad", ((Number) s.get("cantidad")).intValue());
      payload.put("umbralBajo", ((Number) s.get("umbral_bajo")).intValue());
      payload.put("estado", s.get("estado"));
      payload.put("version", ((Number) s.get("version")).intValue());

      Map<String, Object> envelope = new java.util.LinkedHashMap<>();
      envelope.put("eventId", eventId);
      envelope.put("type", "ProductoStockSnapshot");
      envelope.put("schemaVersion", 1);
      envelope.put("aggregateType", "producto-sede");
      envelope.put("aggregateId", aggregateId);
      envelope.put("aggregateVersion", ((Number) s.get("version")).intValue());
      envelope.put("idHotel", idHotel);
      envelope.put("occurredAt", now);
      envelope.put("correlationId", eventId);
      envelope.put("payload", payload);

      try {
        outboxStore.enqueue(
            Destination.KAFKA,
            "inventory.stock.v1",
            aggregateId,
            aggregateId,
            ((Number) s.get("version")).longValue(),
            "ProductoStockSnapshot",
            mapper.writeValueAsString(envelope));
      } catch (Exception ex) {
        throw new IllegalStateException("no se pudo serializar el snapshot de bootstrap", ex);
      }
    }
  }
}
