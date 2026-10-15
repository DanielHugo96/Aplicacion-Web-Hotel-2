package pe.hotel.inventory.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a la tabla técnica {@code outbox} (docs/especificacion/03-contrato-comun.md
 * § Persistencia transversal). Una fila por destino con {@code published_at} propio.
 */
public class OutboxStore {

  private static final String COLUMNS =
      "id, destination, channel, partition_key, aggregate_id, aggregate_version, type, "
          + "payload::text AS payload, created_at, attempts, next_attempt_at";

  private final JdbcTemplate jdbcTemplate;

  public OutboxStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Reserva un lote de pendientes del destino con {@code FOR UPDATE SKIP LOCKED} sobre el índice
   * parcial de pendientes. Solo devuelve la versión más baja pendiente de cada agregado (orden por
   * agregado): la N+1 no se publica antes que la N. Las filas reservadas posponen
   * {@code next_attempt_at} por el arriendo indicado para que otro ciclo no las repita.
   */
  @Transactional
  public List<OutboxMessage> claim(Destination destination, int batchSize, Duration lease) {
    List<OutboxMessage> rows =
        jdbcTemplate.query(
            "SELECT "
                + COLUMNS
                + " FROM outbox o WHERE o.published_at IS NULL AND o.destination = ?"
                + " AND o.next_attempt_at <= now()"
                + " AND NOT EXISTS (SELECT 1 FROM outbox p WHERE p.published_at IS NULL"
                + " AND p.destination = o.destination AND p.aggregate_id = o.aggregate_id"
                + " AND p.aggregate_version < o.aggregate_version)"
                + " ORDER BY o.created_at LIMIT ? FOR UPDATE SKIP LOCKED",
            OutboxStore::map,
            destination.name(),
            batchSize);
    if (!rows.isEmpty()) {
      OffsetDateTime leaseUntil = OffsetDateTime.now(ZoneOffset.UTC).plus(lease);
      for (OutboxMessage row : rows) {
        jdbcTemplate.update("UPDATE outbox SET next_attempt_at = ? WHERE id = ?", leaseUntil, row.id());
      }
    }
    return rows;
  }

  /** Marca la fila como publicada (nunca sin confirmación del broker). */
  @Transactional
  public void markPublished(UUID id) {
    jdbcTemplate.update("UPDATE outbox SET published_at = now() WHERE id = ?", id);
  }

  /** Registra un intento fallido y pospone el siguiente según el backoff. */
  @Transactional
  public void markAttempt(UUID id, Instant nextAttemptAt) {
    jdbcTemplate.update(
        "UPDATE outbox SET attempts = attempts + 1, next_attempt_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
        id);
  }

  /**
   * Encola un mensaje en la misma transacción local que la mutación. Lo usan los planes de dominio
   * al confirmar un hecho; la plataforma solo aporta el transporte.
   */
  @Transactional
  public void enqueue(
      Destination destination,
      String channel,
      String partitionKey,
      String aggregateId,
      long aggregateVersion,
      String type,
      String payload) {
    jdbcTemplate.update(
        "INSERT INTO outbox (id, destination, channel, partition_key, aggregate_id,"
            + " aggregate_version, type, payload, created_at, published_at, attempts, next_attempt_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), now(), NULL, 0, now())",
        UUID.randomUUID(),
        destination.name(),
        channel,
        partitionKey,
        aggregateId,
        aggregateVersion,
        type,
        payload);
  }

  /**
   * Filas que superan los umbrales de alerta de RB4: {@code attempts} por encima del límite o
   * pendientes más de los minutos configurados.
   */
  public List<OutboxMessage> overdue(int alertAttempts, int alertPendingMinutes) {
    return jdbcTemplate.query(
        "SELECT "
            + COLUMNS
            + " FROM outbox WHERE published_at IS NULL"
            + " AND (attempts > ? OR created_at < now() - (? * interval '1 minute'))"
            + " ORDER BY next_attempt_at LIMIT 20",
        OutboxStore::map,
        alertAttempts,
        alertPendingMinutes);
  }

  private static OutboxMessage map(ResultSet rs, int rowNum) throws SQLException {
    return new OutboxMessage(
        rs.getObject("id", UUID.class),
        Destination.valueOf(rs.getString("destination")),
        rs.getString("channel"),
        rs.getString("partition_key"),
        rs.getString("aggregate_id"),
        rs.getLong("aggregate_version"),
        rs.getString("type"),
        rs.getString("payload"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getInt("attempts"),
        rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant());
  }
}
