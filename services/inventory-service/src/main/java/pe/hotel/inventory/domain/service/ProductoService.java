package pe.hotel.inventory.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.client.HotelClient;
import pe.hotel.inventory.domain.dto.AjusteRequest;
import pe.hotel.inventory.domain.dto.AjusteResultado;
import pe.hotel.inventory.domain.dto.MovimientoView;
import pe.hotel.inventory.domain.dto.ProductoAlta;
import pe.hotel.inventory.domain.dto.ProductoUpdate;
import pe.hotel.inventory.domain.dto.ProductoView;
import pe.hotel.inventory.domain.dto.SedeAsignacion;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ApiResponse;
import pe.hotel.inventory.platform.ErrorCode;

/**
 * Catálogo de productos y existencias por sede: altas, edición, baja lógica por sede, asignación
 * de sede, ajustes de stock y consulta de movimientos.
 */
@Service
@Transactional(readOnly = true)
public class ProductoService {

  private static final int MOTIVO_MAX = 300;

  private final JdbcTemplate jdbc;
  private final OutboxService outbox;
  private final HotelClient hotelClient;

  public ProductoService(JdbcTemplate jdbc, OutboxService outbox, HotelClient hotelClient) {
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.hotelClient = hotelClient;
  }

  public List<ProductoView> listar(Integer idHotel, Boolean estado) {
    String sql =
        """
        SELECT p.id, p.sku, p.nombre, p.detalle, p.imagen_url, p.estado, p.version, p.created_at,
               ps.id_hotel, ps.precio, ps.cantidad, ps.umbral_bajo, ps.estado AS ps_estado,
               ps.version AS ps_version
        FROM producto p
        JOIN producto_sede ps ON ps.id_producto = p.id
        WHERE ps.id_hotel = ?
        """;
    List<Object> params = new ArrayList<>();
    params.add(idHotel);
    if (estado != null) {
      sql += " AND p.estado = ? AND ps.estado = ?";
      params.add(estado);
      params.add(estado);
    }
    sql += " ORDER BY p.id ASC";
    return jdbc.query(sql, ProductoService::mapProductoView, params.toArray());
  }

  public ProductoView buscar(Integer idProducto, Integer idHotel) {
    String sql =
        """
        SELECT p.id, p.sku, p.nombre, p.detalle, p.imagen_url, p.estado, p.version, p.created_at,
               ps.id_hotel, ps.precio, ps.cantidad, ps.umbral_bajo, ps.estado AS ps_estado,
               ps.version AS ps_version
        FROM producto p
        JOIN producto_sede ps ON ps.id_producto = p.id
        WHERE p.id = ? AND ps.id_hotel = ?
        """;
    List<ProductoView> rows = jdbc.query(sql, ProductoService::mapProductoView, idProducto, idHotel);
    if (rows.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return rows.get(0);
  }

  @Transactional
  public ProductoView registrar(ProductoAlta alta, Integer actorId) {
    if (alta.sku() == null
        || alta.sku().isBlank()
        || alta.nombre() == null
        || alta.nombre().isBlank()
        || alta.precio() == null
        || alta.precio().signum() <= 0
        || alta.cantidadInicial() == null
        || alta.cantidadInicial() < 0
        || alta.umbralBajo() == null
        || alta.umbralBajo() < 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("producto", "alta incompleta"));
    }
    Integer skuCount =
        jdbc.queryForObject("SELECT count(*) FROM producto WHERE sku = ?", Integer.class, alta.sku());
    if (skuCount != null && skuCount > 0) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("sku", alta.sku()));
    }
    // Valida que la sede exista y esté activa antes de asignar.
    validarSede(alta.idHotel());

    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          java.sql.PreparedStatement ps =
              con.prepareStatement(
                  "INSERT INTO producto (sku, nombre, detalle, imagen_url, estado, version, created_at)"
                      + " VALUES (?, ?, ?, ?, true, 1, ?)",
                  new String[] {"id"});
          ps.setString(1, alta.sku());
          ps.setString(2, alta.nombre());
          ps.setString(3, alta.detalle());
          ps.setString(4, alta.imagenUrl());
          ps.setTimestamp(5, java.sql.Timestamp.from(now));
          return ps;
        },
        keyHolder);
    Integer idProducto = ((Number) keyHolder.getKeys().get("id")).intValue();

    // Asignación inicial de la sede mediante el procedimiento SQL (alta única).
    jdbc.update(
        "CALL sp_producto_sede_crear(?, ?, ?, ?, ?)",
        idProducto,
        alta.idHotel(),
        alta.precio(),
        alta.cantidadInicial(),
        alta.umbralBajo());

    ProductoView view = buscar(idProducto, alta.idHotel());
    // Snapshot de stock inicial.
    emitirSnapshot(view, alta.idHotel(), view.estado());
    return view;
  }

  @Transactional
  public ProductoView actualizar(
      Integer idProducto, Integer idHotel, ProductoUpdate update, String ifMatch,
      Integer productoVersionBody) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
    }
    if (update == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("producto", "cuerpo ausente"));
    }
    Long versionEsperada = parseVersion(ifMatch);

    ProductoView actual = buscar(idProducto, idHotel);
    // productoVersion del body y V del header se validan juntos antes de cualquier cambio.
    if (productoVersionBody != null && !productoVersionBody.equals(actual.productoVersion())) {
      throw new ApiException(
          ErrorCode.PRECONDITION_FAILED, Map.of("productoVersion", String.valueOf(productoVersionBody)));
    }
    if (!versionEsperada.equals(actual.version().longValue())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }

    boolean snapshotCambio =
        (update.nombre() != null && !update.nombre().equals(actual.nombre()))
            || (update.detalle() != null && !update.detalle().equals(actual.detalle()))
            || (update.imagenUrl() != null && !update.imagenUrl().equals(actual.imagenUrl()))
            || (update.precio() != null && update.precio().compareTo(actual.precio()) != 0)
            || (update.umbralBajo() != null && !update.umbralBajo().equals(actual.umbralBajo()))
            || (update.estado() != null && !update.estado().equals(actual.estado()));

    // Actualiza globals del producto.
    if (update.nombre() != null) {
      jdbc.update("UPDATE producto SET nombre = ? WHERE id = ?", update.nombre(), idProducto);
    }
    if (update.detalle() != null) {
      jdbc.update("UPDATE producto SET detalle = ? WHERE id = ?", update.detalle(), idProducto);
    }
    if (update.imagenUrl() != null) {
      jdbc.update("UPDATE producto SET imagen_url = ? WHERE id = ?", update.imagenUrl(), idProducto);
    }

    // Actualiza campos de sede e incrementa version de producto_sede; nunca cantidad.
    jdbc.update(
        """
        UPDATE producto_sede
        SET precio = COALESCE(?, precio),
            umbral_bajo = COALESCE(?, umbral_bajo),
            estado = COALESCE(?, estado),
            version = version + 1
        WHERE id_producto = ? AND id_hotel = ? AND version = ?
        """,
        update.precio(),
        update.umbralBajo(),
        update.estado(),
        idProducto,
        idHotel,
        actual.version());

    ProductoView view = buscar(idProducto, idHotel);
    if (snapshotCambio) {
      emitirSnapshot(view, idHotel, view.estado());
    }
    return view;
  }

  @Transactional
  public ProductoView eliminar(Integer idProducto, Integer idHotel, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
    }
    Long versionEsperada = parseVersion(ifMatch);
    ProductoView actual = buscar(idProducto, idHotel);
    if (!versionEsperada.equals(actual.version().longValue())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }
    // Baja lógica por sede: conserva cantidad y movimientos; solo cambia estado.
    jdbc.update(
        "UPDATE producto_sede SET estado = false, version = version + 1 "
            + "WHERE id_producto = ? AND id_hotel = ? AND version = ?",
        idProducto,
        idHotel,
        actual.version());
    ProductoView view = buscar(idProducto, idHotel);
    emitirSnapshot(view, idHotel, Boolean.FALSE);
    return view;
  }

  @Transactional
  public ProductoView asignarSede(Integer idProducto, SedeAsignacion asignacion, Integer actorId) {
    if (asignacion == null
        || asignacion.precio() == null
        || asignacion.precio().signum() <= 0
        || asignacion.cantidadInicial() == null
        || asignacion.cantidadInicial() < 0
        || asignacion.umbralBajo() == null
        || asignacion.umbralBajo() < 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("asignacion", "incompleta"));
    }
    // Valida que el producto exista.
    Integer productoCount =
        jdbc.queryForObject("SELECT count(*) FROM producto WHERE id = ?", Integer.class, idProducto);
    if (productoCount == null || productoCount == 0) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    // Valida la sede contra hotel antes de crear la asignación.
    validarSede(asignacion.idHotel());

    try {
      jdbc.update(
          "CALL sp_producto_sede_crear(?, ?, ?, ?, ?)",
          idProducto,
          asignacion.idHotel(),
          asignacion.precio(),
          asignacion.cantidadInicial(),
          asignacion.umbralBajo());
    } catch (DataIntegrityViolationException yaExiste) {
      throw new ApiException(
          ErrorCode.DUPLICATE_RESOURCE, Map.of("idHotel", String.valueOf(asignacion.idHotel())));
    }

    ProductoView view = buscar(idProducto, asignacion.idHotel());
    emitirSnapshot(view, asignacion.idHotel(), view.estado());
    return view;
  }

  @Transactional
  public AjusteResultado ajustar(
      Integer idProducto, Integer idHotel, String ifMatch, AjusteRequest request, Integer actorId) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
    }
    Long versionEsperada = parseVersion(ifMatch);
    ProductoView actual = buscar(idProducto, idHotel);
    if (!versionEsperada.equals(actual.version().longValue())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }
    if (request == null || request.delta() == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("delta", "es obligatorio"));
    }
    if (request.delta() == 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("delta", "debe ser distinto de 0"));
    }
    if (request.motivo() == null
        || request.motivo().isBlank()
        || request.motivo().length() > MOTIVO_MAX) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("motivo", "debe tener entre 1 y " + MOTIVO_MAX));
    }

    UUID ajusteId = UUID.randomUUID();
    Integer saldo;
    try {
      saldo =
          jdbc.queryForObject(
              "SELECT fn_stock_ajustar(CAST(? AS uuid), ?, ?, ?, ?, ?)",
              Integer.class,
              ajusteId,
              idProducto,
              idHotel,
              request.delta(),
              request.motivo(),
              actorId);
    } catch (DataAccessException ex) {
      String mensaje = String.valueOf(ex.getMostSpecificCause().getMessage());
      if (mensaje.contains("saldo negativo")) {
        // El ajuste no mueve unidades: el saldo nunca queda negativo.
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR, Map.of("delta", "el ajuste dejaría saldo negativo"));
      }
      if (mensaje.contains("no existe")) {
        throw new ApiException(
            ErrorCode.RESOURCE_NOT_FOUND, Map.of("idProducto", String.valueOf(idProducto)));
      }
      // Cualquier otro fallo de la función es un ajuste inválido, no una fila ausente.
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("delta", "no se pudo aplicar el ajuste"));
    }

    ProductoView view = buscar(idProducto, idHotel);
    emitirSnapshot(view, idHotel, view.estado());
    return new AjusteResultado(ajusteId, saldo, view.version());
  }

  public List<MovimientoView> movimientos(
      Integer idProducto, Integer idHotel, Integer page, Integer size,
      String inicio, String fin) {
    int pagina = page == null || page < 0 ? 0 : page;
    int tamano = size == null || size < 1 ? 20 : size;
    int offset = pagina * tamano;

    List<Object> params = new ArrayList<>();
    String venta = "mv.id_hotel = ? AND md.id_producto = ?";
    params.add(idHotel);
    params.add(idProducto);
    venta += intervalo("mv.created_at", inicio, fin, params);
    String ajuste = "aj.id_hotel = ? AND aj.id_producto = ?";
    params.add(idHotel);
    params.add(idProducto);
    ajuste += intervalo("aj.created_at", inicio, fin, params);

    String sql =
        """
        SELECT 'VENTA' AS tipo, mv.id_venta AS id_venta, mv.id_hotel AS id_hotel, mv.estado AS estado,
               md.cantidad AS cantidad, md.precio_unitario AS precio_unitario,
               NULL::uuid AS ajuste_id, NULL::integer AS delta, NULL::varchar AS motivo,
               mv.created_at AS created_at, mv.updated_at AS updated_at
        FROM movimiento_venta mv
        JOIN movimiento_detalle md ON md.id_venta = mv.id_venta
        WHERE %s
        UNION ALL
        SELECT 'AJUSTE' AS tipo, NULL::integer AS id_venta, aj.id_hotel AS id_hotel,
               NULL::varchar AS estado, NULL::integer AS cantidad, NULL::numeric AS precio_unitario,
               aj.id AS ajuste_id, aj.delta AS delta, aj.motivo AS motivo,
               aj.created_at AS created_at, aj.created_at AS updated_at
        FROM ajuste_stock aj
        WHERE %s
        ORDER BY created_at ASC, tipo ASC, id_venta ASC, ajuste_id ASC
        LIMIT ? OFFSET ?
        """
            .formatted(venta, ajuste);
    params.add(tamano);
    params.add(offset);

    return jdbc.query(sql, ProductoService::mapMovimientoView, params.toArray());
  }

  /** Total de movimientos que cumplen el filtro, para el {@code meta.totalElements} de la lista. */
  public long contarMovimientos(
      Integer idProducto, Integer idHotel, String inicio, String fin) {
    List<Object> params = new ArrayList<>();
    String venta = "mv.id_hotel = ? AND md.id_producto = ?";
    params.add(idHotel);
    params.add(idProducto);
    venta += intervalo("mv.created_at", inicio, fin, params);
    String ajuste = "aj.id_hotel = ? AND aj.id_producto = ?";
    params.add(idHotel);
    params.add(idProducto);
    ajuste += intervalo("aj.created_at", inicio, fin, params);

    String sql =
        """
        SELECT (SELECT count(*) FROM movimiento_venta mv
                  JOIN movimiento_detalle md ON md.id_venta = mv.id_venta
                 WHERE %s)
             + (SELECT count(*) FROM ajuste_stock aj WHERE %s)
        """
            .formatted(venta, ajuste);
    Long total = jdbc.queryForObject(sql, Long.class, params.toArray());
    return total == null ? 0L : total;
  }

  // ------------------------------------------------------------------
  private String intervalo(String columna, String inicio, String fin, List<Object> params) {
    StringBuilder fragmento = new StringBuilder();
    if (inicio != null && !inicio.isBlank()) {
      fragmento.append(" AND ").append(columna).append(" >= CAST(? AS timestamptz)");
      params.add(inicio);
    }
    if (fin != null && !fin.isBlank()) {
      fragmento.append(" AND ").append(columna).append(" < CAST(? AS timestamptz)");
      params.add(fin);
    }
    return fragmento.toString();
  }

  private void emitirSnapshot(ProductoView view, Integer idHotel, Boolean estadoEfectivo) {
    outbox.emitirProductoStockSnapshot(
        idHotel,
        view.idProducto(),
        view.sku(),
        view.nombre(),
        view.precio() == null ? null : view.precio().doubleValue(),
        view.cantidad(),
        view.umbralBajo(),
        Boolean.TRUE.equals(estadoEfectivo),
        view.version());
  }

  private void validarSede(Integer idHotel) {
    try {
      ApiResponse<HotelClient.HotelSede> respuesta = hotelClient.datos(idHotel);
      HotelClient.HotelSede sede = respuesta == null ? null : respuesta.data();
      if (sede == null || !Boolean.TRUE.equals(sede.estado())) {
        // Sede inexistente o inactiva: no se crea la asignación.
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHotel", String.valueOf(idHotel)));
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (feign.FeignException.NotFound noExiste) {
      // Hotel responde 404: la sede no existe, no es un fallo de disponibilidad.
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHotel", String.valueOf(idHotel)));
    } catch (Exception ex) {
      // Hotel caído: 503 sin asignación optimista.
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private static Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    try {
      return Long.valueOf(v);
    } catch (NumberFormatException ex) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("If-Match", "debe ser un entero"));
    }
  }

  private static ProductoView mapProductoView(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    return new ProductoView(
        rs.getInt("id"),
        rs.getString("sku"),
        rs.getString("nombre"),
        rs.getString("detalle"),
        rs.getString("imagen_url"),
        rs.getInt("id_hotel"),
        new BigDecimal(rs.getObject("precio").toString()),
        rs.getInt("cantidad"),
        rs.getInt("umbral_bajo"),
        rs.getBoolean("ps_estado") && rs.getBoolean("estado"),
        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
        rs.getInt("ps_version"),
        rs.getInt("version"));
  }

  private static MovimientoView mapMovimientoView(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    Object precioUnitario = rs.getObject("precio_unitario");
    return new MovimientoView(
        rs.getString("tipo"),
        rs.getObject("id_venta", Integer.class),
        rs.getObject("id_hotel", Integer.class),
        rs.getString("estado"),
        rs.getObject("cantidad", Integer.class),
        precioUnitario == null ? null : new BigDecimal(precioUnitario.toString()),
        rs.getObject("ajuste_id", UUID.class),
        rs.getObject("delta", Integer.class),
        rs.getString("motivo"),
        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
  }
}
