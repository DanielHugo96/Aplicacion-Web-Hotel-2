package pe.hotel.reporting.platform;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bloque {@code meta} del envelope. Los reportes añaden la meta de consistencia eventual
 * ({@code updatedAt}, {@code consistency}, {@code degraded}, {@code bootstrapComplete}); las listas
 * añaden {@code page}, {@code size} y {@code totalElements}. Los campos que no aplican se omiten, y
 * {@code updatedAt} se emite siempre (null mientras no haya ningún checkpoint con timestamp de
 * ingestión), como fija {@code reporting.yaml} § ApiMetaReporte.
 * Ver docs/especificacion/03-contrato-comun.md § Respuestas y
 * docs/contracts/openapi/reporting.yaml § ApiMetaReporte.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Meta(
    String correlationId,
    @JsonInclude(JsonInclude.Include.ALWAYS) OffsetDateTime updatedAt,
    String consistency,
    Boolean degraded,
    Boolean bootstrapComplete,
    Integer page,
    Integer size,
    Long totalElements) {

  public static Meta of(String correlationId) {
    return new Meta(correlationId, null, null, null, null, null, null, null);
  }

  public static Meta report(
      String correlationId,
      OffsetDateTime updatedAt,
      boolean degraded,
      boolean bootstrapComplete,
      Integer page,
      Integer size,
      Long totalElements) {
    return new Meta(
        correlationId, updatedAt, "EVENTUAL", degraded, bootstrapComplete, page, size, totalElements);
  }

  public static Meta of(String correlationId, int page, int size, long totalElements) {
    return new Meta(correlationId, null, null, null, null, page, size, totalElements);
  }
}
