package pe.hotel.reporting.report;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Estado de conexión con Kafka usado por la guardia de degradación (paso 11). */
public class ConnectivityState {

  public record Snapshot(boolean connected, Instant lastSuccess) {}

  private final AtomicReference<Snapshot> snapshot =
      new AtomicReference<>(new Snapshot(true, Instant.now()));

  public void markSuccess() {
    snapshot.set(new Snapshot(true, Instant.now()));
  }

  public void markFailure() {
    snapshot.updateAndGet(current -> new Snapshot(false, current.lastSuccess()));
  }

  public boolean isConnected() {
    return snapshot.get().connected();
  }

  public Instant lastSuccess() {
    return snapshot.get().lastSuccess();
  }

  /** Utilizable mientras no se supere el umbral sin una conexión activa. */
  public boolean usable(int timeoutMinutes) {
    return Duration.between(lastSuccess(), Instant.now()).toMinutes() < timeoutMinutes;
  }
}
