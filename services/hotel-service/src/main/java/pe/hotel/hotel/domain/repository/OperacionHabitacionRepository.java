package pe.hotel.hotel.domain.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code operacion_habitacion} y a los procedimientos de aplicación/habilitación.
 * La tabla y las funciones las crea V5__hotel_operaciones.sql; el servicio no mapea la tabla con
 * JPA porque el CAS y los locks viven en la base.
 */
@Repository
public class OperacionHabitacionRepository {

  public record Operacion(
      UUID blockId,
      Integer idHabitacion,
      String tipo,
      String estado,
      Map<String, Object> request,
      int attempts,
      Instant nextAttemptAt) {}

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public OperacionHabitacionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Operacion> find(UUID blockId) {
    List<Operacion> rows =
        jdbcTemplate.query(
            "SELECT block_id, id_habitacion, tipo, estado, request::text AS request, attempts,"
                + " next_attempt_at FROM operacion_habitacion WHERE block_id = ?",
            this::map,
            blockId);
    return rows.stream().findFirst();
  }

  public Optional<Operacion> findActiveByHabitacion(Integer idHabitacion) {
    List<Operacion> rows =
        jdbcTemplate.query(
            "SELECT block_id, id_habitacion, tipo, estado, request::text AS request, attempts,"
                + " next_attempt_at FROM operacion_habitacion"
                + " WHERE id_habitacion = ? AND estado IN ('PREPARANDO','APLICADA')"
                + " ORDER BY block_id LIMIT 1",
            this::map,
            idHabitacion);
    return rows.stream().findFirst();
  }

  public boolean existsVigente(Integer idHabitacion) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM operacion_habitacion WHERE id_habitacion = ?"
                + " AND estado IN ('PREPARANDO','APLICADA'))",
            Boolean.class,
            idHabitacion);
    return Boolean.TRUE.equals(exists);
  }

  public boolean existsPreparandoByHabitacion(Integer idHabitacion) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM operacion_habitacion WHERE id_habitacion = ?"
                + " AND estado = 'PREPARANDO')",
            Boolean.class,
            idHabitacion);
    return Boolean.TRUE.equals(exists);
  }

  public boolean existsPreparandoByCategoria(Integer idCategoria) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM operacion_habitacion o"
                + " JOIN habitacion h ON h.id = o.id_habitacion"
                + " WHERE o.estado = 'PREPARANDO' AND h.id_categoria = ?)",
            Boolean.class,
            idCategoria);
    return Boolean.TRUE.equals(exists);
  }

  public boolean existsPreparandoByPiso(Integer idPiso, Integer idHotel) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM operacion_habitacion o"
                + " JOIN habitacion h ON h.id = o.id_habitacion"
                + " WHERE o.estado = 'PREPARANDO' AND h.id_piso = ? AND h.id_hotel = ?)",
            Boolean.class,
            idPiso,
            idHotel);
    return Boolean.TRUE.equals(exists);
  }

  public void crear(UUID blockId, Integer idHabitacion, String tipo, String requestJson) {
    jdbcTemplate.update(
        "INSERT INTO operacion_habitacion (block_id, id_habitacion, tipo, estado, request,"
            + " attempts, next_attempt_at) VALUES (?, ?, ?, 'PREPARANDO', CAST(? AS jsonb), 0, now())",
        blockId,
        idHabitacion,
        tipo,
        requestJson);
  }

  public Map<String, Object> aplicarBloqueo(UUID blockId, String tipo) {
    return call("SELECT sp_habitacion_aplicar_bloqueo(?, ?)::text", blockId, tipo);
  }

  public Map<String, Object> habilitar(UUID blockId) {
    return call("SELECT sp_habitacion_habilitar(?)::text", blockId);
  }

  public void rechazar(UUID blockId) {
    jdbcTemplate.update(
        "UPDATE operacion_habitacion SET estado = 'RECHAZADA'"
            + " WHERE block_id = ? AND estado = 'PREPARANDO'",
        blockId);
  }

  public List<Operacion> pendientes(int limit) {
    return jdbcTemplate.query(
        "SELECT block_id, id_habitacion, tipo, estado, request::text AS request, attempts,"
            + " next_attempt_at FROM operacion_habitacion"
            + " WHERE estado = 'PREPARANDO' AND next_attempt_at <= now()"
            + " ORDER BY next_attempt_at, block_id LIMIT ?",
        this::map,
        limit);
  }

  public void registrarIntento(UUID blockId, int attempts, Instant nextAttemptAt) {
    jdbcTemplate.update(
        "UPDATE operacion_habitacion SET attempts = ?, next_attempt_at = ?"
            + " WHERE block_id = ? AND estado = 'PREPARANDO'",
        attempts,
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        blockId);
  }

  /** Operaciones PREPARANDO cuya solicitud lleva más de los minutos indicados (alerta de RB3). */
  public List<Operacion> preparandoAntiguas(int minutos) {
    return jdbcTemplate.query(
        "SELECT block_id, id_habitacion, tipo, estado, request::text AS request, attempts,"
            + " next_attempt_at FROM operacion_habitacion"
            + " WHERE estado = 'PREPARANDO'"
            + " AND COALESCE((request->>'requestedAt')::timestamptz, next_attempt_at)"
            + "     < now() - (? * interval '1 minute')"
            + " ORDER BY next_attempt_at LIMIT 20",
        this::map,
        minutos);
  }

  private Map<String, Object> call(String sql, Object... args) {
    String json = jdbcTemplate.queryForObject(sql, String.class, args);
    try {
      return mapper.readValue(json, MAP_TYPE);
    } catch (Exception e) {
      throw new IllegalStateException("respuesta SQL no es JSON: " + json, e);
    }
  }

  private Operacion map(ResultSet rs, int rowNum) throws SQLException {
    Map<String, Object> request = Map.of();
    String requestJson = rs.getString("request");
    if (requestJson != null && !requestJson.isBlank()) {
      try {
        request = mapper.readValue(requestJson, MAP_TYPE);
      } catch (Exception ignored) {
        // La operación sigue siendo consultable aunque el request histórico no sea JSON.
      }
    }
    return new Operacion(
        rs.getObject("block_id", UUID.class),
        rs.getInt("id_habitacion"),
        rs.getString("tipo"),
        rs.getString("estado"),
        request,
        rs.getInt("attempts"),
        rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant());
  }
}
