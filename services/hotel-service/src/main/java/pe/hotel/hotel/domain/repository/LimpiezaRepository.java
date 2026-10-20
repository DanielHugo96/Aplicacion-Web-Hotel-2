package pe.hotel.hotel.domain.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code limpieza} y a los procedimientos del ciclo. No hay entidad JPA: el lock
 * habitación → tarea y las transiciones viven en V5__hotel_operaciones.sql.
 */
@Repository
public class LimpiezaRepository {

  public record Limpieza(
      UUID cleaningCycleId,
      Integer idHabitacion,
      Integer idHotel,
      Integer idRecepcion,
      Long roomVersion,
      String estado,
      Instant createdAt,
      Instant completedAt,
      Long version) {}

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final String COLUMNS =
      "l.cleaning_cycle_id, l.id_habitacion, h.id_hotel, l.id_recepcion, l.room_version,"
          + " l.estado, l.created_at, l.completed_at, l.version";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public LimpiezaRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<String, Object> solicitar(
      UUID cleaningCycleId, Integer idHabitacion, Integer idRecepcion, Long roomVersion) {
    return call(
        "SELECT sp_limpieza_solicitar(?, ?, ?, ?)::text",
        cleaningCycleId,
        idHabitacion,
        idRecepcion,
        roomVersion);
  }

  public Map<String, Object> completar(UUID cleaningCycleId, Integer idHabitacion, Long version) {
    return call("SELECT sp_limpieza_completar(?, ?, ?)::text", cleaningCycleId, idHabitacion, version);
  }

  public Optional<Limpieza> find(UUID cleaningCycleId) {
    List<Limpieza> rows =
        jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM limpieza l JOIN habitacion h ON h.id = l.id_habitacion"
                + " WHERE l.cleaning_cycle_id = ?",
            this::map,
            cleaningCycleId);
    return rows.stream().findFirst();
  }

  public Optional<Limpieza> findPendiente(Integer idHabitacion) {
    List<Limpieza> rows =
        jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM limpieza l JOIN habitacion h ON h.id = l.id_habitacion"
                + " WHERE l.id_habitacion = ? AND l.estado = 'PENDIENTE'",
            this::map,
            idHabitacion);
    return rows.stream().findFirst();
  }

  public List<Limpieza> listar(Integer idHotel, String estado, int page, int size) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT " + COLUMNS + " FROM limpieza l JOIN habitacion h ON h.id = l.id_habitacion"
                + " WHERE h.id_hotel = ?");
    args.add(idHotel);
    if (estado != null) {
      sql.append(" AND l.estado = ?");
      args.add(estado);
    }
    sql.append(" ORDER BY l.created_at ASC, l.cleaning_cycle_id ASC LIMIT ? OFFSET ?");
    args.add(size);
    args.add((long) page * size);
    return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
  }

  public long contar(Integer idHotel, String estado) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql =
        new StringBuilder(
            "SELECT count(*) FROM limpieza l JOIN habitacion h ON h.id = l.id_habitacion"
                + " WHERE h.id_hotel = ?");
    args.add(idHotel);
    if (estado != null) {
      sql.append(" AND l.estado = ?");
      args.add(estado);
    }
    Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
    return total == null ? 0 : total;
  }

  private Map<String, Object> call(String sql, Object... args) {
    String json = jdbcTemplate.queryForObject(sql, String.class, args);
    try {
      return mapper.readValue(json, MAP_TYPE);
    } catch (Exception e) {
      throw new IllegalStateException("respuesta SQL no es JSON: " + json, e);
    }
  }

  private Limpieza map(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime completed = rs.getObject("completed_at", OffsetDateTime.class);
    return new Limpieza(
        rs.getObject("cleaning_cycle_id", UUID.class),
        rs.getInt("id_habitacion"),
        rs.getInt("id_hotel"),
        rs.getInt("id_recepcion"),
        rs.getLong("room_version"),
        rs.getString("estado"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        completed == null ? null : completed.toInstant(),
        rs.getLong("version"));
  }
}
