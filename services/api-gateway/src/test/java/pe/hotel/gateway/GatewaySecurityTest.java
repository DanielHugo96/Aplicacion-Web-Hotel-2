package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pasos 5, 6, 9, 10 y 12 con JWT RS256 reales: 401 sin token o con token
 * inválido/expirado (envelope UNAUTHENTICATED), públicas sin 401, 404 en ruta
 * no documentada, 503 con envelope cuando el destino no existe, correlationId
 * de ida y vuelta y actuator fuera del 8080.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "management.server.port=0"})
class GatewaySecurityTest {

  private static HttpServer jwksServer;
  private static RSAKey rsaKey;
  private static String jwksUrl;

  @LocalServerPort
  private int port;

  private WebTestClient client;

  @BeforeAll
  static void sirveJwks() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    String jwks = new JWKSet(rsaKey.toPublicJWK()).toString();
    byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
    jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
    jwksServer.createContext("/jwks.json", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    jwksServer.start();
    jwksUrl = "http://localhost:" + jwksServer.getAddress().getPort() + "/jwks.json";
  }

  @AfterAll
  static void detieneJwks() {
    if (jwksServer != null) {
      jwksServer.stop(0);
    }
  }

  @DynamicPropertySource
  static void jwks(DynamicPropertyRegistry registry) {
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUrl);
    registry.add("hotel.gateway.jwt.jwk-set-uri", () -> jwksUrl);
    registry.add("hotel.gateway.jwt.issuer", () -> "hotel-identity");
  }

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  private static String token(String audience, Instant expira) throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject("1")
        .issuer("hotel-identity")
        .audience(audience)
        .issueTime(Date.from(Instant.now().minusSeconds(60)))
        .expirationTime(Date.from(expira))
        .jwtID(UUID.randomUUID().toString())
        .claim("tipoPersona", "Administrador")
        .claim("roles", List.of("ADMIN"))
        .build();
    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(rsaKey));
    return jwt.serialize();
  }

  @Test
  void privadaSinTokenResponde401ConEnvelope() {
    var body = client.get().uri("/api/hotel/listar")
        .exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().exists("X-Correlation-Id")
        .expectBody(String.class)
        .returnResult().getResponseBody();
    assertThat(body).contains("\"success\":false");
    assertThat(body).contains("\"data\":null");
    assertThat(body).contains("\"code\":\"UNAUTHENTICATED\"");
    assertThat(body).contains("correlationId");
    assertThat(body).doesNotContain("stacktrace");
  }

  @Test
  void tokenValidoSinDownstreamResponde503ConEnvelope() throws Exception {
    var result = client.get().uri("/api/hotel/listar")
        .header("Authorization", "Bearer " + token("hotel-api", Instant.now().plusSeconds(300)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody(String.class)
        .returnResult();
    assertThat(result.getResponseBody()).contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"");
  }

  @Test
  void tokenInvalidoResponde401() {
    client.get().uri("/api/hotel/listar")
        .header("Authorization", "Bearer invalido.manipulado.firma")
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class).value(body ->
            assertThat(body).contains("\"code\":\"UNAUTHENTICATED\""));
  }

  @Test
  void tokenConAudienceManipuladaResponde401() throws Exception {
    client.get().uri("/api/hotel/listar")
        .header("Authorization", "Bearer " + token("otro-servicio", Instant.now().plusSeconds(300)))
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class).value(body ->
            assertThat(body).contains("\"code\":\"UNAUTHENTICATED\""));
  }

  @Test
  void tokenExpiradoResponde401() throws Exception {
    client.get().uri("/api/hotel/listar")
        .header("Authorization", "Bearer " + token("hotel-api", Instant.now().minusSeconds(60)))
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class).value(body ->
            assertThat(body).contains("\"code\":\"UNAUTHENTICATED\""));
  }

  @Test
  void publicasNoDevuelven401PorFaltaDeToken() {
    // Sin downstream (Eureka deshabilitado) responden 503, nunca 401.
    client.get().uri("/api/public/hoteles?page=0&size=20")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    client.post().uri("/api/auth/login")
        .header("Content-Type", "application/json")
        .bodyValue("{\"correo\":\"a@b.c\",\"clave\":\"012345678901\"}")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void rutaNoDocumentadaResponde404() throws Exception {
    client.get().uri("/api/noexiste/xyz")
        .header("Authorization", "Bearer " + token("hotel-api", Instant.now().plusSeconds(300)))
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  void correlationIdSeDevuelveYViajaEnMeta() {
    String enviado = UUID.randomUUID().toString();
    var result = client.get().uri("/api/hotel/listar")
        .header("X-Correlation-Id", enviado)
        .exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().valueEquals("X-Correlation-Id", enviado)
        .expectBody(String.class)
        .returnResult();
    assertThat(result.getResponseBody()).contains(enviado);
  }

  @Test
  void correlationIdSeGeneraCuandoFalta() {
    var result = client.get().uri("/api/hotel/listar")
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class)
        .returnResult();
    String devuelto = result.getResponseHeaders().getFirst("X-Correlation-Id");
    assertThat(devuelto).isNotBlank();
    assertThat(result.getResponseBody()).contains(devuelto);
    // UUID válido.
    UUID.fromString(devuelto);
  }

  @Test
  void actuatorNoExpuestoEnElPuertoExterno() {
    client.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isNotFound();
  }
}
