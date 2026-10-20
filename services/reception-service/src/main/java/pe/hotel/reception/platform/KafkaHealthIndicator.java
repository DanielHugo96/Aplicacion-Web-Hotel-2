package pe.hotel.reception.platform;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Degradación visible de Kafka sin afectar readiness (que solo exige PostgreSQL). El broker caído
 * deja el outbox pendiente, no revierte la operación local.
 */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

  private final String bootstrapServers;

  public KafkaHealthIndicator(
      @Value("${spring.kafka.bootstrap-servers:localhost:29092}") String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Override
  public Health health() {
    try (AdminClient admin =
        AdminClient.create(
            Map.of(
                "bootstrap.servers", bootstrapServers,
                "request.timeout.ms", "1500",
                "default.api.timeout.ms", "2000"))) {
      admin.describeCluster().clusterId().get(2, TimeUnit.SECONDS);
      return Health.up().withDetail("bootstrapServers", bootstrapServers).build();
    } catch (Exception error) {
      return Health.down()
          .withDetail("bootstrapServers", bootstrapServers)
          .withDetail("error", error.getClass().getSimpleName())
          .build();
    }
  }
}
