package pe.hotel.identity.persona;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Enrutado y seguridad de la lectura máquina de cliente vigente: la ruta publicada por la spec y
 * el contrato es {@code /internal/clientes/{id}}, sin el prefijo {@code /api} de las rutas humanas.
 * La ruta antigua con prefijo deja de existir.
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
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ClienteInternoControllerTests {

  private static final ClienteVigente VIGENTE =
      new ClienteVigente(
          100,
          "DNI",
          "90000100",
          "Cliente",
          "Uno",
          "cliente.uno@hotel.test",
          true,
          3,
          1L);

  @Autowired MockMvc mockMvc;

  @MockitoBean PersonaService personas;

  @BeforeEach
  void preparar() {
    when(personas.clienteVigente(100)).thenReturn(VIGENTE);
  }

  @Test
  void rutaPublicadaConScopeClientesReadDevuelve200ConElClienteVigente() throws Exception {
    mockMvc
        .perform(
            get("/internal/clientes/100")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "clientes:read"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_clientes:read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.idPersona").value(100))
        .andExpect(jsonPath("$.data.tipoDocumento").value("DNI"))
        .andExpect(jsonPath("$.data.documento").value("90000100"))
        .andExpect(jsonPath("$.data.nombre").value("Cliente"))
        .andExpect(jsonPath("$.data.apellido").value("Uno"))
        .andExpect(jsonPath("$.data.correo").value("cliente.uno@hotel.test"))
        .andExpect(jsonPath("$.data.estado").value(true))
        .andExpect(jsonPath("$.data.idTipoPersona").value(3))
        .andExpect(jsonPath("$.data.version").value(1))
        .andExpect(jsonPath("$.meta.correlationId").isNotEmpty());
  }

  @Test
  void tokenMaquinaSinElScopeClientesReadDevuelve403Forbidden() throws Exception {
    mockMvc
        .perform(
            get("/internal/clientes/100")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "hoteles:read"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_hoteles:read"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void peticionSinTokenDevuelve401Unauthenticated() throws Exception {
    mockMvc
        .perform(get("/internal/clientes/100"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void rutaAntiguaConPrefijoApiDevuelve404() throws Exception {
    mockMvc
        .perform(
            get("/api/internal/clientes/100")
                .with(
                    jwt()
                        .jwt(j -> j.claim("scope", "clientes:read"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_clientes:read"))))
        .andExpect(status().isNotFound());
  }
}
