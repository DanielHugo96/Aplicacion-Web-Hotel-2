package pe.hotel.reception.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a la tabla técnica {@code api_idempotency} descrita en
 * docs/especificacion/03-contrato-comun.md § Persistencia transversal. Clave única
 * {@code (actor, method, route, key)}.
 */
public class ApiIdempotencyRepository {

  private final JdbcTemplate jdbcTemplate;

  public ApiIdempotencyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record IdempotencyRow(
      String status, String requestHash, String resourceId, Integer httpStatus, String response) {}

  public Optional<IdempotencyRow> find(String actor, String method, String route, UUID key) {
    List<IdempotencyRow> rows =
        jdbcTemplate.query(
            """
            SELECT status, request_hash, resource_id, http_status, response::text AS response
              FROM api_idempotency
             WHERE actor = ? AND method = ? AND route = ? AND key = ?
            """,
            (rs, rowNum) ->
                new IdempotencyRow(
                    rs.getString("status"),
                    rs.getString("request_hash"),
                    rs.getString("resource_id"),
                    (Integer) rs.getObject("http_status"),
                    rs.getString("response")),
            actor,
            method,
            route,
            key);
    return rows.stream().findFirst();
  }

  /** Reserva la intención en estado {@code EN_CURSO}; si ya existía, deja la fila como estaba. */
  @Transactional
  public void reserve(String actor, String method, String route, UUID key, String requestHash) {
    jdbcTemplate.update(
        """
        INSERT INTO api_idempotency (actor, method, route, key, request_hash, status)
        VALUES (?, ?, ?, ?, ?, 'EN_CURSO')
        ON CONFLICT (actor, method, route, key) DO NOTHING
        """,
        actor,
        method,
        route,
        key,
        requestHash);
  }

  /** Persiste el resultado terminal para que los reintentos devuelvan el mismo estado y cuerpo. */
  @Transactional
  public void complete(
      String actor,
      String method,
      String route,
      UUID key,
      int httpStatus,
      String responseJson,
      String resourceId) {
    jdbcTemplate.update(
        """
        UPDATE api_idempotency
           SET status = 'COMPLETADA',
               http_status = ?,
               response = CAST(? AS jsonb),
               resource_id = ?
         WHERE actor = ? AND method = ? AND route = ? AND key = ?
        """,
        httpStatus,
        responseJson,
        resourceId,
        actor,
        method,
        route,
        key);
  }

  /** Libera una reserva que no dejó efecto (fallo no clasificado antes de persistir resultado). */
  @Transactional
  public void release(String actor, String method, String route, UUID key) {
    jdbcTemplate.update(
        "DELETE FROM api_idempotency WHERE actor = ? AND method = ? AND route = ? AND key = ?",
        actor,
        method,
        route,
        key);
  }
}
