package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** Reintentos TTL por cola y contador de intentos del mensaje. */
class RabbitRetryPolicyTest {

  @Test
  void usaLosTresReintentosTtlYDespuesDlq() {
    assertThat(RabbitRetryPolicy.colaDeReintento("notificacion.checkin", 0))
        .isEqualTo("notificacion.checkin.retry.5s");
    assertThat(RabbitRetryPolicy.colaDeReintento("notificacion.checkin", 1))
        .isEqualTo("notificacion.checkin.retry.30s");
    assertThat(RabbitRetryPolicy.colaDeReintento("notificacion.checkin", 2))
        .isEqualTo("notificacion.checkin.retry.120s");
    assertThat(RabbitRetryPolicy.colaDeReintento("notificacion.checkin", 3)).isNull();
    assertThat(RabbitRetryPolicy.dlq("notificacion.checkin")).isEqualTo("notificacion.checkin.dlq");
  }

  @Test
  void republicaConservandoCuerpoYSumandoUnIntento() {
    MessageProperties props = new MessageProperties();
    props.setMessageId(UUID.randomUUID().toString());
    props.setCorrelationId(UUID.randomUUID().toString());
    props.setHeader("x-schema-version", 1);
    Message original = new Message("{\"commandId\":\"x\"}".getBytes(StandardCharsets.UTF_8), props);

    Message reintento = NotificationListener.conIntento(original, 2);

    assertThat(reintento.getBody()).isEqualTo(original.getBody());
    assertThat(reintento.getMessageProperties().getMessageId()).isEqualTo(props.getMessageId());
    assertThat(reintento.getMessageProperties().getHeaders())
        .containsEntry(RabbitRetryPolicy.CABECERA_INTENTOS, 2)
        .containsEntry("x-schema-version", 1);
    assertThat(NotificationListener.intentosFallidos(original)).isZero();
    assertThat(NotificationListener.intentosFallidos(reintento)).isEqualTo(2);
  }
}