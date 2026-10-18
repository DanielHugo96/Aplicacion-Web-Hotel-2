package pe.hotel.identity.auth;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claves públicas del emisor: JWKS estándar {@code {keys:[...]}} sin envelope, porque así lo leen
 * los servicios que validan el JWT (docs/contracts/openapi/identity.yaml § JWKS).
 *
 * <p>Solo material público. La clave privada no sale de {@link SigningKeys} y vive fuera de Git.
 */
@RestController
public class JwksController {

  private final SigningKeys signingKeys;

  public JwksController(SigningKeys signingKeys) {
    this.signingKeys = signingKeys;
  }

  @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> jwks() {
    return signingKeys.jwks();
  }
}