package pe.hotel.hotel.platform;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Escribe el envelope de error directamente sobre la respuesta, sin depender del serializador de
 * Spring. Se usa desde la cadena de seguridad, donde no hay controlador.
 */
public final class ResponseWriter {

  private ResponseWriter() {}

  public static void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
    writeError(response, code, code.uiMessage(), Map.of());
  }

  public static void writeError(HttpServletResponse response, ErrorCode code, Map<String, String> fields)
      throws IOException {
    writeError(response, code, code.uiMessage(), fields);
  }

  public static void writeError(
      HttpServletResponse response, ErrorCode code, String message, Map<String, String> fields)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(code.status().value());
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(envelope(message, code.name(), fields));
  }

  /** Escribe un JSON ya construido con el estado y el tipo de contenido de la API. */
  public static void writeJson(HttpServletResponse response, int status, String json) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(json);
  }

  /** Envelope de éxito plano con {@code data} ya serializado. */
  public static String successEnvelope(String message, String dataJson) {
    return "{\"success\":true,\"message\":\""
        + escape(message)
        + "\",\"data\":"
        + (dataJson == null ? "null" : dataJson)
        + ",\"meta\":{\"correlationId\":\""
        + escape(CorrelationId.current())
        + "\"}}";
  }

  /** Envelope de error plano: {@code success/message/data/error/meta}. */
  public static String envelope(String message, String code, Map<String, String> fields) {
    StringBuilder json = new StringBuilder(160);
    json.append("{\"success\":false,\"message\":\"").append(escape(message)).append('"');
    json.append(",\"data\":null,\"error\":{\"code\":\"").append(escape(code)).append("\",\"fields\":{");
    boolean first = true;
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
    }
    json.append("}},\"meta\":{\"correlationId\":\"").append(escape(CorrelationId.current())).append("\"}}");
    return json.toString();
  }

  static String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
