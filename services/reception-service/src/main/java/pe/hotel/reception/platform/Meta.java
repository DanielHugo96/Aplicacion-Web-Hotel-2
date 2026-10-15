package pe.hotel.reception.platform;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bloque {@code meta} del envelope común. Siempre lleva {@code correlationId}; las listas añaden
 * {@code page}, {@code size} y {@code totalElements}.
 * Ver docs/especificacion/03-contrato-comun.md § Respuestas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Meta(String correlationId, Integer page, Integer size, Long totalElements) {

  public static Meta of(String correlationId) {
    return new Meta(correlationId, null, null, null);
  }

  public static Meta of(String correlationId, int page, int size, long totalElements) {
    return new Meta(correlationId, page, size, totalElements);
  }
}
