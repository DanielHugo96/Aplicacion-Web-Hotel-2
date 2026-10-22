package pe.hotel.hotel.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import pe.hotel.hotel.domain.model.Hotel;
import pe.hotel.hotel.messaging.Destination;
import pe.hotel.hotel.messaging.MessageValidator;
import pe.hotel.hotel.messaging.OutboxMessage;
import pe.hotel.hotel.messaging.OutboxStore;

/**
 * El {@code payload} de {@code SedeSnapshot} lleva la foto principal de la sede cuando la tiene, y el
 * mensaje completo valida contra {@code docs/contracts/events/hotel.sede.v1.SedeSnapshot.schema.json}.
 */
class SedeSnapshotImagenTests {

  private static final String URL = "/assets/sedes/arequipa-fachada.jpg";
  private static final String ALT = "Fachada de sillar de la sede de Arequipa con el Misti al fondo";

  private final OutboxStore store = mock(OutboxStore.class);
  private final OutboxService service = new OutboxService();
  private final ObjectMapper mapper = new ObjectMapper();
  private final MessageValidator validator = new MessageValidator("schemas");

  @Test
  void elPayloadLlevaElParDeImagenYValidaContraElEsquema() throws Exception {
    ReflectionTestUtils.setField(service, "outboxStore", store);

    service.emitirSedeSnapshot(hotel(URL, ALT));

    String json = capturarPayload();
    JsonNode payload = mapper.readTree(json).path("payload");
    assertThat(payload.path("imagenUrl").asText()).isEqualTo(URL);
    assertThat(payload.path("imagenAlt").asText()).isEqualTo(ALT);
    validator.validate(mensaje(json));
  }

  @Test
  void unaSedeSinImagenEmiteLosCamposNulosYValidaContraElEsquema() throws Exception {
    ReflectionTestUtils.setField(service, "outboxStore", store);

    service.emitirSedeSnapshot(hotel(null, null));

    String json = capturarPayload();
    JsonNode payload = mapper.readTree(json).path("payload");
    assertThat(payload.has("imagenUrl")).isTrue();
    assertThat(payload.get("imagenUrl").isNull()).isTrue();
    assertThat(payload.has("imagenAlt")).isTrue();
    assertThat(payload.get("imagenAlt").isNull()).isTrue();
    validator.validate(mensaje(json));
  }

  private String capturarPayload() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(store)
        .enqueue(
            eq(Destination.KAFKA),
            eq("hotel.sede.v1"),
            anyString(),
            anyString(),
            anyLong(),
            eq("SedeSnapshot"),
            captor.capture());
    return captor.getValue();
  }

  private static OutboxMessage mensaje(String json) {
    return new OutboxMessage(
        UUID.randomUUID(),
        Destination.KAFKA,
        "hotel.sede.v1",
        "3",
        "3",
        1,
        "SedeSnapshot",
        json,
        Instant.now(),
        0,
        Instant.now());
  }

  private static Hotel hotel(String imagenUrl, String imagenAlt) {
    Hotel hotel =
        new Hotel(
            "arequipa",
            "Casa Sillar Arequipa",
            "Casona de sillar en el centro historico de Arequipa.",
            "Calle Mercaderes 218, Cercado de Arequipa",
            "Arequipa",
            "+51 54 216 800",
            "contacto@casasillar.pe",
            imagenUrl,
            imagenAlt,
            true);
    hotel.setId(3);
    hotel.setEstado(true);
    hotel.setVersion(1L);
    return hotel;
  }
}
