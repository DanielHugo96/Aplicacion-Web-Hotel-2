package pe.hotel.identity.auth;

import java.util.List;
import java.util.Map;

/**
 * Cliente de servicio de {@code service_client}: identidad de máquina con su allowlist de audiencia
 * y scopes. No comparte credencial con ninguna persona
 * (docs/especificacion/03-contrato-comun.md § Servicio a servicio).
 *
 * @param audienciasScopes mapa {@code <audiencia> -> [scope]}; el token solo puede llevar los
 *     scopes de la audiencia que pide
 */
public record ServiceClient(String clientId, String secretHash, Map<String, List<String>> audienciasScopes, boolean estado) {

  public ServiceClient {
    audienciasScopes = audienciasScopes == null ? Map.of() : Map.copyOf(audienciasScopes);
  }

  /** Scopes concedidos para una audiencia, o vacío si el cliente no la tiene en su allowlist. */
  public List<String> scopesDe(String audiencia) {
    return audienciasScopes.getOrDefault(audiencia, List.of());
  }

  public boolean admiteAudiencia(String audiencia) {
    return audienciasScopes.containsKey(audiencia);
  }
}