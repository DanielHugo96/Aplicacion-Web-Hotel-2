package pe.hotel.reception.client;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.hotel.reception.platform.ApiException;
import pe.hotel.reception.platform.ErrorCode;

/**
 * Obtiene y cachea el token máquina de identity para una audiencia destino (identity, hotel o
 * sales), usando las credenciales propias del servicio (SERVICE_CLIENT_ID/SERVICE_CLIENT_SECRET).
 * Ver docs/especificacion/03-contrato-comun.md § Servicio a servicio.
 */
@Component
public class MachineTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(MachineTokenProvider.class);
  private static final long RESERVA_SEGUNDOS = 60;

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String tokenUrl;

  private final Map<String, CachedToken> cache = new HashMap<>();

  public MachineTokenProvider(
      @Value("${hotel.security.machine.client-id:}") String clientId,
      @Value("${hotel.security.machine.client-secret:}") String clientSecret,
      @Value("${hotel.identity.token-url:}") String tokenUrl) {
    this.restClient = RestClient.create(tokenUrl);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenUrl = tokenUrl;
  }

  public synchronized String tokenPara(String audiencia) {
    CachedToken cached = cache.get(audiencia);
    if (cached != null
        && Instant.now().isBefore(cached.expiracion().minusSeconds(RESERVA_SEGUNDOS))) {
      return cached.token();
    }
    if (clientId == null
        || clientId.isBlank()
        || clientSecret == null
        || clientSecret.isBlank()
        || tokenUrl == null
        || tokenUrl.isBlank()) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    String basic =
        Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
    TokenResponse resp =
        restClient
            .post()
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("grant_type=client_credentials&audience=" + audiencia)
            .retrieve()
            .onStatus(
                status -> status.isError(),
                (req, res) -> {
                  throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                })
            .body(TokenResponse.class);
    if (resp == null || resp.access_token() == null) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    cache.put(audiencia, new CachedToken(resp.access_token(), Instant.now().plusSeconds(240)));
    log.info("token_maquina_obtenido client_id={} audience={}", clientId, audiencia);
    return resp.access_token();
  }

  record CachedToken(String token, Instant expiracion) {}

  record TokenResponse(String access_token, String token_type, Long expires_in) {}
}
