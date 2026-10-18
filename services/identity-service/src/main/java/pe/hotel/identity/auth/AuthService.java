package pe.hotel.identity.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.identity.persona.DatosPersona;
import pe.hotel.identity.persona.Persona;
import pe.hotel.identity.persona.PersonaRepository;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.CorrelationId;
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
  private final BienvenidaNotifier bienvenida;

  public AuthService(
      PersonaRepository personas,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokens,
      BienvenidaNotifier bienvenida) {
    this.personas = personas;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.bienvenida = bienvenida;
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

  /**
   * Registro público: cuerpo JSON cerrado que fuerza el rol CLIENTE y no admite {@code idTipoPersona},
   * {@code roles}, {@code sedes} ni {@code estado}. Un campo de esos en el cuerpo lo rechaza el
   * deserializador con 400 {@code VALIDATION_ERROR} antes de llegar aquí, así que la elevación no
   * llega a intentarse.
   *
   * <p>El correo se normaliza a minúsculas, es la clave del UNIQUE de {@code persona}.
   *
   * @throws ApiException {@code VALIDATION_ERROR} por formato o límite, {@code DUPLICATE_RESOURCE} si
   *     el correo normalizado o el par (tipoDocumento, documento) ya existen
   */
  @Transactional
  public PersonaView registrar(RegistroRequest request) {
    Map<String, String> errores = new LinkedHashMap<>();
    String tipoDocumento = DatosPersona.normalizarTipoDocumento(request.tipoDocumento());
    String documento = request.documento() == null ? null : request.documento().trim();
    String nombre = request.nombre() == null ? null : request.nombre().trim();
    String apellido = request.apellido() == null ? null : request.apellido().trim();
    String correo = Claves.normalizarCorreo(request.correo());
    String clave = request.clave();

    if (!DatosPersona.tipoDocumentoValido(tipoDocumento)) {
      errores.put("tipoDocumento", "debe ser DNI, PASAPORTE o CE");
    }
    if (!DatosPersona.documentoValido(tipoDocumento, documento)) {
      errores.put("documento", "DNI de 8 digitos; PASAPORTE o CE de 6 a 20 alfanumericos");
    }
    if (!DatosPersona.textoValido(nombre, DatosPersona.MAX_NOMBRE)) {
      errores.put("nombre", "obligatorio, hasta 100 caracteres");
    }
    if (!DatosPersona.textoValido(apellido, DatosPersona.MAX_NOMBRE)) {
      errores.put("apellido", "obligatorio, hasta 100 caracteres");
    }
    if (!Claves.correoValido(correo)) {
      errores.put("correo", "formato de correo invalido");
    }
    if (!Claves.cumplePolitica(clave)) {
      errores.put("clave", "12-72 bytes UTF-8");
    }
    if (!errores.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, errores);
    }

    Persona persona = crearCliente(tipoDocumento, documento, nombre, apellido, correo, clave);
    // Misma transacción: si la persona se crea, la bienvenida queda encolada.
    bienvenida.encolar(persona, CorrelationId.current());
    log.info("registro_ok id_persona={} rol=CLIENTE", persona.id());
    return PersonaView.de(persona);
  }

  private Persona crearCliente(
      String tipoDocumento,
      String documento,
      String nombre,
      String apellido,
      String correo,
      String clave) {
    comprobarDuplicados(tipoDocumento, documento, correo);
    int id;
    try {
      id =
          personas.insertar(
              tipoDocumento,
              documento,
              nombre,
              apellido,
              correo,
              passwordEncoder.encode(clave),
              PersonaRepository.ID_TIPO_CLIENTE);
    } catch (DuplicateKeyException carrera) {
      // Dos registros simultáneos con el mismo correo o documento: el UNIQUE es la última red.
      comprobarDuplicados(tipoDocumento, documento, correo);
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("correo", "ya registrado"));
    }
    return personas.buscarPorId(id).orElseThrow();
  }

  private void comprobarDuplicados(String tipoDocumento, String documento, String correo) {
    if (personas.existeCorreo(correo)) {
      throw new ApiException(
          ErrorCode.DUPLICATE_RESOURCE, Map.of("correo", "ya registrado por otra persona"));
    }
    if (personas.existeDocumento(tipoDocumento, documento)) {
      throw new ApiException(
          ErrorCode.DUPLICATE_RESOURCE, Map.of("documento", "ya registrado por otra persona"));
    }
  }
}