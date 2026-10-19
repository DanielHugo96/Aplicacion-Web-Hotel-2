package pe.hotel.hotel.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
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

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
    return build(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
    return build(codeForStatus(exception.getStatusCode()), Map.of());
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

  private ErrorCode codeForStatus(HttpStatusCode statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    return switch (status) {
      case BAD_REQUEST -> ErrorCode.VALIDATION_ERROR;
      case UNAUTHORIZED -> ErrorCode.UNAUTHENTICATED;
      case FORBIDDEN -> ErrorCode.FORBIDDEN;
      case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
      case PRECONDITION_FAILED -> ErrorCode.PRECONDITION_FAILED;
      case PRECONDITION_REQUIRED -> ErrorCode.PRECONDITION_REQUIRED;
      case TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMITED;
      case INTERNAL_SERVER_ERROR -> ErrorCode.INTERNAL_ERROR;
      default -> ErrorCode.INTERNAL_ERROR;
    };
  }
}
