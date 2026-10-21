package pe.hotel.sales.domain.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hotel.sales.domain.dto.DebitoResultado;
import pe.hotel.sales.domain.model.CarritoRow;
import pe.hotel.sales.domain.model.CierreRow;
import pe.hotel.sales.domain.model.DetalleRow;
import pe.hotel.sales.domain.model.OperacionRow;
import pe.hotel.sales.domain.model.VentaRow;

/**
 * Persistencia de sales_db. Las transiciones de la máquina de estados son CAS con comprobación
 * de filas modificadas; el commit de la operación y el efecto final van en la misma transacción
 * local (docs/especificacion/microservicios/sales/base-de-datos.md § Funciones/procedimientos).
 */
@Repository
public class SalesRepository {

  private final JdbcTemplate jdbc;

  public SalesRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ------------------------------------------------------------------ carrito

  public CarritoRow upsertCarrito(
      int idRecepcion, int idCliente, int idHotel, int idProducto, int cantidad) {
    return jdbc.queryForObject(
        """
        INSERT INTO carrito (id_recepcion, id_cliente, id_hotel, id_producto, cantidad)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id_recepcion, id_producto) DO UPDATE
           SET cantidad = carrito.cantidad + EXCLUDED.cantidad,
               version  = carrito.version + 1
        RETURNING id, id_recepcion, id_cliente, id_hotel, id_producto, cantidad, created_at, version
        """,
        SalesRepository::mapCarrito,
        idRecepcion,
        idCliente,
        idHotel,
        idProducto,
        cantidad);
  }

  public int limpiarCarrito(int idRecepcion) {
    return jdbc.update("DELETE FROM carrito WHERE id_recepcion = ?", idRecepcion);
  }

  public int quitarCarrito(int idRecepcion, int idProducto) {
    return jdbc.update(
        "DELETE FROM carrito WHERE id_recepcion = ? AND id_producto = ?",
        idRecepcion,
        idProducto);
  }

  public List<CarritoRow> carrito(int idRecepcion) {
    return jdbc.query(
        """
        SELECT id, id_recepcion, id_cliente, id_hotel, id_producto, cantidad, created_at, version
          FROM carrito WHERE id_recepcion = ? ORDER BY id
        """,
        SalesRepository::mapCarrito,
        idRecepcion);
  }

  // ------------------------------------------------------------------ venta

  public Integer insertarVenta(
      int idRecepcion,
      int idCliente,
      int idHotel,
      String requestItemsJson,
      String requestHash,
      UUID operationId,
      int actorId,
      String actorRol) {
    Integer id =
        jdbc.queryForObject(
            """
            INSERT INTO venta (id_recepcion, id_cliente, id_hotel, estado, estado_operacion,
                               admitida, origen, inventory_version, total, medio_pago,
                               request_items, current_operation_id, request_hash, version)
            VALUES (?, ?, ?, 'PENDIENTE', 'PENDIENTE', false, 'NUEVA', 0, 0, NULL,
                    CAST(? AS jsonb), ?, ?, 1)
            RETURNING id
            """,
            Integer.class,
            idRecepcion,
            idCliente,
            idHotel,
            requestItemsJson,
            operationId,
            requestHash);
    insertarOperacion(operationId, id, "VENTA", "PREPARANDO", "{}", actorId, actorRol);
    return id;
  }

  public void insertarOperacion(
      UUID operationId,
      int idVenta,
      String tipo,
      String estado,
      String requestJson,
      int actorId,
      String actorRol) {
    jdbc.update(
        """
        INSERT INTO operacion_venta (operation_id, id_venta, tipo, estado, request,
                                     actor_id, actor_rol)
        VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
        """,
        operationId,
        idVenta,
        tipo,
        estado,
        requestJson,
        actorId,
        actorRol);
  }

  public void actualizarOperacionActual(int idVenta, UUID operationId) {
    jdbc.update(
        "UPDATE venta SET current_operation_id = ?, updated_at = now() WHERE id = ?",
        operationId,
        idVenta);
  }

  public Optional<VentaRow> venta(int id) {
    List<VentaRow> rows =
        jdbc.query("SELECT * FROM venta WHERE id = ?", SalesRepository::mapVenta, id);
    return rows.stream().findFirst();
  }

  public List<VentaRow> ventas(
      int idHotel, String estado, String estadoOperacion, int page, int size) {
    StringBuilder sql =
        new StringBuilder("SELECT * FROM venta WHERE id_hotel = ?");
    List<Object> args = new java.util.ArrayList<>();
    args.add(idHotel);
    if (estado != null && !estado.isBlank()) {
      sql.append(" AND estado = ?");
      args.add(estado);
    }
    if (estadoOperacion != null && !estadoOperacion.isBlank()) {
      sql.append(" AND estado_operacion = ?");
      args.add(estadoOperacion);
    }
    sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
    args.add(size);
    args.add((long) page * size);
    return jdbc.query(sql.toString(), SalesRepository::mapVenta, args.toArray());
  }

  public long contarVentas(int idHotel, String estado, String estadoOperacion) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM venta WHERE id_hotel = ?");
    List<Object> args = new java.util.ArrayList<>();
    args.add(idHotel);
    if (estado != null && !estado.isBlank()) {
      sql.append(" AND estado = ?");
      args.add(estado);
    }
    if (estadoOperacion != null && !estadoOperacion.isBlank()) {
      sql.append(" AND estado_operacion = ?");
      args.add(estadoOperacion);
    }
    Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return total == null ? 0 : total;
  }

  public List<VentaRow> ventasPorRecepcion(int idRecepcion, int page, int size) {
    return jdbc.query(
        "SELECT * FROM venta WHERE id_recepcion = ? ORDER BY id ASC LIMIT ? OFFSET ?",
        SalesRepository::mapVenta,
        idRecepcion,
        size,
        (long) page * size);
  }

  public long contarVentasPorRecepcion(int idRecepcion) {
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM venta WHERE id_recepcion = ?", Long.class, idRecepcion);
    return total == null ? 0 : total;
  }

  public List<DetalleRow> detalles(int idVenta) {
    return jdbc.query(
        """
        SELECT id, id_venta, id_producto, nombre_producto, cantidad, precio_unitario, subtotal
          FROM detalle_venta WHERE id_venta = ? ORDER BY id
        """,
        SalesRepository::mapDetalle,
        idVenta);
  }

  /** CAS operación PREPARANDO → DEBITO_SOLICITADO y venta admitida. Un solo commit antes del HTTP. */
  public boolean prepararDebito(int idVenta, UUID operationId) {
    int operacion =
        jdbc.update(
            """
            UPDATE operacion_venta
               SET estado = 'DEBITO_SOLICITADO', attempts = attempts + 1,
                   next_attempt_at = now() + interval '30 seconds'
             WHERE operation_id = ? AND id_venta = ? AND estado = 'PREPARANDO'
            """,
            operationId,
            idVenta);
    if (operacion != 1) {
      return false;
    }
    int venta =
        jdbc.update(
            "UPDATE venta SET admitida = true, updated_at = now()"
                + " WHERE id = ? AND estado_operacion = 'PENDIENTE'",
            idVenta);
    return venta == 1;
  }

  /** CAS PENDIENTE → CONFIRMADA con snapshot autoritativo; aplica el pago manual si se pidió. */
  public boolean confirmarVenta(
      int idVenta,
      UUID operationId,
      DebitoResultado resultado,
      boolean pagar,
      String medioPago,
      UUID closureId) {
    int filas =
        jdbc.update(
            """
            UPDATE venta
               SET estado_operacion = 'CONFIRMADA',
                   admitida = true,
                   total = ?,
                   inventory_version = ?,
                   estado = CASE WHEN ? THEN 'PAGADO' ELSE estado END,
                   medio_pago = CASE WHEN ? THEN ? ELSE medio_pago END,
                   paid_at = CASE WHEN ? THEN now() ELSE paid_at END,
                   closure_id = CASE WHEN ? THEN ? ELSE closure_id END,
                   version = version + 1,
                   updated_at = now()
             WHERE id = ? AND estado_operacion = 'PENDIENTE'
               AND EXISTS (SELECT 1 FROM operacion_venta
                            WHERE operation_id = ? AND estado = 'DEBITO_SOLICITADO')
            """,
            resultado.total(),
            resultado.version(),
            pagar,
            pagar,
            medioPago,
            pagar,
            pagar,
            closureId,
            idVenta,
            operationId);
    if (filas != 1) {
      return false;
    }
    jdbc.update("DELETE FROM detalle_venta WHERE id_venta = ?", idVenta);
    for (var item : resultado.items()) {
      jdbc.update(
          """
          INSERT INTO detalle_venta (id_venta, id_producto, nombre_producto, cantidad,
                                     precio_unitario, subtotal)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          idVenta,
          item.idProducto(),
          item.nombreProducto(),
          item.cantidad(),
          item.precioUnitario(),
          item.subTotal());
    }
    return true;
  }

  /** CAS PENDIENTE → RECHAZADA, solo sin débito confirmado; la operación se cierra aparte. */
  public boolean rechazarVenta(int idVenta, UUID operationId) {
    int filas =
        jdbc.update(
            """
            UPDATE venta SET estado_operacion = 'RECHAZADA', version = version + 1, updated_at = now()
             WHERE id = ? AND estado_operacion = 'PENDIENTE'
            """,
            idVenta);
    if (filas != 1) {
      return false;
    }
    terminarOperacionSiCoincide(operationId, idVenta);
    return true;
  }

  /** PENDIENTE → CANCELACION_PENDIENTE; la operación sigue no terminal hasta RESTITUIDO. */
  public boolean compensarVenta(int idVenta, UUID operationId, String motivo) {
    int filas =
        jdbc.update(
            """
            UPDATE venta
               SET estado_operacion = 'CANCELACION_PENDIENTE', motivo_cancelacion = ?,
                   version = version + 1, updated_at = now()
             WHERE id = ? AND estado_operacion = 'PENDIENTE'
            """,
            motivo.length() > 300 ? motivo.substring(0, 300) : motivo,
            idVenta);
    if (filas != 1) {
      return false;
    }
    jdbc.update(
        """
        UPDATE operacion_venta
           SET attempts = attempts + 1, next_attempt_at = now() + interval '60 seconds'
         WHERE operation_id = ? AND id_venta = ? AND estado <> 'TERMINAL'
        """,
        operationId,
        idVenta);
    return true;
  }

  /** CANCELACION_PENDIENTE → CANCELADA solo con evidencia de restitución. */
  public boolean completarCancelacion(int idVenta, long inventoryVersion) {
    return jdbc.update(
            """
            UPDATE venta
               SET estado_operacion = 'CANCELADA',
                   inventory_version = GREATEST(inventory_version, ?),
                   version = version + 1, updated_at = now()
             WHERE id = ? AND estado_operacion = 'CANCELACION_PENDIENTE'
            """,
            inventoryVersion,
            idVenta)
        == 1;
  }

  /** CAS de pago: solo CONFIRMADA impagada, versión vigente y medio obligatorio. */
  public boolean pagarVenta(
      int idVenta, UUID operationId, long version, String medioPago, UUID closureId) {
    int filas =
        jdbc.update(
            """
            UPDATE venta
               SET estado = 'PAGADO', medio_pago = ?, paid_at = now(),
                   closure_id = COALESCE(?, closure_id), version = version + 1, updated_at = now()
             WHERE id = ? AND version = ? AND estado_operacion = 'CONFIRMADA' AND estado = 'PENDIENTE'
            """,
            medioPago,
            closureId,
            idVenta,
            version);
    if (filas != 1) {
      return false;
    }
    jdbc.update(
        "UPDATE operacion_venta SET estado = 'TERMINAL'"
            + " WHERE operation_id = ? AND id_venta = ? AND estado <> 'TERMINAL'",
        operationId,
        idVenta);
    return true;
  }

  /** CAS de anulación: PENDIENTE o CONFIRMADA impagada. */
  public boolean solicitarCancelacion(int idVenta, String motivo) {
    return jdbc.update(
            """
            UPDATE venta
               SET estado_operacion = 'CANCELACION_PENDIENTE',
                   motivo_cancelacion = ?, version = version + 1, updated_at = now()
             WHERE id = ? AND estado = 'PENDIENTE'
               AND estado_operacion IN ('PENDIENTE','CONFIRMADA')
            """,
            motivo.length() > 300 ? motivo.substring(0, 300) : motivo,
            idVenta)
        == 1;
  }

  public void marcarOperacionAdmitida(UUID operationId) {
    jdbc.update(
        "UPDATE operacion_venta SET estado = 'ADMITIDA', next_attempt_at = now()"
            + " WHERE operation_id = ? AND estado = 'PREPARANDO'",
        operationId);
  }

  public void terminarOperacionSiCoincide(UUID operationId, int idVenta) {
    jdbc.update(
        "UPDATE operacion_venta SET estado = 'TERMINAL'"
            + " WHERE operation_id = ? AND id_venta = ? AND estado <> 'TERMINAL'",
        operationId,
        idVenta);
  }

  /** Cierra las operaciones no terminales distintas de la nueva ANULACION/PAGO. */
  public void terminarOperacionesAbiertas(int idVenta, UUID excepto) {
    jdbc.update(
        "UPDATE operacion_venta SET estado = 'TERMINAL'"
            + " WHERE id_venta = ? AND estado <> 'TERMINAL' AND operation_id <> ?",
        idVenta,
        excepto);
  }

  public Optional<OperacionRow> operacion(UUID operationId) {
    List<OperacionRow> rows =
        jdbc.query(
            "SELECT * FROM operacion_venta WHERE operation_id = ?",
            SalesRepository::mapOperacion,
            operationId);
    return rows.stream().findFirst();
  }

  public Optional<OperacionRow> operacionAbierta(int idVenta) {
    List<OperacionRow> rows =
        jdbc.query(
            "SELECT * FROM operacion_venta WHERE id_venta = ? AND estado <> 'TERMINAL' ORDER BY created_at LIMIT 1",
            SalesRepository::mapOperacion,
            idVenta);
    return rows.stream().findFirst();
  }

  // ---------------------------------------------------------------- cierre

  public record ResumenRow(
      BigDecimal totalPendiente, BigDecimal totalPagado, List<Integer> pendientes) {}

  public ResumenRow resumen(int idRecepcion, int idHotel) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(total) FILTER (WHERE estado = 'PENDIENTE'), 0) AS pendiente,
               COALESCE(SUM(total) FILTER (WHERE estado = 'PAGADO'), 0) AS pagado,
               COALESCE(array_agg(id ORDER BY id) FILTER (WHERE estado = 'PENDIENTE'), '{}') AS ids
          FROM venta
         WHERE id_recepcion = ? AND id_hotel = ?
           AND admitida = true AND estado_operacion = 'CONFIRMADA'
        """,
        (rs, n) -> {
          Integer[] ids = (Integer[]) rs.getArray("ids").getArray();
          return new ResumenRow(
              rs.getBigDecimal("pendiente"),
              rs.getBigDecimal("pagado"),
              java.util.Arrays.asList(ids));
        },
        idRecepcion,
        idHotel);
  }

  public boolean hayOperacionAbierta(int idRecepcion, int idHotel) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT count(*)
              FROM operacion_venta o JOIN venta v ON v.id = o.id_venta
             WHERE v.id_recepcion = ? AND v.id_hotel = ? AND o.estado <> 'TERMINAL'
            """,
            Long.class,
            idRecepcion,
            idHotel);
    return count != null && count > 0;
  }

  public Optional<CierreRow> cierre(UUID closureId) {
    List<CierreRow> rows =
        jdbc.query(
            "SELECT closure_id, id_recepcion, id_hotel, request_hash, receipt::text AS receipt,"
                + " confirmed_at FROM cierre_consumos WHERE closure_id = ?",
            SalesRepository::mapCierre,
            closureId);
    return rows.stream().findFirst();
  }

  public Optional<CierreRow> cierrePorRecepcion(int idRecepcion) {
    List<CierreRow> rows =
        jdbc.query(
            "SELECT closure_id, id_recepcion, id_hotel, request_hash, receipt::text AS receipt,"
                + " confirmed_at FROM cierre_consumos WHERE id_recepcion = ?",
            SalesRepository::mapCierre,
            idRecepcion);
    return rows.stream().findFirst();
  }

  public void bloquearRecepcion(int idRecepcion) {
    jdbc.execute(
        (java.sql.Connection connection) -> {
          try (java.sql.PreparedStatement statement =
              connection.prepareStatement("SELECT pg_advisory_xact_lock(7101, ?)")) {
            statement.setInt(1, idRecepcion);
            statement.execute();
          }
          return null;
        });
  }

  public void insertarCierre(
      UUID closureId,
      int idRecepcion,
      int idHotel,
      String requestHash,
      BigDecimal totalPendiente,
      BigDecimal totalPagadoPrevio,
      String medioPago,
      String receiptJson) {
    jdbc.update(
        """
        INSERT INTO cierre_consumos (closure_id, id_recepcion, id_hotel, request_hash,
                                     total_pendiente, total_pagado_previo, medio_pago, receipt)
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        closureId,
        idRecepcion,
        idHotel,
        requestHash,
        totalPendiente,
        totalPagadoPrevio,
        medioPago,
        receiptJson);
  }

  public int pagarPendientesDeCierre(
      int idRecepcion, int idHotel, String medioPago, UUID closureId) {
    return jdbc.update(
        """
        UPDATE venta
           SET estado = 'PAGADO', medio_pago = ?, paid_at = now(), closure_id = ?,
               version = version + 1, updated_at = now()
         WHERE id_recepcion = ? AND id_hotel = ?
           AND admitida = true AND estado_operacion = 'CONFIRMADA' AND estado = 'PENDIENTE'
        """,
        medioPago,
        closureId,
        idRecepcion,
        idHotel);
  }

  public List<Integer> ventasDeRecepcionPorEstado(int idRecepcion, int idHotel, String estado) {
    return jdbc.queryForList(
        """
        SELECT id FROM venta
         WHERE id_recepcion = ? AND id_hotel = ? AND admitida = true
           AND estado_operacion = 'CONFIRMADA' AND estado = ?
         ORDER BY id
        """,
        Integer.class,
        idRecepcion,
        idHotel,
        estado);
  }

  public List<Integer> operacionesNoTerminales(int idRecepcion, int idHotel) {
    return jdbc.queryForList(
        """
        SELECT v.id FROM operacion_venta o JOIN venta v ON v.id = o.id_venta
         WHERE v.id_recepcion = ? AND v.id_hotel = ? AND o.estado <> 'TERMINAL'
         ORDER BY v.id
        """,
        Integer.class,
        idRecepcion,
        idHotel);
  }

  // ------------------------------------------------------------- recuperación

  public record Recuperable(int idVenta, UUID operationId, String tipo, String estadoOperacion) {}

  /**
   * Operaciones en PREPARANDO que la recuperación debe atender, separadas por tipo: la venta alta
   * vencida por edad y las operaciones PAGO y ANULACION cuyo siguiente intento ya venció
   * (docs/especificacion/04-flujos.md §2).
   */
  public List<Recuperable> preparandoVencidas(int staleSeconds) {
    List<Recuperable> candidatas = new ArrayList<>();
    candidatas.addAll(preparandoVentasVencidas(staleSeconds));
    candidatas.addAll(preparandoPagos());
    candidatas.addAll(preparandoAnulaciones());
    return candidatas;
  }

  /** Venta alta PENDIENTE con operación VENTA en PREPARANDO de más de staleSeconds: se rechaza. */
  public List<Recuperable> preparandoVentasVencidas(int staleSeconds) {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id
         WHERE o.tipo = 'VENTA' AND v.estado_operacion = 'PENDIENTE' AND o.estado = 'PREPARANDO'
           AND o.created_at < now() - (? * interval '1 second')
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")),
        staleSeconds);
  }

  /**
   * Operación PAGO en PREPARANDO sobre una venta todavía impaga: se reintenta la misma admisión y
   * el mismo pago local. La venta ya PAGADA no se selecciona: no hay nada que cobrar.
   */
  public List<Recuperable> preparandoPagos() {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id
         WHERE o.tipo = 'PAGO' AND o.estado = 'PREPARANDO'
           AND v.estado = 'PENDIENTE' AND v.estado_operacion IN ('PENDIENTE','CONFIRMADA')
           AND o.next_attempt_at <= now()
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")));
  }

  /** Operación ANULACION en PREPARANDO: reanuda su admisión y su CAS de anulación. */
  public List<Recuperable> preparandoAnulaciones() {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id
         WHERE o.tipo = 'ANULACION' AND o.estado = 'PREPARANDO'
           AND v.estado_operacion IN ('PENDIENTE','CONFIRMADA')
           AND o.next_attempt_at <= now()
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")));
  }

  public List<Recuperable> debitoVencido(int staleSeconds) {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id
         WHERE v.estado_operacion = 'PENDIENTE' AND o.estado = 'DEBITO_SOLICITADO'
           AND o.created_at < now() - (? * interval '1 second')
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")),
        staleSeconds);
  }

  public List<Recuperable> cancelacionesPendientes() {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.id_venta = v.id
         WHERE v.estado_operacion = 'CANCELACION_PENDIENTE' AND o.estado <> 'TERMINAL'
           AND o.next_attempt_at <= now()
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")));
  }

  public List<Recuperable> operacionesPorReintentar(String tipo) {
    return jdbc.query(
        """
        SELECT v.id, o.operation_id, o.tipo, v.estado_operacion
          FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id
         WHERE o.tipo = ? AND o.estado = 'ADMITIDA' AND o.next_attempt_at <= now()
        """,
        (rs, n) ->
            new Recuperable(
                rs.getInt("id"),
                rs.getObject("operation_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("estado_operacion")),
        tipo);
  }

  public void posponerOperacion(UUID operationId, int segundos) {
    jdbc.update(
        "UPDATE operacion_venta SET attempts = attempts + 1,"
            + " next_attempt_at = now() + (? * interval '1 second') WHERE operation_id = ?",
        segundos,
        operationId);
  }

  public void avanzarOperacion(UUID operationId) {
    jdbc.update(
        "UPDATE operacion_venta SET attempts = attempts + 1,"
            + " next_attempt_at = now() + interval '30 seconds' WHERE operation_id = ?",
        operationId);
  }

  public boolean existeComandoRestituirPendiente(int idVenta) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE destination = 'RABBIT'"
                + " AND channel = 'stock.restituir' AND aggregate_id = ? AND published_at IS NULL",
            Long.class,
            String.valueOf(idVenta));
    return count != null && count > 0;
  }

  // ------------------------------------------------------------------ mappers

  private static CarritoRow mapCarrito(ResultSet rs, int n) throws SQLException {
    return new CarritoRow(
        rs.getInt("id"),
        rs.getInt("id_recepcion"),
        rs.getInt("id_cliente"),
        rs.getInt("id_hotel"),
        rs.getInt("id_producto"),
        rs.getInt("cantidad"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getLong("version"));
  }

  private static VentaRow mapVenta(ResultSet rs, int n) throws SQLException {
    return new VentaRow(
        rs.getInt("id"),
        rs.getInt("id_recepcion"),
        rs.getInt("id_cliente"),
        rs.getInt("id_hotel"),
        rs.getString("estado"),
        rs.getString("estado_operacion"),
        rs.getBoolean("admitida"),
        rs.getString("origen"),
        rs.getLong("inventory_version"),
        rs.getBigDecimal("total"),
        rs.getString("medio_pago"),
        rs.getString("request_items"),
        rs.getObject("current_operation_id", UUID.class),
        rs.getString("motivo_cancelacion"),
        rs.getLong("version"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
        rs.getObject("paid_at", OffsetDateTime.class) == null
            ? null
            : rs.getObject("paid_at", OffsetDateTime.class).toInstant(),
        rs.getObject("closure_id", UUID.class));
  }

  private static DetalleRow mapDetalle(ResultSet rs, int n) throws SQLException {
    return new DetalleRow(
        rs.getInt("id"),
        rs.getInt("id_venta"),
        rs.getInt("id_producto"),
        rs.getString("nombre_producto"),
        rs.getInt("cantidad"),
        rs.getBigDecimal("precio_unitario"),
        rs.getBigDecimal("subtotal"));
  }

  private static OperacionRow mapOperacion(ResultSet rs, int n) throws SQLException {
    return new OperacionRow(
        rs.getObject("operation_id", UUID.class),
        rs.getInt("id_venta"),
        rs.getString("tipo"),
        rs.getString("estado"),
        rs.getString("request"),
        rs.getInt("actor_id"),
        rs.getString("actor_rol"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
        rs.getInt("attempts"));
  }

  private static CierreRow mapCierre(ResultSet rs, int n) throws SQLException {
    return new CierreRow(
        rs.getObject("closure_id", UUID.class),
        rs.getInt("id_recepcion"),
        rs.getInt("id_hotel"),
        rs.getString("request_hash"),
        rs.getString("receipt"),
        rs.getObject("confirmed_at", OffsetDateTime.class).toInstant());
  }
}
