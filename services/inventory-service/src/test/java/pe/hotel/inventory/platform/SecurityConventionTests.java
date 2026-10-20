package pe.hotel.inventory.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
          + ",org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
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

  // Caso SEC05: una identidad humana (incluso ADMIN) nunca puede invocar rutas /internal/**.
  @Test
  void tokenHumanoEnDescontarDevuelve403SinTocarStock() throws Exception {
    mockMvc
        .perform(
            post("/internal/stock/descontar")
                .with(
                    jwt()
                        .jwt(
                            j -> {
                              j.claim("roles", List.of("ADMIN"));
                              j.claim("sedes", List.of(1));
                            })
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idVenta\":1,\"idHotel\":1,\"items\":[{\"idProducto\":1,\"cantidad\":1}]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // Caso SEC05: token máquina sin scope stock:debit no puede debitar.
  @Test
  void tokenMaquinaSinScopeDebitEnDescontarDevuelve403() throws Exception {
    mockMvc
        .perform(
            post("/internal/stock/descontar")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "stock:read"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "SCOPE_stock:read")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idVenta\":1,\"idHotel\":1,\"items\":[{\"idProducto\":1,\"cantidad\":1}]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // Caso SEC05: token máquina con scope stock:debit llega al controlador (no es 401/403).
  @Test
  void tokenMaquinaConScopeDebitLlegaAlControlador() throws Exception {
    mockMvc
        .perform(
            post("/internal/stock/descontar")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "stock:debit"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "SCOPE_stock:debit")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idVenta\":1,\"idHotel\":1,\"items\":[{\"idProducto\":1,\"cantidad\":1}]}"))
        // Sin base de datos real el débito falla tras la autorización, pero nunca con 401/403.
        .andExpect(status().is5xxServerError());
  }
}
