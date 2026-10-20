package pe.hotel.inventory.client;

import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;
import org.springframework.web.client.RestClient;

/**
 * Obtiene y guarda en caché el token máquina de identity para una audiencia destino (p. ej.
 * {@code hotel}), usando las credenciales propias del servicio ({@code SERVICE_CLIENT_ID /
 * SERVICE_CLIENT_SECRET}) y el emisor local.
 *
 * <p>El token se reutiliza hasta que quede menos de un minuto de vida.
 */
@Component
public class MachineTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(MachineTokenProvider.class);
  private static final long RESERVA_SEGUNDOS = 60;

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final String tokenUrl;

  private String audiencia;
  private String token;
  private Instant expiracion;

  public MachineTokenProvider(
      @Value("${hotel.security.machine.client-id:}") String clientId,
      @Value("${hotel.security.machine.client-secret:}") String clientSecret,
      @Value("${hotel.identity.token-url:}") String tokenUrl) {
    this.restClient = RestClient.create(tokenUrl);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenUrl = tokenUrl;
  }

  /**
   * Token máquina válido para {@code audiencia}. Las credenciales del servicio son las configuradas
   * en {@code hotel.security.machine.client-*} (client id completo, p. ej. {@code inventory-service});
   * {@code clientId} solo identifica al llamador y admite tanto el destino corto
   * ({@code inventory}) como el nombre completo, igual que la audiencia del token.
   *
   * @throws ApiException {@code DEPENDENCY_UNAVAILABLE} si faltan credenciales o identity no responde
   */
  public synchronized String tokenPara(String clientId, String audiencia) {
    String destino =
        this.clientId.endsWith("-service")
            ? this.clientId.substring(0, this.clientId.length() - "-service".length())
            : this.clientId;
    if (!this.clientId.equals(clientId) && !destino.equals(clientId)) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    if (token != null
        && expiracion != null
        && Instant.now().isBefore(expiracion.minusSeconds(RESERVA_SEGUNDOS))
        && this.audiencia != null
        && this.audiencia.equals(audiencia)) {
      return token;
    }
    if (this.clientId.isBlank() || clientSecret.isBlank() || tokenUrl.isBlank()) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    String basic =
        Base64.getEncoder().encodeToString((this.clientId + ":" + clientSecret).getBytes());
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
    this.audiencia = audiencia;
    this.token = resp.access_token();
    // identity emite TTL 5 min; conservamos un margen por si el reloj se desfasa.
    this.expiracion = Instant.now().plusSeconds(240);
    log.info("token_maquina_obtenido client_id={} audience={}", clientId, audiencia);
    return token;
  }

  record TokenResponse(String access_token, String token_type, Long expires_in) {}
}
