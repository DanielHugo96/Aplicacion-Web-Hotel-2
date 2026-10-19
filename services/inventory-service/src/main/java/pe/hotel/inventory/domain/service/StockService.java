package pe.hotel.inventory.domain.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.domain.dto.Debito;
import pe.hotel.inventory.domain.dto.DebitoResultado;
import pe.hotel.inventory.domain.dto.DebitoResultadoItem;
import pe.hotel.inventory.domain.dto.DebitoItem;
import pe.hotel.inventory.domain.dto.MovimientoResumen;
import pe.hotel.inventory.platform.ErrorCode;

/**
 * Descuento y restitución de stock. Toda la lógica de stock vive en funciones SQL
 * ({@code fn_stock_descontar}, {@code sp_stock_restituir}) con locks ordenados, para garantizar
 * atomicidad y serialización bajo concurrencia (docs/especificacion/microservicios/inventory/
 * base-de-datos.md #Funciones/procedimientos).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

  private final JdbcTemplate jdbc;
  private final OutboxService outbox;

  public StockService(JdbcTemplate jdbc, OutboxService outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  /**
   * Descuenta el stock de una venta de forma atómica. Devuelve el resultado sin lanzar excepción
   * para los rechazos de negocio (se persisten como movimiento RECHAZADO y la transacción confirma),
   * de modo que la evidencia del rechazo sobreviva al 409.
   */
  @Transactional
  public DescontarResultado descontar(Debito debito) {
    String itemsJson = itemsJson(debito.items());
    String hash = sha256(debito.idVenta(), debito.idHotel(), itemsJson);

    Map<String, Object> resultado =
        jdbc.queryForObject(
            "SELECT fn_stock_descontar(CAST(? AS integer), CAST(? AS integer), CAST(? AS jsonb), CAST(? AS char(64))) AS resultado",
            (rs, rowNum) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("estado", rs.getString("estado"));
              m.put("resultadoJson", rs.getString("resultado"));
              return m;
            },
            debito.idVenta(),
            debito.idHotel(),
            itemsJson,
            hash);

    String estado = (String) resultado.get("estado");
    String resultadoJson = (String) resultado.get("resultadoJson");

    // Evento de resolución en la misma transacción que el efecto sobre el stock.
    if ("DESCONTADO".equals(estado) || "RECHAZADO".equals(estado)) {
      outbox.emitirMovimientoStockResuelto(debito.idHotel(), debito.idVenta(), estado, 1L);
    }

    switch (estado) {
      case "DESCONTADO" -> {
        return new DescontarResultado(
            Estado.DESCONTADO, parseDebitoResultado(resultadoJson), null, null);
      }
      case "RECHAZADO" -> {
        ErrorCode code = codigoRechazo(resultadoJson);
        return new DescontarResultado(Estado.RECHAZADO, null, code, camposRechazo(resultadoJson));
      }
      case "IDEMPOTENCY_CONFLICT" -> {
        return new DescontarResultado(
            Estado.IDEMPOTENCY_CONFLICT, null, ErrorCode.IDEMPOTENCY_CONFLICT, null);
      }
      default -> {
        return new DescontarResultado(
            Estado.RECHAZADO,
            null,
            ErrorCode.INTERNAL_ERROR,
            Map.of("resultado", "estado inesperado del débito"));
      }
    }
  }

  /** Restitución idempotente de una venta: devuelve cantidades una sola vez o crea lápida. */
  @Transactional
  public void restituir(Integer idVenta, Integer idHotel, String motivo) {
    try (java.sql.Connection con = jdbc.getDataSource().getConnection();
        java.sql.CallableStatement cs = con.prepareCall("CALL sp_stock_restituir(?, ?, ?)")) {
      cs.setInt(1, idVenta);
      cs.setInt(2, idHotel);
      cs.setString(3, motivo);
      cs.execute();
    } catch (java.sql.SQLException ex) {
      throw new IllegalStateException("no se pudo ejecutar la restitución", ex);
    }
  }

  /** Lectura del movimiento de stock de una venta (consulta interna por idVenta). */
  @Transactional(readOnly = true)
  public MovimientoResumen movimientoPorVenta(Integer idVenta) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT id_venta AS idVenta, id_hotel AS idHotel, estado, resultado, version "
                + "FROM movimiento_venta WHERE id_venta = ?",
            (rs, rowNum) -> {
              Map<String, Object> m = new java.util.LinkedHashMap<>();
              m.put("idVenta", rs.getInt("id_venta"));
              m.put("idHotel", rs.getInt("id_hotel"));
              m.put("estado", rs.getString("estado"));
              m.put("resultado", rs.getObject("resultado"));
              m.put("version", rs.getInt("version"));
              return m;
            },
            idVenta);
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> m = rows.get(0);
    return new MovimientoResumen(
        (Integer) m.get("idVenta"),
        (Integer) m.get("idHotel"),
        (String) m.get("estado"),
        m.get("resultado"),
        (Integer) m.get("version"));
  }

  /** Ajuste manual de stock bajo lock; saldo nunca negativo. */
  @Transactional
  public Integer ajustar(
      java.util.UUID id, Integer idProducto, Integer idHotel, Integer delta, String motivo,
      Integer actorId) {
    return jdbc.queryForObject(
        "SELECT fn_stock_ajustar(CAST(? AS uuid), ?, ?, ?, ?, ?)",
        Integer.class,
        id,
        idProducto,
        idHotel,
        delta,
        motivo,
        actorId);
  }

  // ------------------------------------------------------------------
  private String itemsJson(List<DebitoItem> items) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(",");
      DebitoItem it = items.get(i);
      sb.append("{\"idProducto\":").append(it.idProducto()).append(",\"cantidad\":").append(it.cantidad()).append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  private String sha256(Integer idVenta, Integer idHotel, String itemsJson) {
    String canonical = idVenta + "|" + idHotel + "|" + itemsJson;
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(d.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 no disponible", ex);
    }
  }

  private DebitoResultado parseDebitoResultado(String json) {
    // El JSON de resultado de fn_stock_descontar ya tiene la forma de DebitoResultado.
    // Se reutiliza parseando a mapa y construyendo el DTO.
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      Map<String, Object> m = mapper.readValue(json, Map.class);
      List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
      List<DebitoResultadoItem> itemDtos =
          items.stream()
              .map(
                  it ->
                      new DebitoResultadoItem(
                          ((Number) it.get("idProducto")).intValue(),
                          (String) it.get("nombreProducto"),
                          ((Number) it.get("cantidad")).intValue(),
                          new BigDecimal(it.get("precioUnitario").toString()),
                          new BigDecimal(it.get("subTotal").toString())))
              .toList();
      return new DebitoResultado(
          ((Number) m.get("idVenta")).intValue(),
          ((Number) m.get("idHotel")).intValue(),
          (String) m.get("estado"),
          (String) m.get("moneda"),
          new BigDecimal(m.get("total").toString()),
          itemDtos,
          ((Number) m.get("version")).intValue());
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo parsear el resultado del débito", ex);
    }
  }

  private ErrorCode codigoRechazo(String resultadoJson) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      Map<String, Object> m = mapper.readValue(resultadoJson, Map.class);
      String motivo = (String) m.getOrDefault("motivo", "");
      if (motivo.contains("inactivo")) {
        return ErrorCode.PRODUCT_INACTIVE;
      }
      if (motivo.contains("cancelado")) {
        return ErrorCode.DEBIT_CANCELLED;
      }
      return ErrorCode.STOCK_INSUFFICIENT;
    } catch (Exception ex) {
      return ErrorCode.STOCK_INSUFFICIENT;
    }
  }

  private Map<String, String> camposRechazo(String resultadoJson) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      Map<String, Object> m = mapper.readValue(resultadoJson, Map.class);
      String motivo = (String) m.getOrDefault("motivo", "");
      Map<String, String> fields = new LinkedHashMap<>();
      if (motivo.contains("duplicad")) {
        fields.put("items", "ítems de producto duplicados");
      } else if (motivo.contains("insuficiente")) {
        fields.put("cantidad", "stock insuficiente");
      } else if (motivo.contains("inactivo")) {
        fields.put("idProducto", "producto o sede inactivos");
      } else if (motivo.contains("cancelado")) {
        fields.put("idVenta", "débito cancelado por restitución previa");
      }
      return fields;
    } catch (Exception ex) {
      return Map.of();
    }
  }

  public enum Estado {
    DESCONTADO,
    RECHAZADO,
    IDEMPOTENCY_CONFLICT
  }

  public record DescontarResultado(
      Estado estado, DebitoResultado resultado, ErrorCode errorCode, Map<String, String> fields) {}
}
