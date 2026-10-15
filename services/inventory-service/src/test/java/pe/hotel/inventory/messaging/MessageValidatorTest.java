package pe.hotel.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MessageValidatorTest {

  private static final String SCHEMA_FILE = "limpieza.solicitada.LimpiezaSolicitada.v1.schema.json";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MessageValidator validator = new MessageValidator("schemas");

  @Test
  void aceptaElEjemploDelEsquema() throws Exception {
    validator.validate(message(example()));
  }

  @Test
  void rechazaUnCampoExtra() throws Exception {
    ObjectNode node = example();
    node.put("campoExtra", true);
    assertThatThrownBy(() -> validator.validate(message(node)))
        .isInstanceOf(MessageInvalidException.class);
  }

  private OutboxMessage message(JsonNode node) {
    return new OutboxMessage(
        UUID.randomUUID(),
        Destination.RABBIT,
        "limpieza.solicitada",
        "101",
        "101",
        1,
        node.path("type").asText(),
        node.toString(),
        Instant.now(),
        0,
        Instant.now());
  }

  private ObjectNode example() throws Exception {
    try (var in = new ClassPathResource("schemas/" + SCHEMA_FILE).getInputStream()) {
      JsonNode root = mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      return (ObjectNode) root.path("examples").get(0);
    }
  }
}
