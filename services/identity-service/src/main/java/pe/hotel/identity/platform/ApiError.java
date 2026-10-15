package pe.hotel.identity.platform;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Bloque {@code error} del envelope común: código estable y campos afectados.
 * Ver docs/especificacion/03-contrato-comun.md § Respuestas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, Map<String, String> fields) {

  public ApiError {
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  public static ApiError of(ErrorCode code) {
    return new ApiError(code.name(), Map.of());
  }

  public static ApiError of(ErrorCode code, Map<String, String> fields) {
    return new ApiError(code.name(), fields);
  }
}
