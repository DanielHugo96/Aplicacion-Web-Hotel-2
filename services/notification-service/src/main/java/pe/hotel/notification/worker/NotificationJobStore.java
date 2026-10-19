package pe.hotel.notification.worker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a {@code notification_job} por los tres procedimientos fijados en
 * docs/especificacion/microservicios/notification/base-de-datos.md § Procedimientos, más las
 * operaciones de la CLI del operador. Los valores configurables por entorno (lease y máximo de
 * intentos) viajan como GUC de sesión para no cambiar las firmas de la spec.
 */
public class NotificationJobStore {

  /** SQLSTATE propio de la spec: plantilla desconocida o no permitida. */
  public static final String SQLSTATE_PLANTILLA = "NT010";

  /** SQLSTATE propio: mismo commandId con distinto payload_hash. */
  public static final String SQLSTATE_HASH = "NT011";

  /** SQLSTATE propio: payload sin los campos obligatorios de su plantilla. */
  public static final String SQLSTATE_PAYLOAD = "NT012";

  private static final String COLUMNS =
      "command_id, tipo, id_hotel, id_persona, id_recepcion, destinatario, template_version,"
          + " variables::text AS variables, estado, attempts, next_attempt_at, lease_until,"
          + " lease_token, message_id, created_at, sent_at, last_error_code";

  private final JdbcTemplate jdbcTemplate;

  public NotificationJobStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Inbox + job en una sola transacción local. Idempotente por {@code commandId}: repetir el mismo
   * comando no crea otro job; el mismo id con distinto {@code payload_hash} es error permanente.
   * Los SQLSTATE propios de la spec se traducen a error permanente (van directo a la DLQ); el resto
   * de fallos de base son transitorios y usan los reintentos TTL de Rabbit.
   */
  @Transactional
  public void encolar(UUID commandId, String tipo, String cuerpoJson) {
    try {
      jdbcTemplate.update("CALL sp_notificacion_encolar(?, ?, ?::jsonb)", commandId, tipo, cuerpoJson);
    } catch (DataAccessException ex) {
      throw traducir(ex);
    }
  }

  /** Traduce el SQLSTATE de los procedimientos: permanente o transitorio, nunca ambiguo. */
  static RuntimeException traducir(DataAccessException ex) {
    Throwable causa = ex.getCause();
    while (causa != null) {
      if (causa instanceof SQLException sql) {
        String estado = sql.getSQLState();
        if (SQLSTATE_PLANTILLA.equals(estado)
            || SQLSTATE_HASH.equals(estado)
            || SQLSTATE_PAYLOAD.equals(estado)) {
          return new NotificationPermanentException(sql.getMessage());
        }
        break;
      }
      causa = causa.getCause();
    }
    return ex;
  }

  /**
   * Toma un job por worker con {@code FOR UPDATE SKIP LOCKED} y lease de la configuración. La
   * transacción termina antes del envío SMTP: el correo ocurre fuera de ella.
   */
  @Transactional
  public Optional<NotificationJob> tomar(Instant ahora, int leaseSeconds) {
    jdbcTemplate.queryForObject(
        "SELECT set_config('hotel.notification.lease_seconds', ?, true)",
        String.class,
        String.valueOf(leaseSeconds));
    List<NotificationJob> rows =
        jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM fn_notificacion_tomar(?)",
            mapper(),
            OffsetDateTime.ofInstant(ahora, java.time.ZoneOffset.UTC));
    return rows.stream().findFirst();
  }

  /** Resultado por CAS: solo aplica si el lease sigue vigente y el token coincide. */
  @Transactional
  public void resultado(
      UUID commandId, UUID leaseToken, boolean exito, String error, int maxAttempts) {
    jdbcTemplate.queryForObject(
        "SELECT set_config('hotel.notification.max_attempts', ?, true)",
        String.class,
        String.valueOf(maxAttempts));
    jdbcTemplate.update(
        "CALL sp_notificacion_resultado(?, ?, ?, ?)", commandId, leaseToken, exito, error);
  }

  /** Reintento manual: reencola preservando {@code commandId} y sin pasar por alto el inbox. */
  @Transactional
  public int reintentar(UUID commandId) {
    return jdbcTemplate.update(
        "UPDATE notification_job j SET estado = 'PENDIENTE', next_attempt_at = now(), attempts = 0,"
            + " last_error_code = NULL, lease_token = NULL, lease_until = NULL"
            + " WHERE j.command_id = ? AND j.estado = 'ERROR'"
            + " AND EXISTS (SELECT 1 FROM inbox i WHERE i.message_id = j.command_id)",
        commandId);
  }

  /** Error permanente del rendimiento del job: ERROR sin gastar los reintentos SMTP. */
  @Transactional
  public int marcarError(UUID commandId, UUID leaseToken, String codigo) {
    return jdbcTemplate.update(
        "UPDATE notification_job SET estado = 'ERROR', last_error_code = left(?, 80),"
            + " lease_token = NULL, lease_until = NULL"
            + " WHERE command_id = ? AND estado = 'ENVIANDO' AND lease_token = ?",
        codigo,
        commandId,
        leaseToken);
  }

  /** Descarte: estado terminal con motivo, sin borrar el histórico. */
  @Transactional
  public int descartar(UUID commandId, String motivo) {
    return jdbcTemplate.update(
        "UPDATE notification_job SET estado = 'DESCARTADO',"
            + " last_error_code = left('DESCARTADO: ' || ?, 80),"
            + " lease_token = NULL, lease_until = NULL"
            + " WHERE command_id = ? AND estado <> 'DESCARTADO'",
        motivo,
        commandId);
  }

  /**
   * Purga por retención: destinatario y variables a NULL en ENVIADA anterior a la fecha. Nunca
   * toca PENDIENTE, ERROR ni DESCARTADO; conserva commandId, estado y marcas.
   */
  @Transactional
  public int purgar(Instant antesDe) {
    return jdbcTemplate.update(
        "UPDATE notification_job SET destinatario = NULL, variables = NULL"
            + " WHERE estado = 'ENVIADA' AND sent_at IS NOT NULL AND sent_at < ?"
            + " AND (destinatario IS NOT NULL OR variables IS NOT NULL)",
        OffsetDateTime.ofInstant(antesDe, java.time.ZoneOffset.UTC));
  }

  public List<NotificationJob> listar(String estado, int limite) {
    return jdbcTemplate.query(
        "SELECT " + COLUMNS + " FROM notification_job WHERE estado = ?"
            + " ORDER BY next_attempt_at LIMIT ?",
        mapper(),
        estado,
        limite);
  }

  public Optional<NotificationJob> buscar(UUID commandId) {
    return jdbcTemplate
        .query("SELECT " + COLUMNS + " FROM notification_job WHERE command_id = ?", mapper(), commandId)
        .stream()
        .findFirst();
  }

  public Map<String, Integer> contarPorEstado() {
    return jdbcTemplate.query(
        "SELECT estado, count(*) AS total FROM notification_job GROUP BY estado",
        rs -> {
          Map<String, Integer> conteo = new java.util.LinkedHashMap<>();
          while (rs.next()) {
            conteo.put(rs.getString("estado"), rs.getInt("total"));
          }
          return conteo;
        });
  }

  private static RowMapper<NotificationJob> mapper() {
    return (ResultSet rs, int rowNum) -> map(rs);
  }

  private static NotificationJob map(ResultSet rs) throws SQLException {
    return new NotificationJob(
        rs.getObject("command_id", UUID.class),
        rs.getString("tipo"),
        (Integer) rs.getObject("id_hotel"),
        rs.getInt("id_persona"),
        (Integer) rs.getObject("id_recepcion"),
        rs.getString("destinatario"),
        rs.getInt("template_version"),
        rs.getString("variables"),
        rs.getString("estado"),
        rs.getInt("attempts"),
        rs.getObject("next_attempt_at", OffsetDateTime.class),
        rs.getObject("lease_until", OffsetDateTime.class),
        rs.getObject("lease_token", UUID.class),
        rs.getString("message_id"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("sent_at", OffsetDateTime.class),
        rs.getString("last_error_code"));
  }
}