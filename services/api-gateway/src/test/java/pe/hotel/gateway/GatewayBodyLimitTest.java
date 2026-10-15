package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Paso 7: cuerpo mayor a 1MiB responde 413 PAYLOAD_TOO_LARGE con envelope;
 * un cuerpo menor supera el filtro (llega al destino: 503 sin downstream,
 * nunca 413).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "management.server.port=0"})
class GatewayBodyLimitTest {

  @LocalServerPort
  private int port;

  private WebTestClient client;

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void cuerpoMayorA1MiBResponde413() {
    var result = client.post().uri("/api/auth/login")
        .header("Content-Type", "application/json")
        .bodyValue("{\"relleno\":\"" + "x".repeat(1048576 + 64) + "\"}")
        .exchange()
        // 413 numérico (RFC 9110 lo llama Content Too Large).
        .expectStatus().isEqualTo(413)
        .expectHeader().exists("X-Correlation-Id")
        .expectBody(String.class)
        .returnResult();
    assertThat(result.getResponseBody()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    assertThat(result.getResponseBody()).contains("\"data\":null");
    String correlation = result.getResponseHeaders().getFirst("X-Correlation-Id");
    assertThat(result.getResponseBody()).contains(correlation);
  }

  @Test
  void cuerpoMenorSuperaElFiltro() {
    client.post().uri("/api/auth/login")
        .header("Content-Type", "application/json")
        .bodyValue("{\"correo\":\"a@b.c\",\"clave\":\"012345678901\"}")
        .exchange()
        // Sin downstream el destino no existe: 503, pero nunca 413.
        .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
