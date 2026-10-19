package pe.hotel.notification.worker;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoría de las acciones del operador en {@code audit_log}
 * (docs/especificacion/03-contrato-comun.md § Persistencia transversal y
 * docs/especificacion/microservicios/notification/base-de-datos.md § Procedimientos). Nunca
 * guarda destinatario ni contenido privado.
 */
public class AuditLogStore {

  public static final String RETRY = "NOTIFICATION_RETRY";
  public static final String DISCARD = "NOTIFICATION_DISCARD";
  public static final String PURGE = "NOTIFICATION_PURGE";

  private final JdbcTemplate jdbcTemplate;

  public AuditLogStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Registra la acción con actor, comando y resultado (nunca datos privados). */
  @Transactional
  public void registrar(
      String actor,
      String action,
      String resourceId,
      Integer idHotel,
      UUID correlationId,
      String camposJson,
      String antes,
      String despues) {
    String cambios =
        "{\"campos\":"
            + camposJson
            + (antes == null ? "" : ",\"antes\":" + antes)
            + (despues == null ? "" : ",\"despues\":" + despues)
            + "}";
    jdbcTemplate.update(
        "INSERT INTO audit_log (id, actor, action, resource_id, id_hotel, correlation_id, changes)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::jsonb)",
        actor,
        action,
        resourceId,
        idHotel,
        correlationId == null ? UUID.randomUUID() : correlationId,
        cambios);
  }

  /** Cuenta las filas de auditoría de una acción (evidence de NOT03). */
  public int contar(String action, String resourceId) {
    Integer total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_log WHERE action = ? AND resource_id = ?",
            Integer.class,
            action,
            resourceId);
    return total == null ? 0 : total;
  }

  public java.util.List<String> ultimas(String action, int limite) {
    return jdbcTemplate.query(
        "SELECT actor || '|' || action || '|' || resource_id || '|' || changes::text AS fila"
            + " FROM audit_log WHERE action = ? ORDER BY occurred_at DESC LIMIT ?",
        (rs, rowNum) -> rs.getString("fila"),
        action,
        limite);
  }
}