package pe.hotel.identity.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.identity.platform.ApiResponse;
import pe.hotel.identity.platform.CorrelationId;
import pe.hotel.identity.platform.Meta;

/**
 * Rutas públicas de autenticación y cuenta propia.
 *
 * <p>Cada método devuelve el envelope común {@code success/message/data/meta}; el token máquina y el
 * JWKS, en sus controladores, son las dos excepciones sin envelope del contrato.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Login: 200 con la forma legacy {@code Auth} y un JWT RS256 de 15 min. El 401 {@code
   * INVALID_CREDENTIALS} es genérico y no lleva {@code Idempotency-Key}.
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
    AuthResponse auth = authService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(auth, Meta.of(CorrelationId.current())));
  }

  /**
   * Sesión propia: 200 con la Persona del {@code sub} del token, con {@code ETag} para el PUT
   * siguiente. No acepta ninguna cabecera de identidad: el JWT validado es la única fuente.
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<PersonaView>> me(@AuthenticationPrincipal Jwt jwt) {
    PersonaView persona = authService.miPerfil(jwt.getSubject());
    return ResponseEntity.ok()
        .eTag(ETag.de(persona.version()))
        .body(ApiResponse.ok(persona, Meta.of(CorrelationId.current())));
  }

  /**
   * Edita el perfil propio: 200 con la {@code version} incrementada, 428 sin {@code If-Match} y 412
   * con una versión que no es la vigente. Solo nombre, apellido y fotoUrl.
   */
  @PutMapping("/me")
  public ResponseEntity<ApiResponse<PersonaView>> actualizarPerfil(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody PerfilRequest request) {
    PersonaView persona = authService.actualizarPerfil(jwt.getSubject(), request, ifMatch);
    return ResponseEntity.ok()
        .eTag(ETag.de(persona.version()))
        .body(ApiResponse.ok(persona, Meta.of(CorrelationId.current())));
  }

  /**
   * Registro público: 201 con la Persona creada y rol CLIENTE forzado. Exige {@code
   * Idempotency-Key} y un cuerpo JSON cerrado; un {@code idTipoPersona} en el body es 400 {@code
   * VALIDATION_ERROR} y no crea la fila.
   */
  @PostMapping("/registro")
  public ResponseEntity<ApiResponse<PersonaView>> registro(@RequestBody RegistroRequest request) {
    PersonaView persona = authService.registrar(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(persona, Meta.of(CorrelationId.current())));
  }
}