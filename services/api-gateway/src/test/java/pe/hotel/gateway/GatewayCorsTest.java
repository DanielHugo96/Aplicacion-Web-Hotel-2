package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Paso 11 y caso SEC08: preflight permitido con origen de la allowlist y
 * headers If-Match + Idempotency-Key; origen ajeno sin Access-Control-Allow-Origin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "management.server.port=0"})
class GatewayCorsTest {

  @LocalServerPort
  private int port;

  private WebTestClient client;

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void preflightPermitidoConOrigenDeLaAllowlist() {
    client.options().uri("/api/hotel/listar")
        .header("Origin", "http://localhost:4200")
        .header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "If-Match, Idempotency-Key")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200")
        .expectHeader().exists("Access-Control-Allow-Methods");
  }

  @Test
  void preflightPermitidoParaElSegundoOrigen() {
    client.options().uri("/api/hotel/listar")
        .header("Origin", "http://localhost:4300")
        .header("Access-Control-Request-Method", "POST")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4300");
  }

  @Test
  void preflightSinJwtNoExigeToken() {
    // El preflight de origen permitido no pasa por el 401 aunque no lleve JWT.
    client.options().uri("/api/auth/login")
        .header("Origin", "http://localhost:4200")
        .header("Access-Control-Request-Method", "POST")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void origenAjenoNoRecibeAllowOrigin() {
    client.options().uri("/api/hotel/listar")
        .header("Origin", "http://evil.test")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectHeader().value("Access-Control-Allow-Origin", values -> assertThat(values).isNull());
  }

  @Test
  void sinWildcardDeOrigen() {
    client.options().uri("/api/hotel/listar")
        .header("Origin", "http://localhost:4200")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
  }
}
