package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Validación de envelope y payload: sede, destinatario, secretos, HTML, adjuntos y descargas. */
class NotificationCommandTest {

  private static final String COMMAND_ID = "33333333-3333-4333-8333-333333333333";
  private static final String CORRELATION = "22222222-2222-4222-8222-222222222222";

  private static String bienvenida(String payloadExtra) {
    return "{\"commandId\":\"" + COMMAND_ID + "\",\"type\":\"NotificacionBienvenida\","
        + "\"schemaVersion\":1,\"idHotel\":null,\"correlationId\":\"" + CORRELATION + "\","
        + "\"issuedAt\":\"2026-10-04T14:00:00Z\",\"payload\":{\"idPersona\":100,"
        + "\"destinatario\":\"cliente.uno@hotel.test\",\"nombre\":\"Cliente Uno\","
        + "\"templateVersion\":1" + payloadExtra + "}}";
  }

  private static String checkin(String idHotel, String payload) {
    return "{\"commandId\":\"" + COMMAND_ID + "\",\"type\":\"NotificacionCheckin\","
        + "\"schemaVersion\":1,\"idHotel\":" + idHotel + ",\"correlationId\":\"" + CORRELATION + "\","
        + "\"issuedAt\":\"2026-10-04T15:00:00Z\",\"payload\":" + payload + "}";
  }

  private static final String PAYLOAD_CHECKIN =
      "{\"idPersona\":100,\"idRecepcion\":1001,\"destinatario\":\"cliente.uno@hotel.test\","
          + "\"nombre\":\"Cliente Uno\",\"hotel\":\"Hotel Demo Lima Centro\","
          + "\"fechaSalida\":\"2026-10-05\",\"templateVersion\":1}";

  @Test
  void aceptaBienvenidaSinSedeYConSedeObligatoriaEnCheckin() {
    NotificationCommand comando = NotificationCommand.parse(bienvenida(""));

    assertThat(comando.tipo()).isEqualTo("BIENVENIDA");
    assertThat(comando.idHotel()).isNull();
    assertThat(comando.commandId().toString()).isEqualTo(COMMAND_ID);

    assertThat(NotificationCommand.parse(checkin("1", PAYLOAD_CHECKIN)).tipo()).isEqualTo("CHECKIN");
  }

  @Test
  void bienvenidaConSedeEsErrorPermanente() {
    assertThatThrownBy(
            () ->
                NotificationCommand.parse(
                    bienvenida("").replace("\"idHotel\":null", "\"idHotel\":1")))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("no admite sede");
  }

  @Test
  void checkinSinSedeEsErrorPermanente() {
    assertThatThrownBy(() -> NotificationCommand.parse(checkin("null", PAYLOAD_CHECKIN)))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("sin idHotel");
  }

  @Test
  void destinatarioNoCorreoEsErrorPermanente() {
    assertThatThrownBy(
            () ->
                NotificationCommand.parse(
                    bienvenida("").replace("cliente.uno@hotel.test", "cliente.uno")))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("correo válido");
  }

  @Test
  void destinatarioMayorA254EsErrorPermanente() {
    String largo = "a".repeat(250) + "@hotel.test";
    assertThatThrownBy(
            () -> NotificationCommand.parse(bienvenida("").replace("cliente.uno@hotel.test", largo)))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("254");
  }

  @Test
  void campoSecretoEsErrorPermanente() {
    assertThatThrownBy(() -> NotificationCommand.parse(bienvenida(",\"token\":\"abc\"")))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("campo no permitido");
  }

  @Test
  void htmlAdjuntosYDescargasSonErrorPermanente() {
    for (String extra :
        new String[] {",\"html\":\"<b>hola</b>\"", ",\"adjuntos\":[]", ",\"url\":\"https://x\""}) {
      assertThatThrownBy(() -> NotificationCommand.parse(bienvenida(extra)))
          .isInstanceOf(NotificationPermanentException.class)
          .hasMessageContaining("campo no permitido");
    }
  }

  @Test
  void tipoDeComandoDesconocidoEsErrorPermanente() {
    assertThatThrownBy(
            () ->
                NotificationCommand.parse(
                    bienvenida("").replace("NotificacionBienvenida", "NotificacionPromocion")))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("no permitido");
  }

  @Test
  void schemaVersionDistintaDeUnoEsErrorPermanente() {
    assertThatThrownBy(
            () -> NotificationCommand.parse(bienvenida("").replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("schemaVersion");
  }

  @Test
  void cuerpoQueNoEsJsonEsErrorPermanente() {
    assertThatThrownBy(() -> NotificationCommand.parse("no soy json"))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("JSON válido");
  }
}