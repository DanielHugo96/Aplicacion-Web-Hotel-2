package pe.hotel.hotel.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fija el contrato de configuración del circuit breaker de la demo: los cinco valores publicados en
 * {@code resilience4j.circuitbreaker.configs.default.*}, el TimeLimiter en 6000 ms y la exposición
 * de los endpoints de diagnóstico.
 *
 * <p>Las claves de primer nivel se descartan en silencio en Resilience4j 2.3.0, así que esta prueba
 * lee el {@code CircuitBreakerConfig} por defecto que consume el registro, no el YAML.
 * Ver docs/especificacion/infraestructura/configuracion.md § Comunes a todos los servicios y
 * § Health y readiness por servicio.
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
class ResilienciaDemoTests {

  @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
  @Autowired TimeLimiterRegistry timeLimiterRegistry;
  @Autowired MockMvc mockMvc;

  @Test
  void elBreakerPorDefectoUsaLosValoresPublicados() {
    CircuitBreakerConfig config = circuitBreakerRegistry.getDefaultConfig();
    assertThat(config.getSlidingWindowSize()).isEqualTo(10);
    assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10_000L);
    assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
  }

  @Test
  void elTimeLimiterPorDefectoEsperaSeisSegundos() {
    TimeLimiterConfig config = timeLimiterRegistry.getDefaultConfig();
    assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofMillis(6000));
  }

  @Test
  void losEndpointsDeDiagnosticoSeExponenSinToken() throws Exception {
    mockMvc
        .perform(get("/actuator/circuitbreakers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.circuitBreakers").exists());
    mockMvc.perform(get("/actuator/circuitbreakerevents")).andExpect(status().isOk());
  }

  @Test
  void lasRutasDeSaludSiguenSinTokenYLoSensiblesSiguenExigiendoToken() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/api/hotel/listar")).andExpect(status().isUnauthorized());
  }

  @Test
  void elConfigPorDefectoSeAplicaAUnIdDerivadoDeFeign() {
    // El id lo fija Feign (Feign.configKey(...) alfanumerizado): configs.default es el respaldo
    // que findCircuitBreakerProperties(id) aplica, así que cualquier id hereda los valores.
    CircuitBreakerConfig config =
        circuitBreakerRegistry.circuitBreaker("ReceptionClientbloquearint").getCircuitBreakerConfig();
    assertThat(config.getSlidingWindowSize()).isEqualTo(10);
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
  }
}
