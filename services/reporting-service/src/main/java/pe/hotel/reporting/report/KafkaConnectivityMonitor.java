package pe.hotel.reporting.report;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.hotel.reporting.config.ReportingProperties;

/**
 * Comprueba periódicamente que el broker responde. Si lleva más de
 * {@code hotel.reporting.degraded-timeout-minutes} sin conexión activa, los reportes responden 503
 * {@code REPORTING_UNAVAILABLE}; antes del umbral, 200 con {@code degraded=true}.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaConnectivityMonitor {

  private static final Logger log = LoggerFactory.getLogger(KafkaConnectivityMonitor.class);

  private final ConnectivityState state;
  private final String bootstrapServers;
  private final ReportingProperties properties;
  private volatile AdminClient admin;

  public KafkaConnectivityMonitor(
      ConnectivityState state,
      @Value("${spring.kafka.bootstrap-servers:localhost:29092}") String bootstrapServers,
      ReportingProperties properties) {
    this.state = state;
    this.bootstrapServers = bootstrapServers;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${hotel.reporting.connection-check-interval-ms:30000}")
  public void check() {
    try {
      admin()
          .describeCluster()
          .clusterId()
          .get(Math.max(2, properties.getConnectionCheckIntervalMs() / 1000), TimeUnit.SECONDS);
      state.markSuccess();
    } catch (Exception ex) {
      state.markFailure();
      log.warn("kafka_conexion_error error={}", ex.getMessage());
    }
  }

  private AdminClient admin() {
    if (admin == null) {
      synchronized (this) {
        if (admin == null) {
          admin =
              AdminClient.create(
                  Map.of(
                      "bootstrap.servers", bootstrapServers,
                      "request.timeout.ms", "3000",
                      "default.api.timeout.ms", "4000"));
        }
      }
    }
    return admin;
  }

  @PreDestroy
  void close() {
    if (admin != null) {
      admin.close();
    }
  }
}
