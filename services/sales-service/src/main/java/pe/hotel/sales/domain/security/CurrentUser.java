package pe.hotel.sales.domain.security;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Usuario autenticado a partir del JWT validado (nunca de cabeceras falsificables). Expone el
 * sujeto (idPersona), los roles (ADMIN/EMPLEADO/CLIENTE) y las sedes asignadas (solo EMPLEADO).
 */
@Component
public class CurrentUser {

  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_EMPLEADO = "EMPLEADO";
  public static final String ROLE_CLIENTE = "CLIENTE";

  public String actor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return "anonymous";
    }
    if (auth.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return auth.getName();
  }

  public Integer subjectId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getSubject();
      if (sub != null) {
        try {
          return Integer.valueOf(sub);
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  public Set<String> roles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Set.of();
    }
    return auth.getAuthorities().stream()
        .map(
            a -> {
              String authority = a.getAuthority();
              if (authority.startsWith("ROLE_")) {
                return authority.substring("ROLE_".length());
              }
              return null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public List<Integer> sedes() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth.getPrincipal() instanceof Jwt jwt)) {
      return List.of();
    }
    Object sedes = jwt.getClaim("sedes");
    if (sedes instanceof List<?> list) {
      return list.stream()
          .filter(Number.class::isInstance)
          .map(value -> ((Number) value).intValue())
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

  public boolean puedeAccederASede(Integer idHotel) {
    if (isAdmin()) {
      return true;
    }
    if (isEmpleado()) {
      return sedes().contains(idHotel);
    }
    return false;
  }
}
