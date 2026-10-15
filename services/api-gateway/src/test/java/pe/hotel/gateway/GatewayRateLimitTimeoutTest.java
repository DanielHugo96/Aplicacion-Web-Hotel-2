package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pasos 8 y 9: rate limit público con Retry-After (las privadas no consumen
 * el contador) y timeouts por ruta (10s solo en reportes).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "management.server.port=0",
        "hotel.gateway.rate-limit.public-rpm=5"})
@DirtiesContext
class GatewayRateLimitTimeoutTest {

  @LocalServerPort
  private int port;

  private WebTestClient client;

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Autowired
  private RouteLocator routeLocator;

  @Test
  void peticion61Responde429ConRetryAfter() {
    for (int i = 0; i < 5; i++) {
      client.get().uri("/api/public/hoteles?page=0&size=20")
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
    var result = client.get().uri("/api/public/hoteles?page=0&size=20")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader().exists("Retry-After")
        .expectBody(String.class)
        .returnResult();
    assertThat(result.getResponseBody()).contains("\"code\":\"RATE_LIMITED\"");
  }

  @Test
  void rutaPrivadaNoConsumeElContadorPublico() {
    // Agota otro bucket público (login) y verifica que una privada sigue
    // en 401 (no 429): las privadas no consumen el contador público.
    for (int i = 0; i < 5; i++) {
      client.post().uri("/api/auth/registro")
          .header("Content-Type", "application/json")
          .bodyValue("{\"correo\":\"t@t.co\",\"clave\":\"012345678901\"}")
          .exchange();
    }
    client.get().uri("/api/hotel/listar")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void timeoutsPorRuta() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();
    for (Route route : routes) {
      Object read = route.getMetadata().get("response-timeout");
      Object connect = route.getMetadata().get("connect-timeout");
      if ("reporting".equals(route.getId())) {
        assertThat(String.valueOf(read)).isEqualTo("10000");
      } else {
        assertThat(String.valueOf(read)).isEqualTo("5000");
      }
      assertThat(String.valueOf(connect)).isEqualTo("2000");
    }
  }
}
