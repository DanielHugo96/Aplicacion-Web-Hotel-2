package pe.hotel.inventory.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.domain.dto.Debito;
import pe.hotel.inventory.domain.dto.DebitoItem;
import pe.hotel.inventory.domain.dto.DebitoResultado;
import pe.hotel.inventory.domain.dto.DebitoResultadoItem;
import pe.hotel.inventory.domain.dto.MovimientoResumen;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;

/**
 * Descuento y restitución de stock. Toda la lógica de stock vive en funciones SQL
 * ({@code fn_stock_descontar}, {@code sp_stock_restituir}) con locks ordenados, para garantizar
 * atomicidad y serialización bajo concurrencia (docs/especificacion/microservicios/inventory/
 * base-de-datos.md #Funciones/procedimientos).
 *
 * <p>Los eventos de resolución ({@code MovimientoStockResuelto}) y los snapshots de stock afectados
 * se encolan en el outbox dentro de la misma transacción que el efecto, y solo cuando la llamada
 * cambió realmente el estado del movimiento (un reintento idempotente no republica).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final OutboxService outbox;

  public StockService(JdbcTemplate jdbc, OutboxService outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  /**
   * Descuenta el stock de una venta de forma atómica. Devuelve el resultado sin lanzar excepción
   * para los rechazos de negocio (se persisten como movimiento RECHAZADO y la transacción confirma),
   * de modo que la evidencia del rechazo sobreviva al 409. Los ítems duplicados se rechazan con 400
   * antes de tocar SQL.
   */
  @Transactional
  public DescontarResultado descontar(Debito debito) {
    validarItems(debito.items());
    String itemsJson = itemsJson(debito.items());
    String hash = sha256(debito.idVenta(), debito.idHotel(), itemsJson);

    String estadoAntes = estadoDeMovimiento(debito.idVenta());
    String json =
        jdbc.queryForObject(
            "SELECT fn_stock_descontar(CAST(? AS integer), CAST(? AS integer), CAST(? AS jsonb), CAST(? AS char(64)))::text",
            String.class,
            debito.idVenta(),
            debito.idHotel(),
            itemsJson,
            hash);
    Map<String, Object> m = parse(json);
    String estado = (String) m.get("estado");

    // Solo una aplicación real del débito publica; un reintento idempotente no.
    if (!Objects.equals(estadoAntes, estadoDeMovimiento(debito.idVenta()))
        && ("DESCONTADO".equals(estado) || "RECHAZADO".equals(estado))) {
      outbox.emitirMovimientoStockResuelto(
          debito.idHotel(), debito.idVenta(), estado, numero(m.get("version"), 1L));
      if ("DESCONTADO".equals(estado)) {
        emitirSnapshots(
            debito.idHotel(),
            debito.items().stream().map(DebitoItem::idProducto).distinct().toList());
      }
    }

    return switch (estado) {
      case "DESCONTADO" ->
          new DescontarResultado(
              Estado.DESCONTADO,
              parseDebitoResultado(json, debito.idVenta(), debito.idHotel()),
              null,
              null);
      case "RECHAZADO" -> {
        ErrorCode code = codigoRechazo(json);
        yield new DescontarResultado(Estado.RECHAZADO, null, code, camposRechazo(json));
      }
      case "IDEMPOTENCY_CONFLICT" ->
          new DescontarResultado(
              Estado.IDEMPOTENCY_CONFLICT, null, ErrorCode.IDEMPOTENCY_CONFLICT, null);
      default ->
          new DescontarResultado(
              Estado.RECHAZADO,
              null,
              ErrorCode.INTERNAL_ERROR,
              Map.of("resultado", "estado inesperado del débito"));
    };
  }

  /**
   * Restitución idempotente de una venta: devuelve cantidades una sola vez o crea lápida. Corre en
   * la transacción del llamador (el consumidor de {@code stock.restituir}), emite el evento de
   * resolución {@code MovimientoStockResuelto} y los snapshots de las existencias devueltas en la
   * misma transacción, antes del ack. Una contradicción de {@code idHotel} con el movimiento
   * existente no toca stock ajeno: lanza {@link SedeMismatchException} para que el consumidor la
   * lleve a DLQ.
   */
  @Transactional
  public void restituir(Integer idVenta, Integer idHotel, String motivo) {
    String estadoAntes = estadoDeMovimientoConLock(idVenta);
    if (estadoAntes != null) {
      Integer idHotelMovimiento = idHotelDeMovimiento(idVenta);
      if (idHotelMovimiento != null && !idHotelMovimiento.equals(idHotel)) {
        throw new SedeMismatchException(idVenta, idHotelMovimiento, idHotel);
      }
    }

    jdbc.execute(
        (java.sql.Connection con) -> {
          try (java.sql.CallableStatement cs = con.prepareCall("CALL sp_stock_restituir(?, ?, ?)")) {
            cs.setInt(1, idVenta);
            cs.setInt(2, idHotel);
            cs.setString(3, motivo);
            cs.execute();
          }
          return null;
        });

    List<Map<String, Object>> movimiento =
        jdbc.queryForList(
            "SELECT estado, version FROM movimiento_venta WHERE id_venta = ?", idVenta);
    if (movimiento.isEmpty()) {
      return;
    }
    String estadoDespues = (String) movimiento.get(0).get("estado");
    if (Objects.equals(estadoAntes, estadoDespues)) {
      // Restitución repetida: ya estaba en ese estado, no se republica.
      return;
    }
    long version = numero(movimiento.get(0).get("version"), 1L);
    outbox.emitirMovimientoStockResuelto(idHotel, idVenta, estadoDespues, version);
    if ("RESTITUIDO".equals(estadoDespues)) {
      emitirSnapshots(
          idHotel,
          jdbc.queryForList(
              "SELECT id_producto FROM movimiento_detalle WHERE id_venta = ? ORDER BY id_producto",
              Integer.class,
              idVenta));
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
              Map<String, Object> m = new LinkedHashMap<>();
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
  /** Ítems duplicados o fuera de rango: 400 antes de SQL (no sumar ocultamente). */
  private void validarItems(List<DebitoItem> items) {
    if (items == null || items.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("items", "no puede estar vacío"));
    }
    Set<Integer> vistos = new HashSet<>();
    for (DebitoItem item : items) {
      if (item == null || item.idProducto() == null || item.cantidad() == null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("items", "ítem incompleto"));
      }
      if (item.cantidad() < 1 || item.cantidad() > 9999) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR, Map.of("cantidad", "debe estar entre 1 y 9999"));
      }
      if (!vistos.add(item.idProducto())) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR, Map.of("items", "ítems de producto duplicados"));
      }
    }
  }

  /** Snapshots de las existencias afectadas, en orden de idProducto. */
  private void emitirSnapshots(Integer idHotel, List<Integer> idProductos) {
    for (Integer idProducto : idProductos) {
      List<Map<String, Object>> filas =
          jdbc.queryForList(
              """
              SELECT p.sku, p.nombre, ps.precio, ps.cantidad, ps.umbral_bajo, ps.version,
                     (p.estado AND ps.estado) AS estado
              FROM producto_sede ps
              JOIN producto p ON p.id = ps.id_producto
              WHERE ps.id_producto = ? AND ps.id_hotel = ?
              """,
              idProducto,
              idHotel);
      if (filas.isEmpty()) {
        continue;
      }
      Map<String, Object> fila = filas.get(0);
      outbox.emitirProductoStockSnapshot(
          idHotel,
          idProducto,
          (String) fila.get("sku"),
          (String) fila.get("nombre"),
          new BigDecimal(fila.get("precio").toString()).doubleValue(),
          ((Number) fila.get("cantidad")).intValue(),
          ((Number) fila.get("umbral_bajo")).intValue(),
          (Boolean) fila.get("estado"),
          ((Number) fila.get("version")).longValue());
    }
  }

  private String estadoDeMovimiento(Integer idVenta) {
    List<String> estados =
        jdbc.queryForList(
            "SELECT estado FROM movimiento_venta WHERE id_venta = ?", String.class, idVenta);
    return estados.isEmpty() ? null : estados.get(0);
  }

  private String estadoDeMovimientoConLock(Integer idVenta) {
    List<String> estados =
        jdbc.queryForList(
            "SELECT estado FROM movimiento_venta WHERE id_venta = ? FOR UPDATE",
            String.class,
            idVenta);
    return estados.isEmpty() ? null : estados.get(0);
  }

  private Integer idHotelDeMovimiento(Integer idVenta) {
    List<Integer> hoteles =
        jdbc.queryForList(
            "SELECT id_hotel FROM movimiento_venta WHERE id_venta = ?", Integer.class, idVenta);
    return hoteles.isEmpty() ? null : hoteles.get(0);
  }

  private String itemsJson(List<DebitoItem> items) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      DebitoItem it = items.get(i);
      sb.append("{\"idProducto\":")
          .append(it.idProducto())
          .append(",\"cantidad\":")
          .append(it.cantidad())
          .append("}");
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    try {
      return MAPPER.readValue(json, Map.class);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo parsear el resultado del movimiento de stock", ex);
    }
  }

  private long numero(Object valor, long defecto) {
    return valor instanceof Number n ? n.longValue() : defecto;
  }

  /**
   * El {@code resultado} JSONB persistido describe el desenlace sin {@code idVenta}/{@code idHotel}
   * (docs/especificacion/microservicios/inventory/base-de-datos.md § Esquemas JSONB); la respuesta
   * los toma de la petición.
   */
  @SuppressWarnings("unchecked")
  private DebitoResultado parseDebitoResultado(String json, Integer idVenta, Integer idHotel) {
    try {
      Map<String, Object> m = MAPPER.readValue(json, Map.class);
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
          idVenta,
          idHotel,
          (String) m.get("estado"),
          (String) m.get("moneda"),
          new BigDecimal(m.get("total").toString()),
          itemDtos,
          ((Number) m.get("version")).intValue());
    } catch (Exception ex) {
      throw new IllegalStateException(
          "no se pudo parsear el resultado del débito: " + ex.getMessage(), ex);
    }
  }

  private ErrorCode codigoRechazo(String resultadoJson) {
    String motivo = motivoDe(resultadoJson);
    if (motivo.contains("inactivo")) {
      return ErrorCode.PRODUCT_INACTIVE;
    }
    if (motivo.contains("cancelado")) {
      return ErrorCode.DEBIT_CANCELLED;
    }
    if (motivo.contains("duplicad") || motivo.contains("inválida") || motivo.contains("sin ítems")) {
      return ErrorCode.VALIDATION_ERROR;
    }
    return ErrorCode.STOCK_INSUFFICIENT;
  }

  private Map<String, String> camposRechazo(String resultadoJson) {
    String motivo = motivoDe(resultadoJson);
    Map<String, String> fields = new LinkedHashMap<>();
    if (motivo.contains("duplicad")) {
      fields.put("items", "ítems de producto duplicados");
    } else if (motivo.contains("insuficiente")) {
      fields.put("cantidad", "stock insuficiente");
    } else if (motivo.contains("inactivo")) {
      fields.put("idProducto", "producto o sede inactivos");
    } else if (motivo.contains("cancelado")) {
      fields.put("idVenta", "débito cancelado por restitución previa");
    } else if (motivo.contains("asignado")) {
      fields.put("idProducto", "producto no asignado a la sede");
    } else if (motivo.contains("inválida") || motivo.contains("sin ítems")) {
      fields.put("items", motivo);
    }
    return fields;
  }

  private String motivoDe(String resultadoJson) {
    Object motivo = parse(resultadoJson).getOrDefault("motivo", "");
    return motivo == null ? "" : motivo.toString();
  }

  public enum Estado {
    DESCONTADO,
    RECHAZADO,
    IDEMPOTENCY_CONFLICT
  }

  public record DescontarResultado(
      Estado estado, DebitoResultado resultado, ErrorCode errorCode, Map<String, String> fields) {}
}
