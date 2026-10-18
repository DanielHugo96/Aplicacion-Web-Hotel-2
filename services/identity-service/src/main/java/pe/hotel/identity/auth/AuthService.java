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
  /**
   * Sesión propia: la Persona del {@code sub} del token validado, nunca la de una cabecera. Solo
   * devuelve lo suyo, así que no genera registro de auditoría (docs/especificacion/03-contrato-comun.md
   * § Identidad y sedes).
   *
   * @throws ApiException {@code RESOURCE_NOT_FOUND} si el token es válido pero la persona no existe
   */
  public PersonaView miPerfil(String sub) {
    return personas.buscarPorId(idDe(sub)).map(PersonaView::de).orElseThrow(
        () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  /** {@code sub} del token: un id de persona. Un valor que no lo es deja de ser una sesión válida. */
  private int idDe(String sub) {
    if (sub == null || sub.isBlank() || !sub.chars().allMatch(Character::isDigit)) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return Integer.parseInt(sub);
  }

  /**
   * Edita el perfil propio: nombre, apellido y fotoUrl. Exige {@code If-Match} y, con la versión
   * vigente, incrementa {@code version}. Rol, sedes, documento y estado no se tocan: el JSON cerrado
   * los rechaza antes de llegar aquí.
   *
   * @throws ApiException {@code PRECONDITION_REQUIRED} sin {@code If-Match}, {@code
   *     PRECONDITION_FAILED} si la versión no es la vigente, {@code VALIDATION_ERROR} por formato
   */
  @Transactional
  public PersonaView actualizarPerfil(String sub, PerfilRequest request, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    long versionEsperada = ETag.versionDe(ifMatch);
    Map<String, String> errores = validarPerfil(request);
    if (!errores.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, errores);
    }
    if (versionEsperada < 1) {
      throw new ApiException(
          ErrorCode.PRECONDITION_FAILED,
          Map.of("If-Match", "la versión enviada no es la vigente"));
    }
    int id = idDe(sub);
    int afectadas =
        personas.actualizarPerfil(
            id,
            request.nombre().trim(),
            request.apellido().trim(),
            request.fotoUrl(),
            versionEsperada);
    if (afectadas == 0) {
      throw new ApiException(
          ErrorCode.PRECONDITION_FAILED,
          Map.of("If-Match", "la versión enviada no es la vigente"));
    }
    Persona actualizada = personas.leerDespuesDeActualizar(id);
    log.info("perfil_actualizado id_persona={} version={}", id, actualizada.version());
    return PersonaView.de(actualizada);
  }

  private Map<String, String> validarPerfil(PerfilRequest request) {
    Map<String, String> errores = new LinkedHashMap<>();
    if (!DatosPersona.textoValido(request.nombre(), DatosPersona.MAX_NOMBRE)) {
      errores.put("nombre", "obligatorio, hasta 100 caracteres");
    }
    if (!DatosPersona.textoValido(request.apellido(), DatosPersona.MAX_NOMBRE)) {
      errores.put("apellido", "obligatorio, hasta 100 caracteres");
    }
    if (!fotoUrlValida(request.fotoUrl())) {
      errores.put("fotoUrl", "null, o una URL https o de /assets/ de hasta 500 caracteres");
    }
    return errores;
  }

  /**
   * La foto es una referencia, no una descarga: solo se admiten {@code https} o rutas {@code /assets/}
   * (docs/especificacion/03-contrato-comun.md § Límites de campos comunes). El backend nunca
   * descarga una URL arbitraria.
   */
  private boolean fotoUrlValida(String fotoUrl) {
    if (fotoUrl == null) {
      return true;
    }
    String valor = fotoUrl.trim();
    return valor.length() <= 500
        && (valor.startsWith("https://") || valor.startsWith("/assets/"));
  }

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