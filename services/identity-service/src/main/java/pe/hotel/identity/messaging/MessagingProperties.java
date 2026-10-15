package pe.hotel.identity.messaging;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de la plataforma de mensajería (docs/especificacion/infraestructura/configuracion.md
 * § Comunes a todos los servicios). Los valores por defecto son los de la especificación y son
 * sobrescribibles por entorno.
 */
@Component
@ConfigurationProperties(prefix = "hotel")
public class MessagingProperties {

  private final Outbox outbox = new Outbox();
  private final Inbox inbox = new Inbox();
  private final Messaging messaging = new Messaging();

  public Outbox getOutbox() {
    return outbox;
  }

  public Inbox getInbox() {
    return inbox;
  }

  public Messaging getMessaging() {
    return messaging;
  }

  public static class Outbox {

    private long pollIntervalMs = 5000;
    private int batchSize = 50;
    private int alertAttempts = 10;
    private int alertPendingMinutes = 15;

    public long getPollIntervalMs() {
      return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
      this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getAlertAttempts() {
      return alertAttempts;
    }

    public void setAlertAttempts(int alertAttempts) {
      this.alertAttempts = alertAttempts;
    }

    public int getAlertPendingMinutes() {
      return alertPendingMinutes;
    }

    public void setAlertPendingMinutes(int alertPendingMinutes) {
      this.alertPendingMinutes = alertPendingMinutes;
    }
  }

  public static class Inbox {

    private int retentionDays = 30;

    public int getRetentionDays() {
      return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
      this.retentionDays = retentionDays;
    }
  }

  public static class Messaging {

    private boolean enabled = true;
    private String schemasLocation = "schemas";
    private final Kafka kafka = new Kafka();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSchemasLocation() {
      return schemasLocation;
    }

    public void setSchemasLocation(String schemasLocation) {
      this.schemasLocation = schemasLocation;
    }

    public Kafka getKafka() {
      return kafka;
    }
  }

  public static class Kafka {

    private String consumerGroup = "";
    private List<Long> retryIntervalsMs =
        new java.util.ArrayList<>(List.of(1000L, 5000L, 30000L, 120000L, 300000L));

    public String getConsumerGroup() {
      return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
      this.consumerGroup = consumerGroup;
    }

    public List<Long> getRetryIntervalsMs() {
      return retryIntervalsMs;
    }

    public void setRetryIntervalsMs(List<Long> retryIntervalsMs) {
      this.retryIntervalsMs = retryIntervalsMs;
    }
  }
}
