package pe.hotel.reporting.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessagingPolicyTest {

  @Test
  void backoffEsExponencialConTopeDeCincoMinutos() {
    assertThat(MessagingPolicy.backoff(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(MessagingPolicy.backoff(2)).isEqualTo(Duration.ofSeconds(2));
    assertThat(MessagingPolicy.backoff(3)).isEqualTo(Duration.ofSeconds(4));
    assertThat(MessagingPolicy.backoff(4)).isEqualTo(Duration.ofSeconds(8));
    assertThat(MessagingPolicy.backoff(20)).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void alertaPorIntentosOPorPendienteAntiguo() {
    Instant ahora = Instant.parse("2026-10-15T12:00:00Z");
    Instant reciente = ahora.minus(Duration.ofMinutes(5));
    Instant antiguo = ahora.minus(Duration.ofMinutes(16));
    assertThat(MessagingPolicy.shouldAlert(11, reciente, ahora, 10, 15)).isTrue();
    assertThat(MessagingPolicy.shouldAlert(1, antiguo, ahora, 10, 15)).isTrue();
    assertThat(MessagingPolicy.shouldAlert(1, reciente, ahora, 10, 15)).isFalse();
  }
}
