package pe.hotel.identity.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Protocolo de idempotencia sin base de datos: replay, conflicto, en curso y alta nueva. */
@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTests {

  @Mock ApiIdempotencyRepository repository;

  private IdempotencyFilter filter;

  @BeforeEach
  void setUp() {
    filter = new IdempotencyFilter(repository);
  }

  private MockHttpServletRequest request(String key, String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/venta");
    request.setRequestURI("/api/venta");
    if (key != null) {
      request.addHeader("Idempotency-Key", key);
    }
    request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return request;
  }

  @Test
  void sinClaveDevuelve400() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request(null, "{}"), response, (req, res) -> {});
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentAsString()).contains("VALIDATION_ERROR");
    verifyNoInteractions(repository);
  }

  @Test
  void repiteResultadoTerminalSinEfecto() throws Exception {
    String key = UUID.randomUUID().toString();
    MockHttpServletRequest request = request(key, "{\"a\":1}");
    String hash = sha256("{\"a\":1}");
    when(repository.find(eq("anonymous"), eq("POST"), eq("/api/venta"), eq(UUID.fromString(key))))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow(
                    "COMPLETADA", hash, null, 201, "{\"success\":true,\"data\":{\"id\":7}}")));

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> {
      throw new AssertionError("no debe ejecutar el controlador");
    });

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentAsString()).contains("\"id\":7");
  }

  @Test
  void mismaClaveDistintoRequestDevuelve409() throws Exception {
    String key = UUID.randomUUID().toString();
    when(repository.find(eq("anonymous"), eq("POST"), eq("/api/venta"), eq(UUID.fromString(key))))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow("COMPLETADA", "otrohash", null, 201, "{}")));

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request(key, "{\"a\":1}"), response, (req, res) -> {});

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void enCursoDevuelve202() throws Exception {
    String key = UUID.randomUUID().toString();
    MockHttpServletRequest request = request(key, "{\"a\":1}");
    String hash = sha256("{\"a\":1}");
    when(repository.find(eq("anonymous"), eq("POST"), eq("/api/venta"), eq(UUID.fromString(key))))
        .thenReturn(
            Optional.of(
                new ApiIdempotencyRepository.IdempotencyRow("EN_CURSO", hash, "/api/venta/9", null, null)));

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getStatus()).isEqualTo(202);
    assertThat(response.getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void intencionNuevaReservaYCompleta() throws Exception {
    String key = UUID.randomUUID().toString();
    lenient()
        .when(repository.find(any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request(key, "{\"a\":1}"),
        response,
        (req, res) -> {
          ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
          res.getWriter().write("{\"success\":true,\"data\":{\"id\":7}}");
        });

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentAsString()).contains("\"id\":7");
    verify(repository).reserve(eq("anonymous"), eq("POST"), eq("/api/venta"), any(), any());
    verify(repository).complete(eq("anonymous"), eq("POST"), eq("/api/venta"), any(), eq(201), contains("\"id\":7"), any());
  }

  private static String sha256(String body) {
    String canonical = "POST\n/api/venta\n\n" + body + "\n";
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of()
          .formatHex(digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
