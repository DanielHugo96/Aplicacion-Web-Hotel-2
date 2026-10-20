package pe.hotel.hotel.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Parámetros de paginación de las listas del contrato ({@code Page}/{@code Size} de
 * {@code docs/contracts/openapi/hotel.yaml}): página 0-based, tamaño 1..100, orden estable por id.
 * Un valor fuera de rango es {@code VALIDATION_ERROR} con {@code fields.page} o {@code fields.size},
 * nunca un 500.
 */
public final class Pagination {

  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private Pagination() {}

  /** Valida los parámetros y devuelve el {@link Pageable} con el que consultar. */
  public static Pageable validar(int page, int size) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (page < 0) {
      fields.put("page", "page debe ser mayor o igual que 0");
    }
    if (size < 1 || size > MAX_SIZE) {
      fields.put("size", "size debe estar entre 1 y 100");
    }
    if (!fields.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, fields);
    }
    return PageRequest.of(page, size);
  }
}
