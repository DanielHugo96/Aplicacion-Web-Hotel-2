package pe.hotel.identity.platform;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones de negocio y de validación al envelope común con códigos del catálogo.
 * Ver docs/especificacion/03-contrato-comun.md § Respuestas y docs/contracts/errors.md § Genéricos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApi(ApiException exception) {
    return build(exception.code(), exception.fields());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return build(ErrorCode.VALIDATION_ERROR, fields);
  }

  /**
   * Cuerpo que no se puede leer contra el DTO: JSON mal formado, tipo incorrecto o campo no
   * declarado en un JSON cerrado. Nunca se devuelve el detalle del parser al cliente, solo el código
   * del catálogo.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
    Map<String, String> causa = causaDe(exception);
    log.warn("cuerpo no legible: {}", causa);
    return build(ErrorCode.VALIDATION_ERROR, Map.of("cuerpo", "JSON invalido o con campos no permitidos"));
  }

  private Map<String, String> causaDe(HttpMessageNotReadableException exception) {
    for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
      if (causa instanceof UnrecognizedPropertyException) {
        return Map.of(
            "campo",
            String.valueOf(((UnrecognizedPropertyException) causa).getPropertyName()));
      }
    }
    return Map.of("cuerpo", exception.getMessage() == null ? "ilegible" : exception.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
    return build(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
    // Nunca se devuelve stacktrace ni SQL al cliente; el correlationId permite ubicarlo en los logs.
    log.error("unhandled error: {}", exception.getMessage());
    return build(ErrorCode.INTERNAL_ERROR, Map.of());
  }

  private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, Map<String, String> fields) {
    Meta meta = Meta.of(CorrelationId.current());
    ApiResponse<Void> body = ApiResponse.fail(code, ApiError.of(code, fields), meta);
    return ResponseEntity.status(code.status()).body(body);
  }
}
