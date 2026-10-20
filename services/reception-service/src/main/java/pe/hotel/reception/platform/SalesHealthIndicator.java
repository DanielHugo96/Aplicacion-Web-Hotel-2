package pe.hotel.reception.platform;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Degradación visible de sales (resumen y confirmación de cierre). No entra en readiness: un cierre
 * ya aceptado sigue recuperándose, pero un cierre nuevo recibe 503 antes de crear estado durable.
 */
@Component("sales")
public class SalesHealthIndicator implements HealthIndicator {

  private final RestClient client;
  private final String baseUrl;

  public SalesHealthIndicator(@Value("${hotel.feign.url.sales:http://localhost:8085}") String baseUrl) {
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
