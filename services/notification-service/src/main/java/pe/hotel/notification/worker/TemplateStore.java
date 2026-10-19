package pe.hotel.notification.worker;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lectura de la allowlist de plantillas {@code template}
 * (docs/especificacion/microservicios/notification/base-de-datos.md § Tablas). Solo existen las
 * tres plantillas v1 sembradas; cualquier otra combinación es error permanente.
 */
public class TemplateStore {

  private final JdbcTemplate jdbcTemplate;

  public TemplateStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Template> buscar(String tipo, int version) {
    List<Template> filas =
        jdbcTemplate.query(
            "SELECT tipo, version, subject, body_text FROM template WHERE tipo = ? AND version = ?",
            (rs, rowNum) ->
                new Template(
                    rs.getString("tipo"),
                    rs.getInt("version"),
                    rs.getString("subject"),
                    rs.getString("body_text")),
            tipo,
            version);
    return filas.stream().findFirst();
  }

  public record Template(String tipo, int version, String subject, String bodyText) {}
}