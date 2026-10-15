package pe.hotel.notification.platform;

import java.util.Map;

/**
 * Excepción de negocio con un código del catálogo. La traduce {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final transient Map<String, String> fields;

  public ApiException(ErrorCode code) {
    this(code, Map.of());
  }

  public ApiException(ErrorCode code, Map<String, String> fields) {
    super(code.uiMessage());
    this.code = code;
    this.fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, String> fields() {
    return fields;
  }
}
