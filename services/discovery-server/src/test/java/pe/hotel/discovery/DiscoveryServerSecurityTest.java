package pe.hotel.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Paso 2: Eureka en 8761, sin autorregistro single-node, registro protegido
 * con credenciales propias (sin credenciales 401) y salud interna.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"EUREKA_USERNAME=eureka-test", "EUREKA_PASSWORD=test-secreta"})
class DiscoveryServerSecurityTest {

  @LocalServerPort
  private int port;

  private TestRestTemplate rest;

  @Autowired
  private Environment env;

  @BeforeEach
  void setup() {
    rest = new TestRestTemplate();
  }

  private String base() {
    return "http://localhost:" + port;
  }

  @Test
  void sinAutorregistroSingleNode() {
    assertThat(env.getProperty("eureka.client.register-with-eureka")).isEqualTo("false");
    assertThat(env.getProperty("eureka.client.fetch-registry")).isEqualTo("false");
  }

  @Test
  void sinCredencialesResponde401() {
    ResponseEntity<String> respuesta = rest.getForEntity(base() + "/eureka/apps", String.class);
    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void conCredencialesRegistra() {
    ResponseEntity<String> respuesta = rest.withBasicAuth("eureka-test", "test-secreta")
        .getForEntity(base() + "/eureka/apps", String.class);
    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void saludInternaSinAuth() {
    ResponseEntity<String> respuesta =
        rest.getForEntity(base() + "/actuator/health/liveness", String.class);
    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
