package pe.hotel.notification.messaging;

import java.time.Duration;
import java.time.Instant;

/** Reglas puras del outbox: backoff exponencial con tope y umbrales de alerta de RB4. */
public final class MessagingPolicy {

  static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

  private MessagingPolicy() {}

  /** Backoff exponencial 1s, 2s, 4s... con tope de 5 min ({@code attempts} = intentos fallidos). */
  public static Duration backoff(int attempts) {
    int exponent = Math.min(Math.max(attempts, 1) - 1, 30);
    Duration candidate = Duration.ofSeconds(1L << exponent);
    return candidate.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : candidate;
  }

  /** Alerta si se superan los intentos o si la fila lleva demasiado pendiente. */
  public static boolean shouldAlert(
      int attempts, Instant createdAt, Instant now, int alertAttempts, int alertPendingMinutes) {
    if (attempts > alertAttempts) {
      return true;
    }
    return createdAt != null
        && !createdAt.plus(Duration.ofMinutes(alertPendingMinutes)).isAfter(now);
  }

  public static long pendingMinutes(Instant createdAt, Instant now) {
    if (createdAt == null) {
      return 0;
    }
    return Math.max(0, Duration.between(createdAt, now).toMinutes());
  }
}
