package pe.hotel.reporting.projection;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Upserts de las proyecciones y bookkeeping de checkpoints/bootstrap.
 *
 * <p>Todas las escrituras de un evento ocurren en la transacción que abre
 * {@link ProjectionService}; el inbox se reclama dentro de esa misma transacción.
 */
@Repository
public class ProjectionStore {

  private final JdbcTemplate jdbc;

  public ProjectionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ---------------------------------------------------------------------------
  // Catálogo
  // ---------------------------------------------------------------------------

  public void upsertPersona(JsonNode p, long version) {
    jdbc.update(
        """
        INSERT INTO persona_projection (id_persona, nombre, apellido, id_tipo_persona, estado, source_version)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id_persona) DO UPDATE SET
          nombre = EXCLUDED.nombre,
          apellido = EXCLUDED.apellido,
          id_tipo_persona = EXCLUDED.id_tipo_persona,
          estado = EXCLUDED.estado,
          source_version = EXCLUDED.source_version
        WHERE persona_projection.source_version < EXCLUDED.source_version
        """,
        p.path("idPersona").asInt(),
        p.path("nombre").asText(),
        p.path("apellido").asText(),
        p.path("idTipoPersona").asInt(),
        p.path("estado").asBoolean(),
        version);
  }

  public void upsertHotel(JsonNode p, long version) {
    jdbc.update(
        """
        INSERT INTO hotel_projection (id_hotel, nombre, ciudad, estado, source_version)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id_hotel) DO UPDATE SET
          nombre = EXCLUDED.nombre,
          ciudad = EXCLUDED.ciudad,
          estado = EXCLUDED.estado,
          source_version = EXCLUDED.source_version
        WHERE hotel_projection.source_version < EXCLUDED.source_version
        """,
        p.path("idHotel").asInt(),
        p.path("nombre").asText(),
        p.path("ciudad").asText(),
        p.path("estado").asBoolean(),
        version);
  }

  public void upsertHabitacion(JsonNode p, long version) {
    jdbc.update(
        """
        INSERT INTO habitacion_projection (id_habitacion, id_hotel, numero, categoria_nombre, precio,
                                           estado, estado_fisico, ocupada, hotel_version, room_version)
        VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 0)
        ON CONFLICT (id_habitacion) DO UPDATE SET
          id_hotel = EXCLUDED.id_hotel,
          numero = EXCLUDED.numero,
          categoria_nombre = EXCLUDED.categoria_nombre,
          precio = EXCLUDED.precio,
          estado = EXCLUDED.estado,
          estado_fisico = EXCLUDED.estado_fisico,
          hotel_version = EXCLUDED.hotel_version
        WHERE habitacion_projection.hotel_version < EXCLUDED.hotel_version
        """,
        p.path("idHabitacion").asInt(),
        p.path("idHotel").asInt(),
        p.path("numero").asText(),
        p.path("categoriaNombre").asText(),
        p.path("precio").decimalValue(),
        p.path("estado").asBoolean(),
        p.path("estadoFisico").asText(),
        version);
  }

  // ---------------------------------------------------------------------------
  // Gates y limpieza
  // ---------------------------------------------------------------------------

  /**
   * Hotel solo inicializa la parte física: si el gate ya existe (proyectado desde reception) no
   * reemplaza su estado. Un snapshot genérico nunca libera la habitación.
   */
  public void initGateFromHotel(JsonNode p, String type) {
    int idHabitacion = p.path("idHabitacion").asInt();
    UUID cycle = uuid(p.get("cleaningCycleId"));
    UUID block = uuid(p.get("blockId"));
    String estado = "LIBRE";
    if (block != null) {
      estado = "BLOQUEADA";
    } else if (cycle != null) {
      estado = "LIMPIEZA_PENDIENTE";
    }
    long version = p.path("version").asLong(0);
    jdbc.update(
        """
        INSERT INTO gate_projection (id_habitacion, estado, cleaning_cycle_id, block_id, hotel_version, room_version)
        VALUES (?, ?, ?, ?, ?, 0)
        ON CONFLICT (id_habitacion) DO UPDATE SET
          hotel_version = GREATEST(gate_projection.hotel_version, EXCLUDED.hotel_version)
        """,
        idHabitacion,
        estado,
        cycle,
        block,
        version);
  }

  /** HabitacionLista: recuerda el ciclo completado y libera solo ese ciclo pendiente. */
  public void applyLimpiezaCompletada(JsonNode p) {
    int idHabitacion = p.path("idHabitacion").asInt();
    UUID cycle = uuid(p.get("cleaningCycleId"));
    long cleaningRoomVersion = p.path("cleaningRoomVersion").asLong(0);
    long version = p.path("version").asLong(0);
    if (cycle == null) {
      return;
    }
    jdbc.update(
        """
        INSERT INTO limpieza_completada_projection
          (cleaning_cycle_id, id_habitacion, cleaning_room_version, hotel_version, completed_at)
        VALUES (?, ?, ?, ?, now())
        ON CONFLICT (cleaning_cycle_id) DO UPDATE SET
          id_habitacion = EXCLUDED.id_habitacion,
          cleaning_room_version = EXCLUDED.cleaning_room_version,
          hotel_version = EXCLUDED.hotel_version
        """,
        cycle,
        idHabitacion,
        cleaningRoomVersion,
        version);
    initGateIfMissing(idHabitacion, version);
    jdbc.update(
        """
        UPDATE gate_projection
           SET estado = 'LIBRE',
               cleaning_cycle_id = NULL,
               room_version = GREATEST(room_version, ?),
               hotel_version = GREATEST(hotel_version, ?)
         WHERE id_habitacion = ?
           AND estado = 'LIMPIEZA_PENDIENTE'
           AND cleaning_cycle_id = ?
        """,
        cleaningRoomVersion,
        version,
        idHabitacion,
        cycle);
  }

  /** HabitacionHabilitada: libera solo el bloqueo con el mismo blockId; nunca un LISTA genérico. */
  public void applyHabilitacion(JsonNode p) {
    int idHabitacion = p.path("idHabitacion").asInt();
    UUID block = uuid(p.get("blockId"));
    long version = p.path("version").asLong(0);
    initGateIfMissing(idHabitacion, version);
    if (block == null) {
      return;
    }
    jdbc.update(
        """
        UPDATE gate_projection
           SET estado = 'LIBRE',
               block_id = NULL,
               hotel_version = GREATEST(hotel_version, ?)
         WHERE id_habitacion = ? AND block_id = ?
        """,
        version,
        idHabitacion,
        block);
  }

  /** Snapshot de reception aplicado por roomVersion; reconcilia contra ciclos ya completados. */
  public void applyGateFromRecepcion(JsonNode p) {
    JsonNode gate = p.path("ocupacionActual");
    if (gate.isMissingNode() || gate.isNull()) {
      return;
    }
    int idHabitacion = p.path("idHabitacion").asInt();
    long roomVersion = p.path("roomVersion").asLong(0);
    String estado = gate.path("estadoGate").asText("LIBRE");
    UUID cycle = uuid(gate.get("cleaningCycleId"));
    UUID block = uuid(gate.get("blockId"));

    // Si la Lista del ciclo llegó antes que la Salida, el ciclo ya está completado: no dejamos
    // LIMPIEZA_PENDIENTE eterno.
    if ("LIMPIEZA_PENDIENTE".equals(estado) && cycle != null && isCycleCompleted(cycle)) {
      estado = "LIBRE";
      cycle = null;
    }

    jdbc.update(
        """
        INSERT INTO gate_projection (id_habitacion, estado, cleaning_cycle_id, block_id, hotel_version, room_version)
        VALUES (?, ?, ?, ?, 0, ?)
        ON CONFLICT (id_habitacion) DO UPDATE SET
          estado = EXCLUDED.estado,
          cleaning_cycle_id = EXCLUDED.cleaning_cycle_id,
          block_id = EXCLUDED.block_id,
          room_version = EXCLUDED.room_version
        WHERE gate_projection.room_version < EXCLUDED.room_version
        """,
        idHabitacion,
        estado,
        cycle,
        block,
        roomVersion);

    jdbc.update(
        """
        UPDATE habitacion_projection
           SET ocupada = ?,
               room_version = ?
         WHERE id_habitacion = ? AND room_version < ?
        """,
        "OCUPADA".equals(estado),
        roomVersion,
        idHabitacion,
        roomVersion);
  }

  private void initGateIfMissing(int idHabitacion, long hotelVersion) {
    jdbc.update(
        """
        INSERT INTO gate_projection (id_habitacion, estado, hotel_version, room_version)
        VALUES (?, 'LIBRE', ?, 0)
        ON CONFLICT (id_habitacion) DO NOTHING
        """,
        idHabitacion,
        hotelVersion);
  }

  private boolean isCycleCompleted(UUID cycle) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM limpieza_completada_projection WHERE cleaning_cycle_id = ?",
            Integer.class,
            cycle);
    return count != null && count > 0;
  }

  // ---------------------------------------------------------------------------
  // Estadías
  // ---------------------------------------------------------------------------

  public void upsertRecepcion(JsonNode p, long version) {
    int idRecepcion = p.path("idRecepcion").asInt();
    String estado = p.path("estadoEstadia").asText();
    JsonNode cierre = p.get("cierre");
    boolean conCierre = cierre != null && !cierre.isNull();

    BigDecimal totalAlojamiento = null;
    BigDecimal totalConsumos = null;
    BigDecimal totalGeneral = null;
    BigDecimal cobroAhora = null;
    String medioPagoCierre = null;
    UUID closureId = null;
    OffsetDateTime fechaCierre = null;

    if (conCierre) {
      totalAlojamiento = cierre.path("alojamientoTotal").decimalValue();
      totalConsumos = cierre.path("consumosTotal").decimalValue();
      totalGeneral = cierre.path("totalGeneral").decimalValue();
      cobroAhora = cierre.path("cobroAhora").decimalValue();
      medioPagoCierre = textOrNull(cierre.get("medioPago"));
      closureId = uuid(p.get("closureId"));
      fechaCierre = offsetDateTime(cierre.get("closedAt"));
    } else if ("CERRADA".equals(estado)) {
      // Anulación de check-in: CERRADA sin recibo. El CHECK exige importes; se guardan en 0.00 y
      // la ausencia de fecha_cierre la distingue de un cierre real (fuera de cobros y de la caja).
      BigDecimal zero = BigDecimal.ZERO.setScale(2);
      totalAlojamiento = zero;
      totalConsumos = zero;
      totalGeneral = zero;
      cobroAhora = zero;
    }

    jdbc.update(
        """
        INSERT INTO recepcion_projection
          (id_recepcion, id_hotel, id_habitacion, id_cliente, numero, nombre_cliente, fecha_entrada,
           fecha_salida, fecha_cierre, estado_estadia, precio_inicial, adelanto, penalidad,
           total_alojamiento, total_consumos, total_general, cobro_ahora, medio_pago_adelanto,
           medio_pago_cierre, closure_id, archivada, source_version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id_recepcion) DO UPDATE SET
          id_hotel = EXCLUDED.id_hotel,
          id_habitacion = EXCLUDED.id_habitacion,
          id_cliente = EXCLUDED.id_cliente,
          numero = EXCLUDED.numero,
          nombre_cliente = EXCLUDED.nombre_cliente,
          fecha_entrada = EXCLUDED.fecha_entrada,
          fecha_salida = EXCLUDED.fecha_salida,
          fecha_cierre = EXCLUDED.fecha_cierre,
          estado_estadia = EXCLUDED.estado_estadia,
          precio_inicial = EXCLUDED.precio_inicial,
          adelanto = EXCLUDED.adelanto,
          penalidad = EXCLUDED.penalidad,
          total_alojamiento = EXCLUDED.total_alojamiento,
          total_consumos = EXCLUDED.total_consumos,
          total_general = EXCLUDED.total_general,
          cobro_ahora = EXCLUDED.cobro_ahora,
          medio_pago_adelanto = EXCLUDED.medio_pago_adelanto,
          medio_pago_cierre = EXCLUDED.medio_pago_cierre,
          closure_id = EXCLUDED.closure_id,
          archivada = EXCLUDED.archivada,
          source_version = EXCLUDED.source_version
        WHERE recepcion_projection.source_version < EXCLUDED.source_version
        """,
        idRecepcion,
        p.path("idHotel").asInt(),
        p.path("idHabitacion").asInt(),
        p.path("idCliente").asInt(),
        p.path("numero").asText(),
        p.path("nombreCliente").asText(),
        LocalDate.parse(p.path("fechaEntrada").asText()),
        LocalDate.parse(p.path("fechaSalida").asText()),
        fechaCierre == null ? null : OffsetDateTime.ofInstant(fechaCierre.toInstant(), ZoneOffset.UTC),
        estado,
        p.path("precioInicial").decimalValue(),
        p.path("adelanto").decimalValue(),
        p.path("costoPenalidad").decimalValue(),
        totalAlojamiento,
        totalConsumos,
        totalGeneral,
        cobroAhora,
        textOrNull(p.get("medioPagoAdelanto")),
        medioPagoCierre,
        closureId,
        p.path("archivada").asBoolean(),
        version);
  }

  // ---------------------------------------------------------------------------
  // Stock y ventas
  // ---------------------------------------------------------------------------

  public void upsertStock(JsonNode p, long version) {
    jdbc.update(
        """
        INSERT INTO stock_projection (id_hotel, id_producto, nombre, precio, cantidad, umbral, estado, source_version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id_hotel, id_producto) DO UPDATE SET
          nombre = EXCLUDED.nombre,
          precio = EXCLUDED.precio,
          cantidad = EXCLUDED.cantidad,
          umbral = EXCLUDED.umbral,
          estado = EXCLUDED.estado,
          source_version = EXCLUDED.source_version
        WHERE stock_projection.source_version < EXCLUDED.source_version
        """,
        p.path("idHotel").asInt(),
        p.path("idProducto").asInt(),
        p.path("nombreProducto").asText(),
        p.path("precio").decimalValue(),
        p.path("cantidad").asInt(),
        p.path("umbralBajo").asInt(),
        p.path("estado").asBoolean(),
        version);
  }

  public void upsertVenta(JsonNode p, long version) {
    int idVenta = p.path("idVenta").asInt();
    int updated =
        jdbc.update(
            """
            INSERT INTO venta_projection
              (id_venta, id_hotel, id_recepcion, estado, estado_operacion, total, medio_pago,
               created_at, paid_at, source_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id_venta) DO UPDATE SET
              id_hotel = EXCLUDED.id_hotel,
              id_recepcion = EXCLUDED.id_recepcion,
              estado = EXCLUDED.estado,
              estado_operacion = EXCLUDED.estado_operacion,
              total = EXCLUDED.total,
              medio_pago = COALESCE(EXCLUDED.medio_pago, venta_projection.medio_pago),
              created_at = EXCLUDED.created_at,
              paid_at = COALESCE(EXCLUDED.paid_at, venta_projection.paid_at),
              source_version = EXCLUDED.source_version
            WHERE venta_projection.source_version < EXCLUDED.source_version
            """,
            idVenta,
            p.path("idHotel").asInt(),
            p.path("idRecepcion").asInt(),
            p.path("estado").asText(),
            p.path("estadoOperacion").asText(),
            p.path("total").decimalValue(),
            textOrNull(p.get("medioPago")),
            OffsetDateTime.parse(p.path("createdAt").asText()),
            offsetDateTime(p.get("paidAt")),
            version);
    if (updated == 0) {
      return;
    }
    jdbc.update("DELETE FROM venta_detalle_projection WHERE id_venta = ?", idVenta);
    for (JsonNode d : p.path("detalles")) {
      jdbc.update(
          """
          INSERT INTO venta_detalle_projection (id_venta, id_producto, nombre, cantidad, precio, subtotal)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          idVenta,
          d.path("idProducto").asInt(),
          d.path("nombreProducto").asText(),
          d.path("cantidad").asInt(),
          d.path("precioUnitario").decimalValue(),
          d.path("subTotal").decimalValue());
    }
  }

  // ---------------------------------------------------------------------------
  // Checkpoints y bootstrap
  // ---------------------------------------------------------------------------

  public void touchCheckpoint(String topic, int partition, long offset) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO projection_checkpoint
          (topic, partition, last_offset, last_event_at, updated_at, degraded)
        VALUES (?, ?, ?, ?, ?, false)
        ON CONFLICT (topic, partition) DO UPDATE SET
          last_offset = GREATEST(projection_checkpoint.last_offset, EXCLUDED.last_offset),
          last_event_at = COALESCE(EXCLUDED.last_event_at, projection_checkpoint.last_event_at),
          updated_at = now()
        """,
        topic,
        partition,
        offset,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public void ensureCheckpoint(String topic, int partition) {
    jdbc.update(
        """
        INSERT INTO projection_checkpoint
          (topic, partition, last_offset, last_event_at, updated_at, degraded)
        VALUES (?, ?, -1, NULL, now(), false)
        ON CONFLICT (topic, partition) DO NOTHING
        """,
        topic,
        partition);
  }

  public void markDegraded(String topic, int partition) {
    jdbc.update(
        """
        INSERT INTO projection_checkpoint
          (topic, partition, last_offset, last_event_at, updated_at, degraded)
        VALUES (?, ?, -1, NULL, now(), true)
        ON CONFLICT (topic, partition) DO UPDATE SET degraded = true, updated_at = now()
        """,
        topic,
        partition);
  }

  public Long checkpointOffset(String topic, int partition) {
    List<Long> offsets =
        jdbc.queryForList(
            "SELECT last_offset FROM projection_checkpoint WHERE topic = ? AND partition = ?",
            Long.class,
            topic,
            partition);
    return offsets.isEmpty() ? null : offsets.get(0);
  }

  public OffsetDateTime minUpdatedAt() {
    String placeholders = String.join(",", ReportingTopics.ALL.stream().map(t -> "?").toList());
    List<OffsetDateTime> value =
        jdbc.queryForList(
            "SELECT min(last_event_at) FROM projection_checkpoint WHERE topic IN ("
                + placeholders
                + ")",
            OffsetDateTime.class,
            ReportingTopics.ALL.toArray());
    return value.isEmpty() ? null : value.get(0);
  }

  public boolean hasDegradedCheckpoint() {
    Boolean degraded =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM projection_checkpoint WHERE degraded)", Boolean.class);
    return Boolean.TRUE.equals(degraded);
  }

  public void ensureBootstrap(String manifestJson) {
    jdbc.update(
        """
        INSERT INTO projection_bootstrap (id, estado, manifest)
        VALUES (1, 'CARGANDO', CAST(? AS jsonb))
        ON CONFLICT (id) DO NOTHING
        """,
        manifestJson);
  }

  public boolean isBootstrapReady() {
    List<String> estados =
        jdbc.queryForList("SELECT estado FROM projection_bootstrap WHERE id = 1", String.class);
    return !estados.isEmpty() && "LISTO".equals(estados.get(0));
  }

  public void markBootstrapReady() {
    jdbc.update(
        "UPDATE projection_bootstrap SET estado = 'LISTO', completed_at = now() WHERE id = 1");
  }

  public Map<String, Integer> counts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put(
        "personas",
        jdbc.queryForObject("SELECT count(*) FROM persona_projection", Integer.class));
    counts.put(
        "hoteles", jdbc.queryForObject("SELECT count(*) FROM hotel_projection", Integer.class));
    counts.put(
        "habitaciones",
        jdbc.queryForObject("SELECT count(*) FROM habitacion_projection", Integer.class));
    counts.put(
        "productosSede",
        jdbc.queryForObject("SELECT count(*) FROM stock_projection", Integer.class));
    counts.put("ventas", jdbc.queryForObject("SELECT count(*) FROM venta_projection", Integer.class));
    counts.put(
        "recepciones",
        jdbc.queryForObject("SELECT count(*) FROM recepcion_projection", Integer.class));
    return counts;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static UUID uuid(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    String text = node.asText();
    return text == null || text.isBlank() ? null : UUID.fromString(text);
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    String text = node.asText();
    return text == null || text.isBlank() ? null : text;
  }

  private static OffsetDateTime offsetDateTime(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return OffsetDateTime.parse(node.asText());
  }
}
