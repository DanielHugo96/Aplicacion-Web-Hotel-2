package pe.hotel.reception.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

  @ExceptionHandler(ApiDataException.class)
  public ResponseEntity<ApiResponse<Object>> handleApiData(ApiDataException exception) {
    ErrorCode code = exception.code();
    ApiResponse<Object> body =
        new ApiResponse<>(
            false,
            code.uiMessage(),
            exception.data(),
            ApiError.of(code, exception.fields()),
            Meta.of(CorrelationId.current()));
    return ResponseEntity.status(code.status()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return build(ErrorCode.VALIDATION_ERROR, fields);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
    return build(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
  }

  /** JSON cerrado: campo no declarado (fechaEntrada, precios, nombre...) o cuerpo mal formado. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    String message = exception.getMessage() == null ? "" : exception.getMessage();
    Matcher unknown = Pattern.compile("Unrecognized field \"([^\"]+)\"").matcher(message);
    if (unknown.find()) {
      fields.put(unknown.group(1), "campo no permitido");
    } else {
      fields.put("body", "JSON inválido o con tipos incorrectos");
    }
    return build(ErrorCode.VALIDATION_ERROR, fields);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
    return build(ErrorCode.VALIDATION_ERROR, Map.of(exception.getName(), "formato inválido"));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
      MissingServletRequestParameterException exception) {
    return build(ErrorCode.VALIDATION_ERROR, Map.of(exception.getParameterName(), "obligatorio"));
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
