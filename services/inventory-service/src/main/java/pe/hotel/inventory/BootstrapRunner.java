package pe.hotel.inventory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

  /** Serializador propio: el servicio no publica un bean {@code ObjectMapper} en el contenedor. */
  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired(required = false) private JdbcTemplate jdbc;
  @Autowired(required = false) private OutboxStore outboxStore;

  @Override
  public void run(ApplicationArguments args) {
    if (jdbc == null || outboxStore == null) {
      return;
    }
    String now = TS.format(Instant.now());
    List<Map<String, Object>> sedes =
        jdbc.queryForList(
            "SELECT ps.id_producto, ps.id_hotel, p.sku, p.nombre, ps.precio, ps.cantidad, "
                + "ps.umbral_bajo, (p.estado AND ps.estado) AS estado, ps.version "
                + "FROM producto_sede ps JOIN producto p ON p.id = ps.id_producto "
                + "ORDER BY ps.id_producto, ps.id_hotel");
    for (Map<String, Object> s : sedes) {
      Integer idProducto = ((Number) s.get("id_producto")).intValue();
      Integer idHotel = ((Number) s.get("id_hotel")).intValue();
      String aggregateId = idHotel + ":" + idProducto;
      String eventId = OutboxService.eventIdBootstrap("producto-sede", aggregateId);
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
      payload.put("nombreProducto", s.get("nombre"));
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
