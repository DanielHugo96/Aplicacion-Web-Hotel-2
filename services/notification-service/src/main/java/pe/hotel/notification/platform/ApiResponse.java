package pe.hotel.notification.platform;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope común {@code success/message/data/error/meta}.
 * Ver docs/especificacion/03-contrato-comun.md § Respuestas.
 *
 * @param <T> tipo de {@code data}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, ApiError error, Meta meta) {

  public static <T> ApiResponse<T> ok(T data, Meta meta) {
    return new ApiResponse<>(true, "OK", data, null, meta);
  }

  public static <T> ApiResponse<T> ok(String message, T data, Meta meta) {
    return new ApiResponse<>(true, message, data, null, meta);
  }

  public static <T> ApiResponse<T> fail(ErrorCode code, ApiError error, Meta meta) {
    return new ApiResponse<>(false, code.uiMessage(), null, error, meta);
  }

  public static <T> ApiResponse<T> fail(String message, ErrorCode code, Meta meta) {
    return new ApiResponse<>(false, message, null, ApiError.of(code), meta);
  }
}
