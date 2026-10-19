package pe.hotel.identity.client;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.hotel.identity.auth.ServiceClient;
import pe.hotel.identity.auth.ServiceClientRepository;
import pe.hotel.identity.auth.TokenIssuer;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ErrorCode;

/**
 * Obtiene y guarda en caché el token máquina de identity para una audiencia destino (p. ej.
 * {@code hotel}), usando las credenciales propias del servicio registradas en {@code
 * service_client} y el emisor local {@link TokenIssuer}.
 *
 * <p>El token se reutiliza hasta que quede menos de un minuto de vida; así se evita pedir uno nuevo
 * en cada llamada Feign y se tolera una expiración marginal sin fallar la validación de sedes.
 */
@Component
public class MachineTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(MachineTokenProvider.class);

  private static final long RESERVA_SEGUNDOS = 60;

  private final ServiceClientRepository clientes;
  private final TokenIssuer tokens;

  private String clienteId;
  private String audiencia;
  private String token;
  private Instant expiracion;

  public MachineTokenProvider(ServiceClientRepository clientes, TokenIssuer tokens) {
    this.clientes = clientes;
    this.tokens = tokens;
  }

  /**
   * Token máquina válido para {@code audiencia}, con los scopes que {@code clientId} tiene en su
   * allowlist para esa audiencia.
   *
   * @throws ApiException {@code DEPENDENCY_UNAVAILABLE} si el cliente no está configurado, no está
   *     activo o no autoriza esa audiencia
   */
  public synchronized String tokenPara(String clientId, String audiencia) {
    if (token != null
        && expiracion != null
        && Instant.now().isBefore(expiracion.minusSeconds(RESERVA_SEGUNDOS))
        && this.clienteId.equals(clientId)
        && this.audiencia.equals(audiencia)) {
      return token;
    }
    ServiceClient cliente =
        clientes.buscar(clientId).orElseThrow(() -> hotelNoDisponible("cliente_no_configurado"));
    if (!cliente.estado()) {
      throw hotelNoDisponible("cliente_inactivo");
    }
    if (!cliente.admiteAudiencia(audiencia)) {
      throw hotelNoDisponible("audience_fuera_de_allowlist");
    }
    List<String> scopes = cliente.scopesDe(audiencia);
    String nuevo = tokens.emitirMaquina(clientId, audiencia, String.join(" ", scopes));
    this.clienteId = clientId;
    this.audiencia = audiencia;
    this.token = nuevo;
    this.expiracion = Instant.now().plusSeconds(tokens.ttlMaquinaSegundos());
    log.info(
        "token_maquina_obtenido client_id={} audience={} scopes={}",
        clientId,
        audiencia,
        scopes.size());
    return nuevo;
  }

  private ApiException hotelNoDisponible(String motivo) {
    log.warn("hotel_no_disponible motivo={}", motivo);
    return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  }
}
