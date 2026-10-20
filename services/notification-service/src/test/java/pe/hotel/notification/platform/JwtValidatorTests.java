package pe.hotel.notification.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida la política de {@code iss}/{@code aud} de la convención: aud incorrecta o issuer distinto se
 * rechazan (caso `SEC06`), y la audiencia humana, el destino corto que emite identity o el nombre
 * largo del propio servicio se aceptan.
 */
class JwtValidatorTests {

  private final OAuth2TokenValidator<Jwt> validator =
      new SecurityConfig().hotelJwtValidator("hotel-identity", "notification-service");

  private static Jwt token(String issuer, List<String> audiences) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer(issuer)
        .subject("1")
        .audience(audiences)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(120))
        .build();
  }

  @Test
  void aceptaAudienciaHumana() {
    assertThat(validator.validate(token("hotel-identity", List.of("hotel-api"))).hasErrors()).isFalse();
  }

  @Test
  void aceptaDestinoCorto() {
    assertThat(validator.validate(token("hotel-identity", List.of("notification"))).hasErrors()).isFalse();
  }

  @Test
  void aceptaNombreLargoDelServicio() {
    assertThat(validator.validate(token("hotel-identity", List.of("notification-service"))).hasErrors())
        .isFalse();
  }

  @Test
  void rechazaAudienciaIncorrecta() {
    assertThat(validator.validate(token("hotel-identity", List.of("otro-servicio"))).hasErrors()).isTrue();
  }

  @Test
  void rechazaIssuerIncorrecto() {
    assertThat(validator.validate(token("hotel-otro", List.of("hotel-api"))).hasErrors()).isTrue();
  }
}
