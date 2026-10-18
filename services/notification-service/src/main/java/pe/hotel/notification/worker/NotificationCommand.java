package pe.hotel.notification.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de comando RabbitMQ leído de la cola
 * (docs/especificacion/05-mensajeria.md § Envelope de comando RabbitMQ y
 * docs/especificacion/microservicios/notification/api.md § RabbitMQ).
 */
public record NotificationCommand(
    UUID commandId,
    String type,
    int schemaVersion,
    Integer idHotel,
    UUID correlationId,
    String issuedAt,
    JsonNode payload,
    String body,
    String tipo) {

  /** Tipos aceptados y su plantilla (allowlist; una plantilla desconocida va directo a DLQ). */
  public static final Map<String, String> TIPOS =
      Map.of(
          "NotificacionBienvenida", "BIENVENIDA",
          "NotificacionCheckin", "CHECKIN",
          "NotificacionCheckout", "CHECKOUT");

  /** Campos que jamás deben viajar en el comando: secretos, HTML arbitrario, adjuntos, URLs. */
  private static final List<String> PROHIBIDOS =
      List.of(
          "secreto", "secret", "password", "contrasena", "contraseña", "clave", "token", "jwt",
          "html", "adjunto", "adjuntos", "attachment", "attachments", "url", "urls", "descarga",
          "descargas", "download", "contentType", "mimeType");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Valida envelope y payload cerrados. Devuelve el comando listo para encolar o lanza
   * {@link NotificationPermanentException} (camino de DLQ, sin reintentos TTL).
   */
  public static NotificationCommand parse(String body) {
    JsonNode node;
    try {
      node = MAPPER.readTree(body);
    } catch (Exception ex) {
      throw new NotificationPermanentException("el cuerpo no es JSON válido");
    }
    if (node == null || !node.isObject()) {
      throw new NotificationPermanentException("el comando no es un objeto JSON");
    }

    UUID commandId = uuid(node, "commandId");
    UUID correlationId = uuid(node, "correlationId");
    String type = texto(node, "type");
    String tipo = TIPOS.get(type);
    if (tipo == null) {
      throw new NotificationPermanentException("tipo de comando no permitido: " + type);
    }
    if (node.path("schemaVersion").asInt(-1) != 1) {
      throw new NotificationPermanentException("schemaVersion distinta de 1");
    }
    String issuedAt = texto(node, "issuedAt");

    JsonNode idHotelNode = node.get("idHotel");
    if (idHotelNode == null) {
      throw new NotificationPermanentException("el comando no declara idHotel");
    }
    Integer idHotel = null;
    if (!idHotelNode.isNull()) {
      if (!idHotelNode.isInt() || idHotelNode.asInt() < 1) {
        throw new NotificationPermanentException("idHotel debe ser entero positivo o null");
      }
      idHotel = idHotelNode.asInt();
    }
    // Bienvenida es global (sede nula); checkin y checkout exigen sede.
    if ("BIENVENIDA".equals(tipo) && idHotel != null) {
      throw new NotificationPermanentException("la bienvenida no admite sede");
    }
    if (!"BIENVENIDA".equals(tipo) && idHotel == null) {
      throw new NotificationPermanentException(type + " sin idHotel");
    }

    JsonNode payload = node.get("payload");
    if (payload == null || !payload.isObject()) {
      throw new NotificationPermanentException("el comando no trae payload");
    }
    rechazarProhibidos(payload);
    String destinatario = texto(payload, "destinatario");
    if (destinatario.length() > 254) {
      throw new NotificationPermanentException("destinatario supera 254 caracteres");
    }
    if (!destinatario.contains("@") || destinatario.startsWith("@") || destinatario.endsWith("@")) {
      throw new NotificationPermanentException("destinatario no es un correo válido");
    }
    if (texto(payload, "nombre").isBlank()) {
      throw new NotificationPermanentException("el payload no trae nombre");
    }

    return new NotificationCommand(
        commandId, type, 1, idHotel, correlationId, issuedAt, payload, body, tipo);
  }

  private static void rechazarProhibidos(JsonNode payload) {
    Iterator<String> campos = payload.fieldNames();
    while (campos.hasNext()) {
      String campo = campos.next();
      String normalizado = campo.toLowerCase(java.util.Locale.ROOT);
      for (String prohibido : PROHIBIDOS) {
        if (normalizado.contains(prohibido.toLowerCase(java.util.Locale.ROOT))) {
          throw new NotificationPermanentException(
              "el comando trae un campo no permitido: " + campo);
        }
      }
    }
  }

  private static UUID uuid(JsonNode node, String campo) {
    String valor = texto(node, campo);
    try {
      return UUID.fromString(valor);
    } catch (IllegalArgumentException ex) {
      throw new NotificationPermanentException(campo + " no es un UUID");
    }
  }

  private static String texto(JsonNode node, String campo) {
    JsonNode valor = node.get(campo);
    if (valor == null || !valor.isTextual()) {
      throw new NotificationPermanentException("el comando no declara " + campo);
    }
    return valor.asText();
  }
}