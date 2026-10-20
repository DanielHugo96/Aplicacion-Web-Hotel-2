package pe.hotel.reception.platform;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Degradación visible de identity (token máquina y clientes). No entra en el grupo readiness: la
 * operación en curso no se bloquea por una dependencia caída, pero quien llama a /internal sí
 * recibe 503 antes de crear estado durable.
 */
@Component("identity")
public class IdentityHealthIndicator implements HealthIndicator {

  private final RestClient client;
  private final String baseUrl;

  public IdentityHealthIndicator(
      @Value("${hotel.feign.url.identity:http://localhost:8081}") String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
    factory.setReadTimeout((int) Duration.ofSeconds(1).toMillis());
    this.client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    this.baseUrl = baseUrl;
  }

  @Override
  public Health health() {
    try {
      client.get().uri("/actuator/health/liveness").retrieve().toBodilessEntity();
      return Health.up().withDetail("baseUrl", baseUrl).build();
    } catch (Exception error) {
      return Health.down()
          .withDetail("baseUrl", baseUrl)
          .withDetail("error", error.getClass().getSimpleName())
          .build();
    }
  }
}
