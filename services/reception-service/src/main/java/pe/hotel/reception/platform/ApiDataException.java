package pe.hotel.reception.platform;

import java.util.Map;

/**
 * Error de negocio que además devuelve un bloque {@code data} en el envelope, como el 409
 * {@code AMOUNT_MISMATCH} con su cotización y {@code closureId}. El código sigue saliendo del
 * catálogo de {@link ErrorCode}; no inventa códigos.
 */
public class ApiDataException extends RuntimeException {

  private final ErrorCode code;
  private final transient Map<String, String> fields;
  private final transient Object data;

  public ApiDataException(ErrorCode code, Object data, Map<String, String> fields) {
    super(code.uiMessage());
    this.code = code;
    this.data = data;
    this.fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, String> fields() {
    return fields;
  }

  public Object data() {
    return data;
  }
}
