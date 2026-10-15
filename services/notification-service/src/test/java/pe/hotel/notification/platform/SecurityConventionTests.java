package pe.hotel.notification.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Humo de la convención de seguridad y del envelope: 401 con envelope ante token ausente o firma
 * ajena, y {@code X-Correlation-Id} aceptado, generado y devuelto.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
          + ",org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "spring.datasource.hikari.initialization-fail-timeout=-1",
      "hotel.messaging.enabled=false"
    })
@AutoConfigureMockMvc
class SecurityConventionTests {

  private static final String CORRELATION = "11111111-1111-4111-8111-111111111111";

  @Autowired MockMvc mockMvc;

  @Test
  void rutaProtegidaSinTokenDevuelve401ConEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/hotel/listar"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(CorrelationId.HEADER))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.meta.correlationId").isNotEmpty());
  }

  @Test
  void correlacionAceptadaYDevuelta() throws Exception {
    mockMvc
        .perform(get("/api/hotel/listar").header(CorrelationId.HEADER, CORRELATION))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(CorrelationId.HEADER, CORRELATION))
        .andExpect(jsonPath("$.meta.correlationId").value(CORRELATION));
  }

  @Test
  void firmaAjenaDevuelve401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer header.payload.firma-ajena"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void saludNoRequiereToken() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void tokenHumanoSinScopeEnInternalDevuelve403() throws Exception {
    mockMvc
        .perform(
            get("/internal/hoteles/1")
                .with(
                    jwt()
                        .jwt(j -> j.claim("roles", java.util.List.of("ADMIN")))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void tokenMaquinaConScopePasaLaAutorizacion() throws Exception {
    mockMvc
        .perform(
            get("/internal/hoteles/1")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "hoteles:read"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "SCOPE_hoteles:read"))))
        // Sin controlador todavía sería 404, pero nunca 401/403: el scope de la ruta interna es válido.
        .andExpect(status().isNotFound());
  }
}
