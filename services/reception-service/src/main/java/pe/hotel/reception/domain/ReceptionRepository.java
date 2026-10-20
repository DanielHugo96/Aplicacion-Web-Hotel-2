package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.domain.dto.ResumenRecepcion;

/** Acceso JDBC a reception_db. Las mutaciones llaman a las funciones de V4 (lock + invariantes). */
@Repository
public class ReceptionRepository {

  public static final ZoneId LIMA = ZoneId.of("America/Lima");

  private static final String RECEPCION_COLUMNS =
      "r.id, r.id_hotel, r.id_habitacion, r.id_cliente, r.cliente_snapshot::text AS cliente_snapshot,"
          + " r.habitacion_snapshot::text AS habitacion_snapshot, r.tarifa, r.fecha_entrada,"
          + " r.fecha_salida, r.fecha_salida_confirmacion, r.precio_inicial, r.adelanto,"
          + " r.medio_pago_adelanto, r.precio_restante, r.total_pagado, r.costo_penalidad,"
          + " r.motivo_penalidad, r.observacion, r.estado_estadia, r.archivada, r.version";

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ReceptionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public record Gate(
      int idHabitacion,
      int idHotel,
      String estado,
      Integer idRecepcion,
      UUID cleaningCycleId,
      UUID blockId,
      long roomVersion,
      long hotelVersion) {}

  public Map<String, Object> checkin(String requestJson, String clienteJson, String habitacionJson) {
    String json =
        jdbc.queryForObject(
            "SELECT fn_checkin(CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb))::text",
            String.class,
            requestJson,
            clienteJson,
            habitacionJson);
    return read(json);
  }

  public Map<String, Object> actualizar(int id, LocalDate salida, String observacion, long version) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_recepcion_actualizar(?, ?, ?, ?)::text",
            String.class,
            id,
            salida,
            observacion,
            version);
    return read(json);
  }

  public Map<String, Object> bloquear(
      UUID blockId, int idHabitacion, int idHotel, String motivo, String hash) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_habitacion_bloquear(?, ?, ?, ?, ?)::text",
            String.class,
            blockId,
            idHabitacion,
            idHotel,
            motivo,
            hash);
    return read(json);
  }

  public Map<String, Object> admitir(UUID operationId, int idRecepcion, int idVenta, String tipo) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_operacion_admitir(?, ?, ?, ?)::text",
            String.class,
            operationId,
            idRecepcion,
            idVenta,
            tipo);
    return read(json);
  }

  public Map<String, Object> finalizar(
      UUID operationId, int idRecepcion, int idVenta, String tipo, String resultado) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_operacion_finalizar(?, ?, ?, ?, ?)::text",
            String.class,
            operationId,
            idRecepcion,
            idVenta,
            tipo,
            resultado);
    return read(json);
  }

  public void inicializarGate(int idHabitacion, int idHotel, long hotelVersion) {
    jdbc.queryForObject(
        "SELECT sp_gate_inicializar(?, ?, ?)::text", String.class, idHabitacion, idHotel, hotelVersion);
  }

  public void snapshotGate(int idHabitacion, int idHotel, long hotelVersion) {
    jdbc.queryForObject(
        "SELECT sp_gate_snapshot(?, ?, ?)::text", String.class, idHabitacion, idHotel, hotelVersion);
  }

  public void bootstrapBloqueado(
      int idHabitacion, int idHotel, long hotelVersion, UUID blockId, String hash) {
    int inserted =
        jdbc.update(
            "INSERT INTO room_gate (id_habitacion, id_hotel, estado, block_id, room_version,"
                + " hotel_version, updated_at) VALUES (?, ?, 'BLOQUEADA', ?, 0, ?, now())"
                + " ON CONFLICT (id_habitacion) DO NOTHING",
            idHabitacion,
            idHotel,
            blockId,
            hotelVersion);
    if (inserted == 1) {
      jdbc.update(
          "INSERT INTO bloqueo_habitacion (block_id, id_habitacion, id_hotel, motivo, request_hash,"
              + " estado) VALUES (?, ?, ?, ?, ?, 'ACTIVO') ON CONFLICT (block_id) DO NOTHING",
          blockId,
          idHabitacion,
          idHotel,
          "Bootstrap de habitación no admisible",
          hash);
    }
  }

  public Map<String, Object> liberarCiclo(int idHabitacion, UUID cleaningCycleId, long hotelVersion) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_gate_liberar_ciclo(?, ?, ?)::text",
            String.class,
            idHabitacion,
            cleaningCycleId,
            hotelVersion);
    return read(json);
  }

  public Map<String, Object> liberarBloqueo(int idHabitacion, UUID blockId, long hotelVersion) {
    String json =
        jdbc.queryForObject(
            "SELECT sp_gate_liberar_bloqueo(?, ?, ?)::text",
            String.class,
            idHabitacion,
            blockId,
            hotelVersion);
    return read(json);
  }

  public Optional<Gate> gate(int idHabitacion) {
    List<Gate> rows =
        jdbc.query(
            "SELECT id_habitacion, id_hotel, estado, id_recepcion, cleaning_cycle_id, block_id,"
                + " room_version, hotel_version FROM room_gate WHERE id_habitacion = ?",
            (rs, n) ->
                new Gate(
                    rs.getInt("id_habitacion"),
                    rs.getInt("id_hotel"),
                    rs.getString("estado"),
                    (Integer) rs.getObject("id_recepcion"),
                    rs.getObject("cleaning_cycle_id", UUID.class),
                    rs.getObject("block_id", UUID.class),
                    rs.getLong("room_version"),
                    rs.getLong("hotel_version")),
            idHabitacion);
    return rows.stream().findFirst();
  }

  public Optional<RecepcionView> findById(int id) {
    List<RecepcionView> rows =
        jdbc.query(
            "SELECT " + RECEPCION_COLUMNS + " FROM recepcion r WHERE r.id = ?",
            this::mapRecepcion,
            id);
    return rows.stream().findFirst();
  }

  public Optional<RecepcionView> findByHabitacionActiva(int idHabitacion) {
    List<RecepcionView> rows =
        jdbc.query(
            "SELECT "
                + RECEPCION_COLUMNS
                + " FROM recepcion r WHERE r.id_habitacion = ?"
                + " AND r.estado_estadia IN ('ACTIVA','CERRANDO')",
            this::mapRecepcion,
            idHabitacion);
    return rows.stream().findFirst();
  }

  public List<RecepcionView> listar(
      Integer idHotel, String estadoEstadia, Integer idCliente, int page, int size) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT ").append(RECEPCION_COLUMNS)
        .append(" FROM recepcion r WHERE 1 = 1");
    if (idHotel != null) {
      sql.append(" AND r.id_hotel = ?");
      args.add(idHotel);
    }
    if (idCliente != null) {
      sql.append(" AND r.id_cliente = ?");
      args.add(idCliente);
    }
    if (estadoEstadia != null) {
      sql.append(" AND r.estado_estadia = ?");
      args.add(estadoEstadia);
    }
    sql.append(" ORDER BY r.id ASC LIMIT ? OFFSET ?");
    args.add(size);
    args.add((long) page * size);
    return jdbc.query(sql.toString(), this::mapRecepcion, args.toArray());
  }

  public long count(Integer idHotel, String estadoEstadia, Integer idCliente) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM recepcion r WHERE 1 = 1");
    if (idHotel != null) {
      sql.append(" AND r.id_hotel = ?");
      args.add(idHotel);
    }
    if (idCliente != null) {
      sql.append(" AND r.id_cliente = ?");
      args.add(idCliente);
    }
    if (estadoEstadia != null) {
      sql.append(" AND r.estado_estadia = ?");
      args.add(estadoEstadia);
    }
    Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return total == null ? 0 : total;
  }

  public Optional<ResumenRecepcion> resumen(int id) {
    List<ResumenRecepcion> rows =
        jdbc.query(
            "SELECT id, id_hotel, id_cliente, id_habitacion, estado_estadia, version"
                + " FROM recepcion WHERE id = ?",
            (rs, n) ->
                new ResumenRecepcion(
                    rs.getInt("id"),
                    rs.getInt("id_hotel"),
                    rs.getInt("id_cliente"),
                    rs.getInt("id_habitacion"),
                    rs.getString("estado_estadia"),
                    rs.getLong("version")),
            id);
    return rows.stream().findFirst();
  }

  public boolean tieneOperacionesAbiertas(int idRecepcion) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM operacion_consumo WHERE id_recepcion = ? AND estado = 'ABIERTA'",
            Integer.class,
            idRecepcion);
    return count != null && count > 0;
  }

  private RecepcionView mapRecepcion(ResultSet rs, int rowNum) throws SQLException {
    try {
      JsonNode cliente = mapper.readTree(rs.getString("cliente_snapshot"));
      JsonNode habitacion = mapper.readTree(rs.getString("habitacion_snapshot"));
      String estado = rs.getString("estado_estadia");
      LocalDate salida = rs.getObject("fecha_salida", LocalDate.class);
      OffsetDateTime confirmacion = rs.getObject("fecha_salida_confirmacion", OffsetDateTime.class);
      return new RecepcionView(
          rs.getInt("id"),
          rs.getInt("id_hotel"),
          rs.getInt("id_cliente"),
          rs.getInt("id_habitacion"),
          habitacion.path("numero").asText(null),
          habitacion.path("categoriaNombre").asText(null),
          habitacion.path("pisoNombre").asText(null),
          habitacion.path("detalle").asText(null),
          rs.getBigDecimal("tarifa"),
          cliente.path("nombre").asText(null),
          cliente.path("apellido").asText(null),
          cliente.path("tipoDocumento").asText(null),
          cliente.path("documento").asText(null),
          cliente.path("correo").asText(null),
          rs.getObject("fecha_entrada", LocalDate.class),
          salida,
          confirmacion == null ? null : confirmacion.toInstant(),
          rs.getBigDecimal("precio_inicial"),
          rs.getBigDecimal("adelanto"),
          rs.getString("medio_pago_adelanto"),
          rs.getBigDecimal("precio_restante"),
          rs.getBigDecimal("total_pagado"),
          rs.getBigDecimal("costo_penalidad"),
          rs.getString("motivo_penalidad"),
          rs.getString("observacion"),
          "ACTIVA".equals(estado) || "CERRANDO".equals(estado),
          estado,
          rs.getBoolean("archivada"),
          "ACTIVA".equals(estado) && salida != null && salida.isBefore(LocalDate.now(LIMA)),
          rs.getLong("version"),
          null,
          null,
          null);
    } catch (Exception e) {
      throw new SQLException("snapshot JSONB inválido", e);
    }
  }

  private Map<String, Object> read(String json) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = mapper.readValue(json, LinkedHashMap.class);
      return value;
    } catch (Exception e) {
      throw new IllegalStateException("respuesta no JSON de la función: " + json, e);
    }
  }

  public static String hash(String payload) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of().formatHex(digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  public static Instant now() {
    return Instant.now();
  }

  public static BigDecimal money(Object value) {
    return value == null ? null : new BigDecimal(value.toString());
  }
}
