package pe.hotel.reporting.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MessageValidatorTest {

  private static final String TOPIC = "identity.persona.v1";
  private static final String SCHEMA_FILE = "identity.persona.v1.PersonaSnapshot.schema.json";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MessageValidator validator = new MessageValidator("schemas");

  @Test
  void aceptaElEjemploDelEsquema() throws Exception {
    validator.validate(TOPIC, example().toString());
  }

  @Test
  void rechazaUnCampoExtra() throws Exception {
    ObjectNode node = example();
    node.put("campoExtra", true);
    assertThatThrownBy(() -> validator.validate(TOPIC, node.toString()))
        .isInstanceOf(MessageInvalidException.class);
  }

  @Test
  void rechazaUnTipoSinEsquemaEnElTopic() {
    assertThatThrownBy(() -> validator.validate(TOPIC, "{\"type\":\"Otro\"}"))
        .isInstanceOf(MessageInvalidException.class);
  }

  private ObjectNode example() throws Exception {
    try (var in = new ClassPathResource("schemas/" + SCHEMA_FILE).getInputStream()) {
      JsonNode root = mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      return (ObjectNode) root.path("examples").get(0);
    }
  }
}
