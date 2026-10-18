package pe.hotel.identity.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code service_client}. Solo se lee el hash del secreto: la comparación es
 * {@code BCryptPasswordEncoder.matches} en Java, nunca una comparación en SQL
 * (docs/especificacion/microservicios/identity/base-de-datos.md § Procedimientos y funciones).
 */
@Repository
public class ServiceClientRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public ServiceClientRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Cliente por {@code client_id}, exista o no (la estado se comprueba aparte). */
  public Optional<ServiceClient> buscar(String clientId) {
    List<Map<String, Object>> filas =
        jdbcTemplate.queryForList(
            """
            SELECT client_id, secret_hash, audiencias_scopes::text AS audiencias_scopes, estado
              FROM service_client
             WHERE client_id = ?
            """,
            clientId);
    if (filas.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> fila = filas.get(0);
    return Optional.of(
        new ServiceClient(
            (String) fila.get("client_id"),
            (String) fila.get("secret_hash"),
            audiencias((String) fila.get("audiencias_scopes")),
            Boolean.TRUE.equals(fila.get("estado"))));
  }

  private Map<String, List<String>> audiencias(String json) {
    try {
      return json == null ? Map.of() : mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("service_client.audiencias_scopes no es un objeto JSON", e);
    }
  }
}