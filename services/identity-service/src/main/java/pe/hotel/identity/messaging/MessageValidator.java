package pe.hotel.identity.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Valida el envelope completo (evento Kafka o comando RabbitMQ) contra su JSON Schema v1 de
 * docs/contracts/events/ antes de publicar y al consumir. Los esquemas se copian al classpath en
 * {@code schemas/} durante el build desde la fuente única del repositorio.
 */
public class MessageValidator {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JsonSchemaFactory factory;
  private final Map<String, String> idByFileName = new HashMap<>();
  private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

  public MessageValidator(String schemasLocation) {
    String prefix = schemasLocation.endsWith("/") ? schemasLocation : schemasLocation + "/";
    Map<String, String> resourcesById = new HashMap<>();
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath*:" + prefix + "*.schema.json");
      for (Resource resource : resources) {
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode node = mapper.readTree(content);
        String id = node.path("$id").asText("");
        if (!id.isEmpty()) {
          resourcesById.put(id, content);
          idByFileName.put(resource.getFilename(), id);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("No se pudieron cargar los JSON Schemas v1 de " + prefix, e);
    }
    if (idByFileName.isEmpty()) {
      throw new IllegalStateException("No hay JSON Schemas v1 en " + prefix);
    }
    this.factory =
        JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
            .schemaLoaders(builder -> builder.schemas(resourcesById))
            .build();
  }

  public void validate(OutboxMessage message) {
    JsonNode node;
    try {
      node = mapper.readTree(message.payload());
    } catch (JsonProcessingException e) {
      throw new MessageInvalidException("payload no es JSON valido: " + e.getOriginalMessage());
    }
    String type = node.path("type").asText("");
    if (type.isEmpty()) {
      throw new MessageInvalidException("el mensaje no declara type");
    }
    String schemaId = idByFileName.get(schemaFileName(message.destination(), message.channel(), type));
    if (schemaId == null) {
      throw new MessageInvalidException("no existe esquema v1 para " + type);
    }
    JsonSchema schema =
        cache.computeIfAbsent(schemaId, id -> factory.getSchema(java.net.URI.create(id)));
    Set<ValidationMessage> errors = schema.validate(node);
    if (!errors.isEmpty()) {
      throw new MessageInvalidException("mensaje invalido: " + errors.iterator().next().getMessage());
    }
  }

  static String schemaFileName(Destination destination, String channel, String type) {
    return destination == Destination.RABBIT
        ? channel + "." + type + ".v1.schema.json"
        : channel + "." + type + ".schema.json";
  }
}
