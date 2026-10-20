package pe.hotel.identity.persona;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import pe.hotel.identity.client.HotelClient;
import pe.hotel.identity.messaging.OutboxStore;
import pe.hotel.identity.platform.ApiIdempotencyRepository;

/**
 * Rutas humanas de personas y tipos de persona: el claim {@code sedes} llega del decoder como
 * {@code Long} y no puede castearse a {@code List<Integer>}; además, las mutaciones sin
 * {@code If-Match} deben llegar al servicio y responder 428, no 500.
 *
 * <p>El repositorio, la auditoría SQL y la idempotencia se simulan: aquí se comprueba el contrato de
 * la capa web y las reglas del servicio, no PostgreSQL.
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
class PersonaControllerTests {

  private static final String IDEM = "33333333-3333-4333-8333-333333333333";

  private static final String CUERPO_ACTUALIZAR =
      "{\"nombre\":\"Cliente\",\"apellido\":\"Uno\",\"correo\":\"cliente.uno@hotel.test\","
          + "\"idTipoPersona\":3,\"sedes\":[1],\"estado\":true}";

  @Autowired MockMvc mockMvc;

  @MockitoBean PersonaRepository personas;
  @MockitoBean HotelClient hotel;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean ApiIdempotencyRepository idempotencia;
  @MockitoBean OutboxStore outbox;
  @MockitoBean PlatformTransactionManager transaccion;

  @BeforeEach
  void preparar() {
    when(idempotencia.find(any(), any(), any(), any())).thenReturn(Optional.empty());
    when(personas.buscarPorId(100)).thenReturn(Optional.of(cliente()));
    when(personas.buscarTipoPersona(3))
        .thenReturn(Optional.of(new TipoPersona(3, "Cliente", "CLIENTE", true, 1L)));
    when(personas.listar(eq(3), eq("90000100"), eq(0), eq(20)))
        .thenReturn(List.of(cliente()));
    when(personas.contar(eq(3), eq("90000100"))).thenReturn(1L);
  }

  @Test
  void empleadoListaConDocumentoDevuelve200AunqueElClaimSedesVengaEnLong() throws Exception {
    mockMvc
        .perform(
            get("/api/persona/listar")
                .param("page", "0")
                .param("size", "20")
                .param("documento", "90000100")
                .with(empleado()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.personas.length()").value(1))
        .andExpect(jsonPath("$.data.personas[0].documento").value("90000100"))
        .andExpect(jsonPath("$.meta.totalElements").value(1));
  }

  @Test
  void empleadoListaSinDocumentoDevuelve400ValidationError() throws Exception {
    mockMvc
        .perform(get("/api/persona/listar").param("page", "0").param("size", "20").with(empleado()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.documento").isNotEmpty());
  }

  @Test
  void empleadoBuscaConDocumentoDevuelve200() throws Exception {
    mockMvc
        .perform(
            get("/api/persona/buscar/100")
                .param("documento", "90000100")
                .with(empleado()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.idPersona").value(100))
        .andExpect(jsonPath("$.data.documento").value("90000100"));
  }

  @Test
  void empleadoActualizaPersonaDevuelve403() throws Exception {
    mockMvc
        .perform(
            put("/api/persona/actualizar/100")
                .with(empleado())
                .header("Idempotency-Key", IDEM)
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_ACTUALIZAR))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void adminActualizaPersonaSinIfMatchDevuelve428() throws Exception {
    mockMvc
        .perform(
            put("/api/persona/actualizar/100")
                .with(admin())
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_ACTUALIZAR))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  @Test
  void adminEliminaPersonaSinIfMatchDevuelve428() throws Exception {
    mockMvc
        .perform(
            delete("/api/persona/eliminar/100")
                .with(admin())
                .header("Idempotency-Key", IDEM))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  @Test
  void adminActualizaTipoPersonaSinIfMatchDevuelve428() throws Exception {
    mockMvc
        .perform(
            put("/api/tipopersona/actualizar/3")
                .with(admin())
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Cliente\"}"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  private static RequestPostProcessor empleado() {
    return jwt()
        .jwt(
            j ->
                j.subject("2")
                    .claim("roles", List.of("EMPLEADO"))
                    .claim("sedes", List.of(1L))
                    .claim("tipoPersona", "Empleado"));
  }

  private static RequestPostProcessor admin() {
    return jwt()
        .jwt(
            j ->
                j.subject("1")
                    .claim("roles", List.of("ADMIN"))
                    .claim("sedes", List.of())
                    .claim("tipoPersona", "Administrador"));
  }

  private static Persona cliente() {
    return new Persona(
        100,
        "DNI",
        "90000100",
        "Cliente",
        "Uno",
        "cliente.uno@hotel.test",
        "hash",
        null,
        3,
        "CLIENTE",
        "Cliente",
        true,
        1L,
        OffsetDateTime.parse("2026-10-01T10:00:00Z"),
        List.of(1));
  }
}
