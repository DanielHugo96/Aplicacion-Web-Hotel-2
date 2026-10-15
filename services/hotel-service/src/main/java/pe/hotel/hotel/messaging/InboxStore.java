package pe.hotel.hotel.messaging;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a la tabla técnica {@code inbox} (docs/especificacion/03-contrato-comun.md
 * § Persistencia transversal). El consumidor registra aquí el {@code messageId} en la misma
 * transacción que el efecto, antes del ack, para deduplicar por {@code (consumer, message_id)}.
 */
public class InboxStore {

  private final JdbcTemplate jdbcTemplate;

  public InboxStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Registra el mensaje para el consumidor. Devuelve {@code true} si es nuevo (debe aplicarse el
   * efecto) y {@code false} si ya se procesó (duplicado descartado por inbox).
   */
  @Transactional
  public boolean claim(String consumer, UUID messageId) {
    int inserted =
        jdbcTemplate.update(
            "INSERT INTO inbox (consumer, message_id, processed_at) VALUES (?, ?, now())"
                + " ON CONFLICT (consumer, message_id) DO NOTHING",
            consumer,
            messageId);
    return inserted == 1;
  }

  public boolean alreadyProcessed(String consumer, UUID messageId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inbox WHERE consumer = ? AND message_id = ?",
            Integer.class,
            consumer,
            messageId);
    return count != null && count > 0;
  }

  /** Purga operativa de inbox; nunca borra pendientes (solo filas ya procesadas y antiguas). */
  @Transactional
  public int purgeOlderThan(Instant threshold) {
    return jdbcTemplate.update(
        "DELETE FROM inbox WHERE processed_at < ?", OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC));
  }
}
