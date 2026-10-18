package pe.hotel.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Ventana fija de un minuto del contador en memoria. */
class RateLimiterTests {

  @Test
  void admiteHastaElLimiteYDespuesRechaza() {
    RateLimiter limiter = new RateLimiter();

    for (int intento = 1; intento <= 3; intento++) {
      assertThat(limiter.registrar("login|ip|correo", 3).permitido()).as("intento %d", intento).isTrue();
    }

    RateLimiter.Decision rechazada = limiter.registrar("login|ip|correo", 3);
    assertThat(rechazada.permitido()).isFalse();
    assertThat(rechazada.retryAfterSegundos()).isBetween(1L, 60L);
  }

  @Test
  void cadaClaveTieneSuPropioPresupuesto() {
    RateLimiter limiter = new RateLimiter();
    for (int intento = 0; intento < 5; intento++) {
      limiter.registrar("login|ip|uno@hotel.test", 5);
    }

    assertThat(limiter.registrar("login|ip|uno@hotel.test", 5).permitido()).isFalse();
    assertThat(limiter.registrar("login|ip|dos@hotel.test", 5).permitido()).isTrue();
  }

  @Test
  void sinLimiteConfiguradoTodoPasa() {
    assertThat(new RateLimiter().registrar("registro|ip", 0).permitido()).isTrue();
  }
}