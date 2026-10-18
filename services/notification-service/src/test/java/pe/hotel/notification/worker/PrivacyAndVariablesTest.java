package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Privacidad en logs y parseo de las variables del job. */
class PrivacyAndVariablesTest {

  @Test
  void enmascaraElDestinatarioSinPerderElDominio() {
    assertThat(Privacy.maskEmail("cliente.uno@hotel.test")).isEqualTo("c***@hotel.test");
    assertThat(Privacy.maskEmail(null)).isEqualTo("(vacío)");
    assertThat(Privacy.maskEmail("sin-arroba")).isEqualTo("***");
  }

  @Test
  void lasVariablesDelJobSeVuelvenMapaEscalar() {
    NotificationJob job = job("{\"nombre\":\"Cliente Uno\",\"idRecepcion\":1001,\"total\":70.00}");

    assertThat(NotificationDispatcher.variables(job))
        .containsEntry("nombre", "Cliente Uno")
        .containsEntry("idRecepcion", "1001")
        .containsEntry("total", "70.00");
  }

  @Test
  void variablesNoEscalaresSonErrorPermanente() {
    NotificationJob job = job("{\"nombre\":{\"anidado\":true}}");

    assertThatThrownBy(() -> NotificationDispatcher.variables(job))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("no escalar");
  }

  private static NotificationJob job(String variables) {
    return new NotificationJob(
        UUID.randomUUID(),
        "CHECKOUT",
        1,
        100,
        1001,
        "cliente.uno@hotel.test",
        1,
        variables,
        "ENVIANDO",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        UUID.randomUUID(),
        "id@hotel.test",
        OffsetDateTime.now(),
        null,
        null);
  }
}