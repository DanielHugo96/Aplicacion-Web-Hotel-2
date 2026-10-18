package pe.hotel.identity.auth;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.hotel.identity.persona.Persona;
import pe.hotel.identity.persona.PersonaRepository;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ErrorCode;

/**
 * Login y emisión de sesión.
 *
 * <p>La credencial se verifica con BCrypt en Java contra el {@code clave_hash} de la persona; en SQL
 * no hay comparación de texto plano. El fallo es siempre el mismo 401 {@code INVALID_CREDENTIALS}:
 * no distingue correo inexistente de clave incorrecta ni de persona inactiva
 * (docs/especificacion/microservicios/identity/api.md § REST).
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final PersonaRepository personas;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokens;

  public AuthService(
      PersonaRepository personas, PasswordEncoder passwordEncoder, TokenIssuer tokens) {
    this.personas = personas;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
  }

  /**
   * Login público. No lleva {@code Idempotency-Key}: cada intento es un login nuevo, no una mutación
   * idempotente.
   *
   * @throws ApiException {@code INVALID_CREDENTIALS} ante cualquier fallo, con el mismo mensaje
   */
  public AuthResponse login(LoginRequest request) {
    String correo = request == null ? null : Claves.normalizarCorreo(request.correo());
    String clave = request == null ? null : request.clave();
    Optional<Persona> persona = correo == null ? Optional.empty() : personas.buscarPorCorreo(correo);
    // El log dice si se rechazó, nunca el correo ni la clave.
    if (persona.isEmpty() || !persona.get().estado()) {
      log.info("login_rechazado motivo=credenciales_invalidas");
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!Claves.coincide(passwordEncoder, clave, persona.get().claveHash())) {
      log.info("login_rechazado motivo=credenciales_invalidas");
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    log.info("login_ok id_persona={} rol={}", persona.get().id(), persona.get().codigoRol());
    return AuthResponse.de(
        persona.get(),
        tokens.emitirPersona(persona.get()),
        (int) tokens.ttlPersonaSegundos());
  }
}