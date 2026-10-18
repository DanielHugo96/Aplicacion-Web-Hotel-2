package pe.hotel.identity.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.identity.messaging.Destination;
import pe.hotel.identity.messaging.OutboxStore;
import pe.hotel.identity.persona.Persona;
import pe.hotel.identity.platform.CorrelationId;

/**
 * Encola el comando {@code notificacion.bienvenida} de un registro público, dentro de la misma
 * transacción que crea la persona.
 *
 * <p>El envelope es el de [05 §Envelope de comando RabbitMQ](../especificacion/05-mensajeria.md) y el
 * payload el de
 * {@code docs/contracts/events/notificacion.bienvenida.NotificacionBienvenida.v1.schema.json}:
 * {@code idPersona}, {@code destinatario}, {@code nombre} y {@code templateVersion:1}, con
 * {@code idHotel:null} porque el cliente recién registrado no pertenece a una sede.
 *
 * <p>La fila se escribe y ya está: el poller de la plataforma la publica después del commit. No se
 * envía correo desde la transacción ni se depende de que el broker esté arriba para dar el alta.
 */
@Component
public class BienvenidaNotifier {

  public static final String CANAL = "notificacion.bienvenida";
  public static final String TIPO = "NotificacionBienvenida";

  private static final Logger log = LoggerFactory.getLogger(BienvenidaNotifier.class);

  private final ObjectProvider<OutboxStore> outbox;
  private final ObjectMapper mapper = new ObjectMapper();

  public BienvenidaNotifier(ObjectProvider<OutboxStore> outbox) {
    this.outbox = outbox;
  }

  /** Encola la bienvenida de la persona recién creada; se une a la transacción en curso. */
  @Transactional
  public void encolar(Persona persona, String correlationId) {
    OutboxStore store = outbox.getIfAvailable();
    if (store == null) {
      // Solo ocurre con la mensajería desactivada a propósito (hotel.messaging.enabled=false).
      log.warn("bienvenida_no_encolada id_persona={} motivo=mensajeria_desactivada", persona.id());
      return;
    }
    String commandId = UUID.randomUUID().toString();
    store.enqueue(
        Destination.RABBIT,
        CANAL,
        null,
        String.valueOf(persona.id()),
        persona.version(),
        TIPO,
        envelope(commandId, persona, correlationId));
    log.info(
        "bienvenida_encolada id_persona={} command_id={} canal={}",
        persona.id(),
        commandId,
        CANAL);
  }

  private String envelope(String commandId, Persona persona, String correlationId) {
    ObjectNode raiz = mapper.createObjectNode();
    raiz.put("commandId", commandId);
    raiz.put("type", TIPO);
    raiz.put("schemaVersion", 1);
    raiz.putNull("idHotel");
    raiz.put("correlationId", correccionDe(correlationId));
    raiz.put("issuedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)));
    ObjectNode cuerpo = raiz.putObject("payload");
    cuerpo.put("idPersona", persona.id());
    cuerpo.put("destinatario", persona.correo());
    cuerpo.put("nombre", persona.nombre());
    cuerpo.put("templateVersion", 1);
    return raiz.toString();
  }

  /** El comando siempre lleva un correlationId; si la petición no lo trajo, se genera uno. */
  private String correccionDe(String correlationId) {
    return correlationId == null || correlationId.isBlank()
        ? CorrelationId.resolve(null)
        : correlationId;
  }
}