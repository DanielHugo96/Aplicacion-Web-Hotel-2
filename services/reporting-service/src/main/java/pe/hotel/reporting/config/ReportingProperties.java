package pe.hotel.reporting.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades del read model: límites de los reportes, umbral de degradación y bootstrap de
 * dev/demo (manifiesto + fixture de snapshots del seed terminado).
 */
@Component
@ConfigurationProperties(prefix = "hotel.reporting")
public class ReportingProperties {

  private int maxRangeDays = 366;
  private int degradedTimeoutMinutes = 5;
  private long connectionCheckIntervalMs = 30000;
  private final Bootstrap bootstrap = new Bootstrap();

  public int getMaxRangeDays() {
    return maxRangeDays;
  }

  public void setMaxRangeDays(int maxRangeDays) {
    this.maxRangeDays = maxRangeDays;
  }

  public int getDegradedTimeoutMinutes() {
    return degradedTimeoutMinutes;
  }

  public void setDegradedTimeoutMinutes(int degradedTimeoutMinutes) {
    this.degradedTimeoutMinutes = degradedTimeoutMinutes;
  }

  public long getConnectionCheckIntervalMs() {
    return connectionCheckIntervalMs;
  }

  public void setConnectionCheckIntervalMs(long connectionCheckIntervalMs) {
    this.connectionCheckIntervalMs = connectionCheckIntervalMs;
  }

  public Bootstrap getBootstrap() {
    return bootstrap;
  }

  public static class Bootstrap {

    private boolean enabled = true;
    private String seedLocation = "classpath:bootstrap/demo-seed.json";
    private Map<String, Integer> dominios = new LinkedHashMap<>();
    private List<Watermark> watermarks = List.of();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSeedLocation() {
      return seedLocation;
    }

    public void setSeedLocation(String seedLocation) {
      this.seedLocation = seedLocation;
    }

    public Map<String, Integer> getDominios() {
      return dominios;
    }

    public void setDominios(Map<String, Integer> dominios) {
      this.dominios = dominios;
    }

    public List<Watermark> getWatermarks() {
      return watermarks;
    }

    public void setWatermarks(List<Watermark> watermarks) {
      this.watermarks = watermarks;
    }
  }

  public static class Watermark {

    private String topic;
    private int partition;
    private long lastOffset = -1;

    public String getTopic() {
      return topic;
    }

    public void setTopic(String topic) {
      this.topic = topic;
    }

    public int getPartition() {
      return partition;
    }

    public void setPartition(int partition) {
      this.partition = partition;
    }

    public long getLastOffset() {
      return lastOffset;
    }

    public void setLastOffset(long lastOffset) {
      this.lastOffset = lastOffset;
    }
  }
}
