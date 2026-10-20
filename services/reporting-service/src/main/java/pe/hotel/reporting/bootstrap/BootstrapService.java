package pe.hotel.reporting.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.reporting.config.ReportingProperties;
import pe.hotel.reporting.projection.ProjectionStore;
import pe.hotel.reporting.projection.ReportingTopics;

/**
 * Bootstrap de dev/demo: manifiesto con conteos por dominio y watermarks, fixture de snapshots del
 * seed terminado (3 sedes, 6 habitaciones, 9 stocks y 6 personas; 0 ventas/estadías), checkpoints
 * de todas las particiones suscritas —incluidas las vacías con offset -1— y LISTO en una sola
 * transacción al cumplirse el manifiesto (REP03).
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.reporting.bootstrap",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BootstrapService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

  private final ProjectionStore store;
  private final ReportingProperties properties;
  private final ResourceLoader resourceLoader;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper = new ObjectMapper();

  public BootstrapService(
      ProjectionStore store,
      ReportingProperties properties,
      ResourceLoader resourceLoader,
      TransactionTemplate transactions) {
    this.store = store;
    this.properties = properties;
    this.resourceLoader = resourceLoader;
    this.transactions = transactions;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      // Una sola transacción: fixture + checkpoints + LISTO (o CARGANDO si no se cumple).
      transactions.executeWithoutResult(status -> bootstrap());
    } catch (Exception ex) {
      // La base puede no estar lista en el primer arranque: readiness ya refleja la dependencia y
      // el plan de infraestructura reintenta. No se marca LISTO con datos incompletos.
      log.error("bootstrap_error error={}", ex.getMessage());
    }
  }

  public void bootstrap() {
    ReportingProperties.Bootstrap config = properties.getBootstrap();
    store.ensureBootstrap(manifestJson(config));
    if (store.isBootstrapReady()) {
      log.info("bootstrap_estado LISTO");
      return;
    }

    ensureCheckpoints(config);
    loadSeed(config.getSeedLocation());

    if (manifiestoCumplido(config) && watermarksAlcanzados(config)) {
      store.markBootstrapReady();
      log.info("bootstrap_estado LISTO dominios={} watermarks={}", store.counts(), config.getWatermarks().size());
    } else {
      log.info("bootstrap_estado CARGANDO dominios={}", store.counts());
    }
  }

  private void ensureCheckpoints(ReportingProperties.Bootstrap config) {
    for (String topic : ReportingTopics.ALL) {
      store.ensureCheckpoint(topic, 0);
    }
    for (ReportingProperties.Watermark watermark : config.getWatermarks()) {
      store.ensureCheckpoint(watermark.getTopic(), watermark.getPartition());
    }
  }

  private void loadSeed(String location) {
    try {
      Resource resource = resourceLoader.getResource(location);
      if (!resource.exists()) {
        log.warn("bootstrap_seed_ausente location={}", location);
        return;
      }
      JsonNode seed = mapper.readTree(resource.getInputStream());
      for (JsonNode p : seed.path("personas")) {
        store.upsertPersona(p, p.path("version").asLong(1));
      }
      for (JsonNode p : seed.path("hoteles")) {
        store.upsertHotel(p, p.path("version").asLong(1));
      }
      for (JsonNode p : seed.path("habitaciones")) {
        long version = p.path("version").asLong(1);
        store.upsertHabitacion(p, version);
        store.initGateFromHotel(p, "HabitacionCreada");
      }
      for (JsonNode p : seed.path("stocks")) {
        store.upsertStock(p, p.path("version").asLong(1));
      }
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo cargar el fixture de bootstrap: " + ex.getMessage(), ex);
    }
  }

  private boolean manifiestoCumplido(ReportingProperties.Bootstrap config) {
    Map<String, Integer> counts = store.counts();
    if (config.getDominios() == null || config.getDominios().isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Integer> expected : config.getDominios().entrySet()) {
      int actual = counts.getOrDefault(expected.getKey(), 0);
      if (actual < expected.getValue()) {
        return false;
      }
    }
    return true;
  }

  private boolean watermarksAlcanzados(ReportingProperties.Bootstrap config) {
    for (ReportingProperties.Watermark watermark : config.getWatermarks()) {
      Long current = store.checkpointOffset(watermark.getTopic(), watermark.getPartition());
      if (current == null || current < watermark.getLastOffset()) {
        return false;
      }
    }
    return true;
  }

  private String manifestJson(ReportingProperties.Bootstrap config) {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("dominios", config.getDominios());
    List<Map<String, Object>> watermarks = new ArrayList<>();
    if (config.getWatermarks().isEmpty()) {
      for (String topic : ReportingTopics.ALL) {
        watermarks.add(Map.of("topic", topic, "partition", 0, "lastOffset", -1));
      }
    } else {
      for (ReportingProperties.Watermark watermark : config.getWatermarks()) {
        watermarks.add(
            Map.of(
                "topic", watermark.getTopic(),
                "partition", watermark.getPartition(),
                "lastOffset", watermark.getLastOffset()));
      }
    }
    manifest.put("watermarks", watermarks);
    try {
      return mapper.writeValueAsString(manifest);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar el manifiesto de bootstrap", ex);
    }
  }
}
