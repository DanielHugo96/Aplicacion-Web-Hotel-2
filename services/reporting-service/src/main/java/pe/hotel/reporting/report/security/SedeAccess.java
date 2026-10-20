package pe.hotel.reporting.report.security;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;

/**
 * Resuelve la sede que puede consultar el usuario autenticado.
 *
 * <ul>
 *   <li>ADMIN: cualquier sede; puede omitir idHotel en el dashboard (consolidado).
 *   <li>EMPLEADO: solo sedes de su claim {@code sedes}; omitir el filtro no lo hace global.
 *   <li>CLIENTE: prohibido (403).
 *   <li>Sede ajena o inexistente: 404 anti-enumeración.
 * </ul>
 */
@Component
public class SedeAccess {

  private final CurrentUser user;
  private final JdbcTemplate jdbcTemplate;

  public SedeAccess(CurrentUser user, JdbcTemplate jdbcTemplate) {
    this.user = user;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param idHotel sede pedida (null en el dashboard consolidado)
   * @param required true en los cuatro reportes por sede
   * @return la sede validada, o null para el consolidado ADMIN
   */
  public Integer require(Integer idHotel, boolean required) {
    if (idHotel == null && required) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("idHotel", "parámetro obligatorio"));
    }
    if (user.isCliente() || (!user.isAdmin() && !user.isEmpleado())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (idHotel == null) {
      if (user.isEmpleado()) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR, Map.of("idHotel", "obligatorio para tu rol"));
      }
      return null;
    }
    if (idHotel < 1) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("idHotel", "debe ser positivo"));
    }
    if (user.isEmpleado() && !user.sedes().contains(idHotel)) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    if (user.isAdmin() && !existe(idHotel)) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return idHotel;
  }

  private boolean existe(Integer idHotel) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM hotel_projection WHERE id_hotel = ?", Integer.class, idHotel);
    return count != null && count > 0;
  }
}
