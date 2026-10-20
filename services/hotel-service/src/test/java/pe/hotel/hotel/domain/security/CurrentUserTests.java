package pe.hotel.hotel.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * El claim {@code sedes} lo entrega el decoder como {@code Long}; el alcance del EMPLEADO no puede
 * quedar vacío por eso. Ver docs/especificacion/03-contrato-comun.md § Identidad y sedes.
 */
class CurrentUserTests {

  private final CurrentUser currentUser = new CurrentUser();

  @AfterEach
  void limpiarContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void sedesComoLongSeConviertenYConservanElAlcance() {
    autenticar(List.of(1L), "ROLE_EMPLEADO");

    assertThat(currentUser.sedes()).containsExactly(1);
    assertThat(currentUser.puedeAccederASede(1)).isTrue();
    assertThat(currentUser.puedeAccederASede(2)).isFalse();
  }

  @Test
  void sedesComoIntegerTambienSeAceptan() {
    autenticar(List.of(3), "ROLE_EMPLEADO");

    assertThat(currentUser.sedes()).containsExactly(3);
    assertThat(currentUser.puedeAccederASede(3)).isTrue();
  }

  @Test
  void claimAusenteDevuelveListaVacia() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuer("hotel-identity")
            .subject("2")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(120))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"))));

    assertThat(currentUser.sedes()).isEmpty();
  }

  private void autenticar(List<?> sedes, String authority) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuer("hotel-identity")
            .subject("2")
            .claim("sedes", sedes)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(120))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
  }
}
