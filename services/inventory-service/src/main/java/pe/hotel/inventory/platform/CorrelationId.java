package pe.hotel.inventory.platform;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Acceso a la correlación de la petición en curso.
 * Ver docs/especificacion/03-contrato-comun.md § Headers.
 */
public final class CorrelationId {

  /** Cabecera pública de correlación. */
  public static final String HEADER = "X-Correlation-Id";

  /** Clave del MDC con la que los logs estructurados emiten el identificador. */
  public static final String MDC_KEY = "correlationId";

  /** Clave del MDC con el nombre del servicio; los logs estructurados la emiten como {@code service}. */
  public static final String SERVICE_MDC_KEY = "service";

  private CorrelationId() {}

  /** Identificador de la petición en curso; cadena vacía si no hay contexto. */
  public static String current() {
    String value = MDC.get(MDC_KEY);
    return value == null ? "" : value;
  }

  /** Devuelve un UUID válido o genera uno nuevo si el valor falta o no es un UUID. */
  public static String resolve(String candidate) {
    if (candidate != null) {
      try {
        return UUID.fromString(candidate.trim()).toString();
      } catch (IllegalArgumentException ignored) {
        // valor no UUID: se genera uno nuevo
      }
    }
    return UUID.randomUUID().toString();
  }
}
