package pe.hotel.inventory.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

/**
 * Comando {@code stock.restituir} (docs/especificacion/05-mensajeria.md § Topics y asignación
 * exacta): envelope común más payload con {@code idVenta}, {@code idRecepcion} y {@code motivo}.
 * El emisor no envía cantidades: inventory usa su movimiento original o crea una lápida.
 */
record StockRestituirCommand(
    UUID commandId,
    String type,
    Integer idHotel,
    UUID correlationId,
    String issuedAt,
    Integer idVenta,
    Integer idRecepcion,
    String motivo) {

  static StockRestituirCommand parse(String body) {
    try {
      JsonNode envelope = new ObjectMapper().readTree(body);
      JsonNode payload = envelope.path("payload");
      return new StockRestituirCommand(
          UUID.fromString(envelope.path("commandId").asText()),
          envelope.path("type").asText(),
          envelope.path("idHotel").isNull() ? null : envelope.path("idHotel").asInt(),
          UUID.fromString(envelope.path("correlationId").asText()),
          envelope.path("issuedAt").asText(),
          payload.path("idVenta").asInt(),
          payload.path("idRecepcion").isNull() ? null : payload.path("idRecepcion").asInt(),
          payload.path("motivo").asText());
    } catch (Exception ex) {
      throw new MessageInvalidException("comando StockRestituir inválido: " + ex.getMessage());
    }
  }
}
