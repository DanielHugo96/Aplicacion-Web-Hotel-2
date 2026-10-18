package pe.hotel.identity.auth;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ErrorCode;

/**
 * Emisión del token máquina por {@code client_credentials}.
 *
 * <p>La credencial ya la verificó el filtro Basic; aquí solo se decide si esa identidad puede pedir
 * un token para el destino pedido: la audiencia tiene que estar en la allowlist del cliente
 * ({@code service_client.audiencias_scopes}) y el scope se limita a los de esa audiencia.
 * Ver docs/especificacion/03-contrato-comun.md § Servicio a servicio.
 */
@Service
public class MachineTokenService {

  private static final Logger log = LoggerFactory.getLogger(MachineTokenService.class);

  private static final String GRANT_TYPE = "client_credentials";

  private final TokenIssuer tokens;

  public MachineTokenService(TokenIssuer tokens) {
    this.tokens = tokens;
  }

  /**
   * @param servicio cliente autenticado con Basic
   * @param grantType debe ser {@code client_credentials}
   * @param audiencia destino del token; tiene que estar en la allowlist del cliente
   * @throws pe.hotel.identity.platform.ApiException {@code VALIDATION_ERROR} si el grant o la
   *     audiencia no valen
   */
  public TokenResponse emitir(ServiceClientAuthentication servicio, String grantType, String audiencia) {
    if (!GRANT_TYPE.equals(grantType)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, java.util.Map.of("grant_type", "solo client_credentials"));
    }
    if (audiencia == null
        || audiencia.isBlank()
        || !servicio.audienciasScopes().containsKey(audiencia)) {
      log.warn(
          "token_maquina_rechazado client_id={} motivo=audiencia_fuera_de_allowlist",
          servicio.clientId());
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, java.util.Map.of("audience", "destino no autorizado para este cliente"));
    }
    List<String> scopes = servicio.audienciasScopes().getOrDefault(audiencia, List.of());
    long ttl = tokens.ttlMaquinaSegundos();
    String token = tokens.emitirMaquina(servicio.clientId(), audiencia, String.join(" ", scopes));
    log.info(
        "token_maquina_emitido client_id={} audience={} scopes={}",
        servicio.clientId(),
        audiencia,
        scopes.size());
    return TokenResponse.de(token, ttl);
  }
}