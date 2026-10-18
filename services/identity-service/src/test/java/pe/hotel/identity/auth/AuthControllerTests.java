package pe.hotel.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import pe.hotel.identity.messaging.Destination;
import pe.hotel.identity.messaging.OutboxStore;
import pe.hotel.identity.persona.Persona;
import pe.hotel.identity.persona.PersonaRepository;
import pe.hotel.identity.platform.ApiIdempotencyRepository;
import pe.hotel.identity.ratelimit.RateLimiter;

/**
 * Superficie REST de /api/auth y /internal/auth/token contra repositorios simulados: contrato,
 * envelope, códigos del catálogo e idempotencia. La base real y los brokers no hacen falta aquí; su
 * comportamiento se comprueba en la corrida de aceptación del plan.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
          + ",org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "spring.datasource.hikari.initialization-fail-timeout=-1",
      "hotel.messaging.enabled=false",
      // Clave fija para que las pruebas puedan calcular el request_hash HMAC que espera el filtro.
      "hotel.identity.idempotency-hmac-key=clave-hmac-de-prueba"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTests {

  private static final String IDEM = "22222222-2222-4222-8222-222222222222";

  @Autowired MockMvc mockMvc;
  @Autowired PasswordEncoder encoder;
  @Autowired RateLimiter rateLimiter;

  @MockitoBean PersonaRepository personas;
  @MockitoBean ServiceClientRepository clientes;
  @MockitoBean ApiIdempotencyRepository idempotencia;
  @MockitoBean OutboxStore outbox;
  // La CI no levanta PostgreSQL: la transacción real se comprueba en la corrida de aceptación.
  @MockitoBean PlatformTransactionManager transaccion;

  private final ObjectMapper mapper = new ObjectMapper();

  private String hashDemo;

  @BeforeEach
  void preparar() {
    // El rate limit vive en memoria y es por proceso: cada prueba empieza con la ventana limpia.
    rateLimiter.olvidar();
    hashDemo = Fixtures.hash(encoder, Fixtures.CLAVE);
    when(personas.buscarPorCorreo(anyString())).thenReturn(Optional.empty());
  }

  private Persona admin() {
    return Fixtures.persona(1, "admin@hotel.test", "ADMIN", List.of(), hashDemo);
  }

  private static String basic(String usuario, String secreto) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((usuario + ":" + secreto).getBytes(StandardCharsets.UTF_8));
  }

  private static JsonNode claims(String token) throws Exception {
    String payload = token.split("\\.")[1];
    return new ObjectMapper()
        .readTree(Base64.getUrlDecoder().decode(payload + "=".repeat((4 - payload.length() % 4) % 4)));
  }

  /**
   * El request_hash que el filtro calcula para este request: HMAC del canónico con la clave fija de
   * la clase. Con el mismo valor, el filtro reproduce el resultado terminal guardado.
   */
  private static String hashDe(String metodo, String ruta, String cuerpo) {
    return pe.hotel.identity.platform.IdempotencyHashes.hmacSha256Hex(
        "clave-hmac-de-prueba", metodo + "\n" + ruta + "\n\n" + cuerpo + "\n");
  }

  /** El claim `aud` es un string cuando tiene un solo elemento; el contrato lo fija a `hotel-api`. */
  private static String audiencia(JsonNode claims) {
    JsonNode aud = claims.path("aud");
    return aud.isArray() ? aud.get(0).asText() : aud.asText();
  }

  private String loginComoAdmin() throws Exception {
    when(personas.buscarPorCorreo("admin@hotel.test")).thenReturn(Optional.of(admin()));
    String cuerpo =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"correo\":\"admin@hotel.test\",\"clave\":\"" + Fixtures.CLAVE + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(cuerpo).path("data").path("token").asText();
  }

  // ---------------------------------------------------------------- login

  @Test
  void loginDevuelveLaFormaLegacyConTokenDeQuinceMinutos() throws Exception {
    when(personas.buscarPorCorreo("admin@hotel.test")).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"Admin@Hotel.Test \",\"clave\":\"" + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.idPersona").value(1))
        .andExpect(jsonPath("$.data.tipoPersona").value("Administrador"))
        .andExpect(jsonPath("$.data.expiresIn").value(900))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(jsonPath("$.data.clave").doesNotExist())
        .andExpect(jsonPath("$.data.claveHash").doesNotExist())
        .andExpect(jsonPath("$.meta.correlationId").isNotEmpty());

    // El correo se normaliza a minúsculas antes de buscar.
    verify(personas).buscarPorCorreo("admin@hotel.test");
  }

  @Test
  void loginEmiteLosClaimsFijadosPorElContrato() throws Exception {
    String token = loginComoAdmin();
    JsonNode claims = claims(token);
    assertThat(claims.path("iss").asText()).isEqualTo("hotel-identity");
    assertThat(audiencia(claims)).isEqualTo("hotel-api");
    assertThat(claims.path("sub").asText()).isEqualTo("1");
    assertThat(claims.path("tipoPersona").asText()).isEqualTo("Administrador");
    assertThat(claims.path("roles").get(0).asText()).isEqualTo("ADMIN");
    assertThat(claims.path("sedes").isArray()).isTrue();
    assertThat(claims.path("jti").asText()).isNotBlank();
    assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(900);
  }

  @Test
  void loginConClaveIncorrectaYConCorreoInexistenteDanElMismo401() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"admin@hotel.test\",\"clave\":\"no-es-la-clave\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error.fields").isEmpty());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"nadie@hotel.test\",\"clave\":\"no-es-la-clave\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos."));
  }

  // -------------------------------------------------------------- registro

  @Test
  void registroConIdTipoPersonaEnElCuerpoEs400YNoCreaLaFila() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000199\",\"nombre\":\"Alta\","
                        + "\"apellido\":\"Con Rol\",\"correo\":\"alta@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\",\"idTipoPersona\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verify(personas, never()).insertar(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void registroValidoFuerzaClienteYSinSedes() throws Exception {
    when(personas.insertar(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(120);
    when(personas.buscarPorId(120))
        .thenReturn(
            Optional.of(Fixtures.persona(120, "cliente.nuevo@hotel.test", "CLIENTE", List.of(), "h")));

    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000199\",\"nombre\":\"Cliente\","
                        + "\"apellido\":\"Nuevo\",\"correo\":\"Cliente.Nuevo@Hotel.Test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.tipoPersona").value("Cliente"))
        .andExpect(jsonPath("$.data.idTipoPersona").value(3))
        .andExpect(jsonPath("$.data.sedes").isEmpty())
        .andExpect(jsonPath("$.data.correo").value("cliente.nuevo@hotel.test"))
        .andExpect(jsonPath("$.data.clave").doesNotExist());

    ArgumentCaptor<Integer> rol = ArgumentCaptor.forClass(Integer.class);
    verify(personas).insertar(any(), any(), any(), any(), any(), any(), rol.capture());
    assertThat(rol.getValue()).isEqualTo(3);
  }

  @Test
  void registroConCorreoODocumentoExistenteEs409SinTomarLaCuenta() throws Exception {
    when(personas.existeCorreo("repetido@hotel.test")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000200\",\"nombre\":\"Repetido\","
                        + "\"apellido\":\"Idem\",\"correo\":\"repetido@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
        .andExpect(jsonPath("$.error.fields.correo").isNotEmpty());

    verify(personas, never()).insertar(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void registroSinIdempotencyKeyEs400() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000201\",\"nombre\":\"Sin Clave\","
                        + "\"apellido\":\"Idem\",\"correo\":\"sinclave@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields['Idempotency-Key']").isNotEmpty());
  }

  @Test
  void registroConLaMismaClaveYOtroCuerpoEs409IdempotencyConflict() throws Exception {
    when(idempotencia.find(any(), eq("POST"), eq("/api/auth/registro"), any()))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow(
                    "COMPLETADA", "otro-hash", null, 201, "{}")));

    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000202\",\"nombre\":\"Otro\","
                        + "\"apellido\":\"Cuerpo\",\"correo\":\"otro@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

    verify(personas, never()).insertar(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void registroRepetidoConLaMismaClaveDevuelveEl201GuardadoSinCrearOtraFila() throws Exception {
    String cuerpo =
        "{\"success\":true,\"message\":\"OK\",\"data\":{\"idPersona\":120},"
            + "\"meta\":{\"correlationId\":\"11111111-1111-4111-8111-111111111111\"}}";
    String peticion =
        "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000203\",\"nombre\":\"Repetido\","
            + "\"apellido\":\"Idem\",\"correo\":\"repetido@hotel.test\",\"clave\":\""
            + Fixtures.CLAVE
            + "\"}";
    when(idempotencia.find(any(), eq("POST"), eq("/api/auth/registro"), any()))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow(
                    "COMPLETADA",
                    hashDe("POST", "/api/auth/registro", peticion),
                    null,
                    201,
                    cuerpo)));

    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.idPersona").value(120));

    verify(personas, never()).insertar(any(), any(), any(), any(), any(), any(), anyInt());
    verify(outbox, never()).enqueue(any(), any(), any(), any(), any(Long.class), any(), any());
  }

  @Test
  void registroEncolaLaBienvenidaConElPayloadDelEsquema() throws Exception {
    when(personas.insertar(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(120);
    when(personas.buscarPorId(120))
        .thenReturn(
            Optional.of(Fixtures.persona(120, "cliente.nuevo@hotel.test", "CLIENTE", List.of(), "h")));

    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000199\",\"nombre\":\"Cliente\","
                        + "\"apellido\":\"Nuevo\",\"correo\":\"cliente.nuevo@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isCreated());

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(outbox)
        .enqueue(
            eq(Destination.RABBIT),
            eq("notificacion.bienvenida"),
            isNull(),
            eq("120"),
            any(Long.class),
            eq("NotificacionBienvenida"),
            payload.capture());

    // El payload tiene que validar contra el JSON Schema publicado del comando.
    new pe.hotel.identity.messaging.MessageValidator("schemas")
        .validate(
            new pe.hotel.identity.messaging.OutboxMessage(
                UUID.randomUUID(),
                Destination.RABBIT,
                "notificacion.bienvenida",
                null,
                "120",
                1,
                "NotificacionBienvenida",
                payload.getValue(),
                java.time.Instant.now(),
                0,
                java.time.Instant.now()));
  }

  // -------------------------------------------------------------------- me

  @Test
  void meSinTokenEs401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void meDevuelveLaPersonaDelSubConETag() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(get("/api/auth/me").with(jwt().jwt(j -> j.subject("1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.idPersona").value(1))
        .andExpect(jsonPath("$.data.clave").doesNotExist())
        .andExpect(header().string("ETag", "\"1\""));
  }

  @Test
  void meIgnoraCabecerasDeIdentidadFalsificadas() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));
    when(personas.buscarPorId(100))
        .thenReturn(
            Optional.of(Fixtures.persona(100, "cliente.uno@hotel.test", "CLIENTE", List.of(), "h")));

    mockMvc
        .perform(
            get("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("X-User-Id", "100")
                .header("X-Role", "CLIENTE")
                .header("X-Hotel-Id", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.idPersona").value(1));

    verify(personas, never()).buscarPorId(100);
  }

  // ------------------------------------------------------------ perfil (V)

  @Test
  void putPerfilConIfMatchVigenteDevuelve200ConVersionIncrementada() throws Exception {
    Persona actualizado =
        new Persona(
            1, "DNI", "90000001", "Nombre Editado", "Apellido", "admin@hotel.test", "hash", null, 1,
            "ADMIN", "Administrador", true, 2L, java.time.OffsetDateTime.parse("2026-10-01T10:00:00Z"),
            List.of());
    when(personas.actualizarPerfil(eq(1), any(), any(), any(), eq(1L))).thenReturn(1);
    when(personas.leerDespuesDeActualizar(1)).thenReturn(actualizado);

    mockMvc
        .perform(
            put("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre Editado\",\"apellido\":\"Apellido\",\"fotoUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(2))
        .andExpect(header().string("ETag", "\"2\""));
  }

  @Test
  void putPerfilSinIfMatchEs428() throws Exception {
    mockMvc
        .perform(
            put("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\"}"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  @Test
  void putPerfilConVersionAjenaEs412() throws Exception {
    when(personas.actualizarPerfil(eq(1), any(), any(), any(), eq(9L))).thenReturn(0);

    mockMvc
        .perform(
            put("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .header("If-Match", "\"9\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_FAILED"))
        .andExpect(jsonPath("$.error.fields['If-Match']").isNotEmpty());
  }

  @Test
  void putPerfilConSedesORolEnElCuerpoEs400PorJsonCerrado() throws Exception {
    mockMvc
        .perform(
            put("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"sedes\":[1]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc
        .perform(
            put("/api/auth/me")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"idTipoPersona\":1}"))
        .andExpect(status().isBadRequest());
  }

  // --------------------------------------------------------------- clave

  @Test
  void cambioDeClaveValidoDevuelve200ConDataNullYGuardaElHash() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"claveActual\":\"" + Fixtures.CLAVE + "\",\"claveNueva\":\"clave-nueva-9999\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"data\":null")));

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(personas).actualizarClave(eq(1), hash.capture());
    assertThat(hash.getValue()).isNotEqualTo("clave-nueva-9999").startsWith("$2");
    assertThat(encoder.matches("clave-nueva-9999", hash.getValue())).isTrue();
  }

  @Test
  void cambioDeClaveConClaveActualIncorrectaEs400() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"claveActual\":\"no-es-la-clave\",\"claveNueva\":\"clave-nueva-9999\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"))
        .andExpect(jsonPath("$.error.fields.claveActual").isNotEmpty());

    verify(personas, never()).actualizarClave(anyInt(), anyString());
  }

  @Test
  void claveNuevaFueraDePoliticaEs400PasswordPolicy() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveActual\":\"" + Fixtures.CLAVE + "\",\"claveNueva\":\"corta\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY"))
        .andExpect(jsonPath("$.error.fields.claveNueva").isNotEmpty());
  }

  @Test
  void claveNuevaIgualALaActualEs400ValidationError() throws Exception {
    when(personas.buscarPorId(1)).thenReturn(Optional.of(admin()));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"claveActual\":\"" + Fixtures.CLAVE + "\",\"claveNueva\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.claveNueva").isNotEmpty());
  }

  @Test
  void reintentoDelCambioDeClaveDevuelveEl200GuardadoSinVolverATocarLaClave() throws Exception {
    String peticion =
        "{\"claveActual\":\"" + Fixtures.CLAVE + "\",\"claveNueva\":\"clave-nueva-9999\"}";
    when(idempotencia.find(any(), eq("PUT"), eq("/api/auth/password"), any()))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow(
                    "COMPLETADA",
                    hashDe("PUT", "/api/auth/password", peticion),
                    null,
                    200,
                    "{\"success\":true,\"message\":\"OK\",\"data\":null}")));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(jwt().jwt(j -> j.subject("1")))
                .header("Idempotency-Key", IDEM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(peticion))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"data\":null")));

    verify(personas, never()).actualizarClave(anyInt(), anyString());
  }

  // --------------------------------------------------------------- social

  @Test
  void socialRetiradaEs410SinIdempotencyKey() throws Exception {
    mockMvc
        .perform(post("/api/auth/social"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.error.code").value("SOCIAL_LOGIN_DISABLED"))
        .andExpect(jsonPath("$.error.fields.ruta").value("/api/auth/social"));
  }

  // ------------------------------------------------------------------ jwks

  @Test
  void jwksDevuelveSoloMaterialPublico() throws Exception {
    String cuerpo =
        mockMvc
            .perform(get("/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode clave = mapper.readTree(cuerpo).path("keys").get(0);
    assertThat(clave.path("kty").asText()).isEqualTo("RSA");
    assertThat(clave.path("use").asText()).isEqualTo("sig");
    assertThat(clave.path("alg").asText()).isEqualTo("RS256");
    assertThat(clave.path("kid").asText()).isNotBlank();
    assertThat(clave.path("n").asText()).isNotBlank();
    assertThat(clave.path("e").asText()).isNotBlank();
    assertThat(clave.has("d")).as("la clave privada nunca se publica").isFalse();
    assertThat(cuerpo).doesNotContain("PRIVATE");
  }

  // --------------------------------------------------------- token maquina

  @Test
  void tokenMaquinaEmiteUnTokenLimitadoPorLaAllowlistDelCliente() throws Exception {
    when(clientes.buscar(Fixtures.CLIENTE_ID))
        .thenReturn(
            Optional.of(
                Fixtures.cliente(
                    Fixtures.CLIENTE_ID,
                    Fixtures.hash(encoder, Fixtures.SECRETO_CLIENTE),
                    Map.of("identity", List.of("clientes:read")))));

    String cuerpo =
        mockMvc
            .perform(
                post("/internal/auth/token")
                    .header("Authorization", basic(Fixtures.CLIENTE_ID, Fixtures.SECRETO_CLIENTE))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("audience", "identity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(300))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode token = claims(mapper.readTree(cuerpo).path("access_token").asText());
    assertThat(token.path("sub").asText()).isEqualTo(Fixtures.CLIENTE_ID);
    assertThat(audiencia(token)).isEqualTo("identity");
    assertThat(token.path("scope").asText()).isEqualTo("clientes:read");
    assertThat(token.path("exp").asLong() - token.path("iat").asLong()).isEqualTo(300);
    assertThat(token.has("roles")).as("un token maquina no lleva roles humanos").isFalse();
  }

  @Test
  void tokenMaquinaSinEnvelope() throws Exception {
    when(clientes.buscar(Fixtures.CLIENTE_ID))
        .thenReturn(
            Optional.of(
                Fixtures.cliente(
                    Fixtures.CLIENTE_ID,
                    Fixtures.hash(encoder, Fixtures.SECRETO_CLIENTE),
                    Map.of("identity", List.of("clientes:read")))));

    String cuerpo =
        mockMvc
            .perform(
                post("/internal/auth/token")
                    .header("Authorization", basic(Fixtures.CLIENTE_ID, Fixtures.SECRETO_CLIENTE))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("audience", "identity"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(mapper.readTree(cuerpo).has("success")).isFalse();
    assertThat(mapper.readTree(cuerpo).has("meta")).isFalse();
  }

  @Test
  void tokenMaquinaConBasicInvalidoAusenteOConJwtHumanoEs401() throws Exception {
    when(clientes.buscar(Fixtures.CLIENTE_ID))
        .thenReturn(
            Optional.of(
                Fixtures.cliente(
                    Fixtures.CLIENTE_ID,
                    Fixtures.hash(encoder, Fixtures.SECRETO_CLIENTE),
                    Map.of("identity", List.of("clientes:read")))));

    mockMvc
        .perform(
            post("/internal/auth/token")
                .header("Authorization", basic(Fixtures.CLIENTE_ID, "secreto-incorrecto"))
                .param("grant_type", "client_credentials")
                .param("audience", "identity"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    mockMvc
        .perform(
            post("/internal/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("audience", "identity"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/internal/auth/token")
                .with(jwt().jwt(j -> j.subject("1")))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("audience", "identity"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokenMaquinaConAudienciaNoPermitidaOGrantIncorrectoEs400() throws Exception {
    when(clientes.buscar(Fixtures.CLIENTE_ID))
        .thenReturn(
            Optional.of(
                Fixtures.cliente(
                    Fixtures.CLIENTE_ID,
                    Fixtures.hash(encoder, Fixtures.SECRETO_CLIENTE),
                    Map.of("identity", List.of("clientes:read")))));

    mockMvc
        .perform(
            post("/internal/auth/token")
                .header("Authorization", basic(Fixtures.CLIENTE_ID, Fixtures.SECRETO_CLIENTE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("audience", "sales"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.audience").isNotEmpty());

    mockMvc
        .perform(
            post("/internal/auth/token")
                .header("Authorization", basic(Fixtures.CLIENTE_ID, Fixtures.SECRETO_CLIENTE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "password")
                .param("audience", "identity"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fields.grant_type").isNotEmpty());
  }

  // ------------------------------------------------------------ rate limit

  @Test
  void loginSeLimitaACincoIntentosPorMinutoYPorCuenta() throws Exception {
    for (int intento = 1; intento <= 5; intento++) {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"correo\":\"admin@hotel.test\",\"clave\":\"incorrecta\"}"))
          .andExpect(status().isUnauthorized());
    }
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"admin@hotel.test\",\"clave\":\"incorrecta\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

    // Otra cuenta desde la misma IP sigue teniendo su propio presupuesto.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"otro@hotel.test\",\"clave\":\"incorrecta\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registroSeLimitaATresIntentosPorMinutoYPorIp() throws Exception {
    when(personas.insertar(any(), any(), any(), any(), any(), any(), anyInt())).thenAnswer(llamada -> 130 + llamada.getArgument(1, String.class).hashCode() % 10);
    when(personas.buscarPorId(anyInt()))
        .thenAnswer(
            llamada ->
                Optional.of(
                    Fixtures.persona(
                        llamada.getArgument(0),
                        "limite@hotel.test",
                        "CLIENTE",
                        List.of(),
                        "hash")));

    for (int intento = 1; intento <= 3; intento++) {
      when(idempotencia.find(any(), any(), any(), any())).thenReturn(Optional.empty());
      mockMvc
          .perform(
              post("/api/auth/registro")
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"tipoDocumento\":\"DNI\",\"documento\":\"9000019"
                          + intento
                          + "\",\"nombre\":\"Limite\",\"apellido\":\"Prueba\",\"correo\":\"limite"
                          + intento
                          + "@hotel.test\",\"clave\":\""
                          + Fixtures.CLAVE
                          + "\"}"))
          .andExpect(status().isCreated());
    }
    mockMvc
        .perform(
            post("/api/auth/registro")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipoDocumento\":\"DNI\",\"documento\":\"90000199\",\"nombre\":\"Limite\","
                        + "\"apellido\":\"Prueba\",\"correo\":\"limite4@hotel.test\",\"clave\":\""
                        + Fixtures.CLAVE + "\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }
}