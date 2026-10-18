package pe.hotel.identity.auth;

import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Identidad de un cliente de servicio autenticado con Basic. Deliberadamente es un tipo propio: el
 * endpoint de token máquina solo lo acepta a él, así que un JWT de persona (que llega como
 * {@code JwtAuthenticationToken}) nunca puede pedir un token de servicio.
 */
public final class ServiceClientAuthentication extends AbstractAuthenticationToken {

  private final String clientId;
  private final Map<String, List<String>> audienciasScopes;

  public ServiceClientAuthentication(String clientId, Map<String, List<String>> audienciasScopes) {
    super(List.of(new SimpleGrantedAuthority("ROLE_SERVICE_CLIENT")));
    this.clientId = clientId;
    this.audienciasScopes = Map.copyOf(audienciasScopes);
    setAuthenticated(true);
  }

  public String clientId() {
    return clientId;
  }

  public Map<String, List<String>> audienciasScopes() {
    return audienciasScopes;
  }

  @Override
  public Object getPrincipal() {
    return clientId;
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public String getName() {
    return clientId;
  }
}