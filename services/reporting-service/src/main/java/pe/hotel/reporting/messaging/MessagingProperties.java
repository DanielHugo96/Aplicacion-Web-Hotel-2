package pe.hotel.reporting.messaging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de mensajería del read model (docs/especificacion/infraestructura/configuracion.md
 * § Comunes a todos los servicios). Reporting solo consume Kafka: no tiene outbox, Rabbit ni
 * publicación de eventos.
 */
@Component
@ConfigurationProperties(prefix = "hotel")
public class MessagingProperties {

  private final Inbox inbox = new Inbox();
  private final Messaging messaging = new Messaging();

  public Inbox getInbox() {
    return inbox;
  }

  public Messaging getMessaging() {
    return messaging;
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

    private String consumerGroup = "reporting-proyeccion-v1";
    private List<Long> retryIntervalsMs = new ArrayList<>(List.of(1000L, 5000L, 30000L, 120000L, 300000L));

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
