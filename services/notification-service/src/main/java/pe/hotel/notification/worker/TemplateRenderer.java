package pe.hotel.notification.worker;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Render de plantillas en texto plano
 * (docs/especificacion/microservicios/notification/base-de-datos.md § Esquemas JSONB). Solo se
 * aceptan las variables de la plantilla, todas escalares; los valores se escapan para que no
 * puedan inyectar marcadores ni cabeceras.
 */
public final class TemplateRenderer {

  /** Allowlist de variables por plantilla, en el orden de la spec. */
  public static final Map<String, List<String>> VARIABLES =
      Map.of(
          "BIENVENIDA", List.of("nombre"),
          "CHECKIN", List.of("nombre", "hotel", "idRecepcion", "fechaSalida"),
          "CHECKOUT", List.of("nombre", "hotel", "idRecepcion", "totalAlojamiento", "totalConsumos"));

  private TemplateRenderer() {}

  public static Set<String> variablesDe(String tipo) {
    List<String> variables = VARIABLES.get(tipo);
    if (variables == null) {
      throw new NotificationPermanentException("plantilla desconocida: " + tipo);
    }
    return Set.copyOf(variables);
  }

  /**
   * Sustituye los marcadores {@code {{clave}}} del cuerpo por los valores escapados. Exige las
   * variables obligatorias de la plantilla, rechaza claves desconocidas y valores no escalares.
   */
  public static String render(String cuerpo, String tipo, Map<String, Object> valores) {
    Set<String> permitidas = variablesDe(tipo);
    for (String clave : valores.keySet()) {
      if (!permitidas.contains(clave)) {
        throw new NotificationPermanentException("variable desconocida en " + tipo + ": " + clave);
      }
    }
    String resultado = cuerpo;
    for (String clave : permitidas) {
      if (!valores.containsKey(clave) || valores.get(clave) == null) {
        throw new NotificationPermanentException("falta la variable " + clave + " de " + tipo);
      }
      Object valor = valores.get(clave);
      if (valor instanceof Map || valor instanceof List || valor instanceof java.util.Collection) {
        throw new NotificationPermanentException("valor no escalar en " + clave + " de " + tipo);
      }
      resultado = resultado.replace("{{" + clave + "}}", escape(String.valueOf(valor)));
    }
    return resultado;
  }

  /** Texto plano: sin CR/LF ni llaves que inyecten marcadores; nada de HTML. */
  public static String escape(String valor) {
    return valor
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("{", "(")
        .replace("}", ")")
        .trim();
  }
}