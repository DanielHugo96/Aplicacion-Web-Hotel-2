package pe.hotel.inventory.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.domain.dto.AjusteRequest;
import pe.hotel.inventory.domain.dto.AjusteResultado;
import pe.hotel.inventory.domain.dto.MovimientoView;
import pe.hotel.inventory.domain.dto.ProductoAlta;
import pe.hotel.inventory.domain.dto.ProductoUpdate;
import pe.hotel.inventory.domain.dto.ProductoView;
import pe.hotel.inventory.domain.dto.SedeAsignacion;
import pe.hotel.inventory.client.HotelClient;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;

/**
 * Catálogo de productos y existencias por sede: altas, edición, baja lógica por sede, asignación
 * de sede, ajustes de stock y consulta de movimientos.
 */
@Service
@Transactional(readOnly = true)
public class ProductoService {

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
    Integer idProducto = (Integer) keyHolder.getKeys().get("id");

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
    outbox.emitirProductoStockSnapshot(
        alta.idHotel(),
        idProducto,
        alta.sku(),
        alta.nombre(),
        alta.precio().doubleValue(),
        alta.cantidadInicial(),
        alta.umbralBajo(),
        true,
        view.version());
    return view;
  }

  @Transactional
  public ProductoView actualizar(
      Integer idProducto, Integer idHotel, ProductoUpdate update, String ifMatch,
      Integer productoVersionHeader) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
    }
    Long versionEsperada = parseVersion(ifMatch);

    ProductoView actual = buscar(idProducto, idHotel);
    // productoVersion del body y V del header se validan juntos antes de cualquier cambio.
    if (update.productoVersion() != null
        && !update.productoVersion().equals(actual.productoVersion())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("productoVersion", ifMatch));
    }
    if (!versionEsperada.equals(actual.version())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }

    boolean snapshotCambio =
        (update.nombre() != null && !update.nombre().equals(actual.nombre()))
            || (update.detalle() != null && !update.detalle().equals(actual.detalle()))
            || (update.imagenUrl() != null && !update.imagenUrl().equals(actual.imagenUrl()))
            || (update.precio() != null && !update.precio().equals(actual.precio()))
            || (update.umbralBajo() != null && !update.umbralBajo().equals(actual.umbralBajo()))
            || (update.estado() != null && update.estado() != actual.estado());

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

    // Actualiza campos de sede e incrementa version de producto_sede.
    jdbc.update(
        """
        UPDATE producto_sede
        SET precio = COALESCE(?, precio),
            umbral_bajo = COALESCE(?, umbral_bajo),
            estado = COALESCE(?, estado),
            version = version + 1,
            updated_at = now()
        WHERE id_producto = ? AND id_hotel = ? AND version = ?
        """,
        update.precio(),
        update.umbralBajo(),
        update.estado() == null ? actual.estado() : update.estado(),
        idProducto,
        idHotel,
        actual.version());

    ProductoView view = buscar(idProducto, idHotel);
    if (snapshotCambio) {
      outbox.emitirProductoStockSnapshot(
          idHotel,
          idProducto,
          view.sku(),
          view.nombre(),
          view.precio() == null ? null : view.precio().doubleValue(),
          view.cantidad(),
          view.umbralBajo(),
          view.estado(),
          view.version());
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
    if (!versionEsperada.equals(actual.version())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }
    // Baja lógica por sede: conserva cantidad y movimientos; solo cambia estado.
    jdbc.update(
        "UPDATE producto_sede SET estado = false, version = version + 1, updated_at = now() "
            + "WHERE id_producto = ? AND id_hotel = ? AND version = ?",
        idProducto,
        idHotel,
        actual.version());
    ProductoView view = buscar(idProducto, idHotel);
    outbox.emitirProductoStockSnapshot(
        idHotel,
        idProducto,
        view.sku(),
        view.nombre(),
        view.precio() == null ? null : view.precio().doubleValue(),
        view.cantidad(),
        view.umbralBajo(),
        false,
        view.version());
    return view;
  }

  @Transactional
  public ProductoView asignarSede(Integer idProducto, SedeAsignacion asignacion, Integer actorId) {
    // Valida que el producto exista.
    Integer productoCount =
        jdbc.queryForObject("SELECT count(*) FROM producto WHERE id = ?", Integer.class, idProducto);
    if (productoCount == null || productoCount == 0) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    // Valida la sede contra hotel antes de crear la asignación.
    validarSede(asignacion.idHotel());

    Integer inserted =
        jdbc.update(
            "CALL sp_producto_sede_crear(?, ?, ?, ?, ?)",
            idProducto,
            asignacion.idHotel(),
            asignacion.precio(),
            asignacion.cantidadInicial(),
            asignacion.umbralBajo());
    if (inserted == 0) {
      throw new ApiException(
          ErrorCode.DUPLICATE_RESOURCE, Map.of("idProducto", String.valueOf(idProducto)));
    }

    ProductoView view = buscar(idProducto, asignacion.idHotel());
    outbox.emitirProductoStockSnapshot(
        asignacion.idHotel(),
        idProducto,
        view.sku(),
        view.nombre(),
        view.precio() == null ? null : view.precio().doubleValue(),
        view.cantidad(),
        view.umbralBajo(),
        view.estado(),
        view.version());
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
    if (!versionEsperada.equals(actual.version())) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", ifMatch));
    }
    if (request.delta() == 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("delta", "debe ser distinto de 0"));
    }
    Integer saldo =
        jdbc.queryForObject(
            "SELECT fn_stock_ajustar(CAST(? AS uuid), ?, ?, ?, ?, ?)",
            Integer.class,
            UUID.randomUUID(),
            idProducto,
            idHotel,
            request.delta(),
            request.motivo(),
            actorId);
    ProductoView view = buscar(idProducto, idHotel);
    outbox.emitirProductoStockSnapshot(
        idHotel,
        idProducto,
        view.sku(),
        view.nombre(),
        view.precio() == null ? null : view.precio().doubleValue(),
        view.cantidad(),
        view.umbralBajo(),
        view.estado(),
        view.version());
    return new AjusteResultado(UUID.randomUUID(), saldo, view.version());
  }

  public List<MovimientoView> movimientos(
      Integer idProducto, Integer idHotel, Integer page, Integer size,
      String inicio, String fin) {
    int offset = (page == null ? 0 : page) * (size == null ? 20 : size);
    int limit = size == null ? 20 : size;
    String sql =
        """
        SELECT 'VENTA' AS tipo, mv.id_venta AS id_venta, mv.id_hotel AS id_hotel, mv.estado AS estado,
               md.cantidad AS cantidad, md.precio_unitario AS precio_unitario,
               NULL::uuid AS ajuste_id, NULL::integer AS delta, NULL::varchar AS motivo,
               mv.created_at AS created_at, mv.updated_at AS updated_at
        FROM movimiento_venta mv
        JOIN movimiento_detalle md ON md.id_venta = mv.id_venta
        WHERE mv.id_hotel = ?
        UNION ALL
        SELECT 'AJUSTE' AS tipo, NULL::integer AS id_venta, aj.id_hotel AS id_hotel,
               NULL::varchar AS estado, NULL::integer AS cantidad, NULL::numeric AS precio_unitario,
               aj.id AS ajuste_id, aj.delta AS delta, aj.motivo AS motivo,
               aj.created_at AS created_at, aj.created_at AS updated_at
        FROM ajuste_stock aj
        WHERE aj.id_hotel = ?
        ORDER BY created_at ASC, id_venta, ajuste_id
        LIMIT ? OFFSET ?
        """;
    return jdbc.query(
        sql,
        (rs, rowNum) -> {
          String tipo = rs.getString("tipo");
          return new MovimientoView(
              tipo,
              rs.getObject("id_venta", Integer.class),
              rs.getObject("id_hotel", Integer.class),
              rs.getString("estado"),
              rs.getObject("cantidad", Integer.class),
              rs.getObject("precio_unitario") == null
                  ? null
                  : new BigDecimal(rs.getObject("precio_unitario").toString()),
              rs.getObject("ajuste_id", UUID.class),
              rs.getObject("delta", Integer.class),
              rs.getString("motivo"),
              rs.getObject("created_at", Instant.class),
              rs.getObject("updated_at", Instant.class));
        },
        idHotel,
        idHotel,
        limit,
        offset);
  }

  private void validarSede(Integer idHotel) {
    try {
      HotelClient.HotelSede sede = hotelClient.datos(idHotel);
      if (sede == null || !sede.estado()) {
        throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private static Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
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
        rs.getObject("created_at", Instant.class),
        rs.getInt("ps_version"),
        rs.getInt("version"));
  }
}
