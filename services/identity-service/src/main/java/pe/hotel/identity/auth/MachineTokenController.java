package pe.hotel.identity.auth;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ErrorCode;

/**
 * Emisión del token máquina. Solo se autentica con Basic de cliente de servicio: la cadena acepta la
 * ruta porque el filtro Basic ya dejó la identidad puesta, y si no es una identidad de servicio se
 * responde 401. Un token de persona no sirve aquí
 * (docs/especificacion/03-contrato-comun.md § Servicio a servicio).
 *
 * <p>Respuesta estándar sin envelope, con el destino en {@code aud} y el scope como string separado
 * por espacios.
 */
@RestController
@RequestMapping("/internal/auth")
public class MachineTokenController {

  private final MachineTokenService machineTokens;

  public MachineTokenController(MachineTokenService machineTokens) {
    this.machineTokens = machineTokens;
  }

  @PostMapping(
      value = "/token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public TokenResponse token(
      @RequestParam(name = "grant_type", required = false) String grantType,
      @RequestParam(name = "audience", required = false) String audience,
      Authentication authentication) {
    if (!(authentication instanceof ServiceClientAuthentication servicio)) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return machineTokens.emitir(servicio, grantType, audience);
  }
}