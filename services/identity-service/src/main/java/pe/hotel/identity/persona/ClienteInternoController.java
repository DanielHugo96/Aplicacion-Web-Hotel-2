package pe.hotel.identity.persona;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ApiResponse;
import pe.hotel.identity.platform.CorrelationId;
import pe.hotel.identity.platform.ErrorCode;
import pe.hotel.identity.platform.Meta;

/**
 * Lectura máquina de cliente vigente. Cuelga de {@code /internal}, como el resto de controladores
 * internos del repositorio, y no del prefijo {@code /api} de las rutas humanas
 * (docs/especificacion/microservicios/identity/api.md § REST y docs/contracts/openapi/identity.yaml).
 *
 * <p>Exige el scope {@code clientes:read} del token máquina; no genera auditoría de lectura humana.
 */
@RestController
@RequestMapping("/internal")
public class ClienteInternoController {

  private final PersonaService personas;

  public ClienteInternoController(PersonaService personas) {
    this.personas = personas;
  }

  @GetMapping("/clientes/{id}")
  public ResponseEntity<ApiResponse<PersonaController.ClienteVigenteView>> clienteVigente(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id) {

    validarTokenMaquinaConScope(jwt, "clientes:read");

    ClienteVigente vigente = personas.clienteVigente(id);
    return ResponseEntity.ok()
        .body(ApiResponse.ok(
            new PersonaController.ClienteVigenteView(vigente), Meta.of(CorrelationId.current())));
  }

  private void validarTokenMaquinaConScope(Jwt jwt, String scopeRequerido) {
    String scope = jwt.getClaimAsString("scope");
    if (scope == null || !scope.contains(scopeRequerido)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }
}
