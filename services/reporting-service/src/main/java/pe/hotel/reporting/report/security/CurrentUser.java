package pe.hotel.reporting.report.security;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Usuario autenticado a partir del JWT validado (nunca de cabeceras falsificables). Expone los
 * roles (ADMIN/EMPLEADO/CLIENTE) y las sedes asignadas del empleado.
 * Ver docs/especificacion/03-contrato-comun.md § Identidad y sedes.
 */
@Component
public class CurrentUser {

  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_EMPLEADO = "EMPLEADO";
  public static final String ROLE_CLIENTE = "CLIENTE";

  public Set<String> roles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Set.of();
    }
    return auth.getAuthorities().stream()
        .map(
            a -> {
              String authority = a.getAuthority();
              return authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public List<Integer> sedes() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return List.of();
    }
    Object sedes = jwt.getClaim("sedes");
    if (sedes instanceof List<?> list) {
      return list.stream()
          .map(
              value -> {
                if (value instanceof Number number) {
                  return number.intValue();
                }
                if (value instanceof String text) {
                  try {
                    return Integer.valueOf(text);
                  } catch (NumberFormatException ignored) {
                    return null;
                  }
                }
                return null;
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }
    return List.of();
  }

  public boolean isAdmin() {
    return roles().contains(ROLE_ADMIN);
  }

  public boolean isEmpleado() {
    return roles().contains(ROLE_EMPLEADO);
  }

  public boolean isCliente() {
    return roles().contains(ROLE_CLIENTE);
  }
}
