package pe.hotel.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.hotel.identity.config.IdentityProperties;
import pe.hotel.identity.platform.CachedBodyHttpServletRequest;
import pe.hotel.identity.platform.IdempotencyCustomizer;
import pe.hotel.identity.platform.IdempotencyFilter;
import pe.hotel.identity.platform.IdempotencyHashes;

/**
 * Ajusta la idempotencia de las rutas de identity donde la convención por defecto no encaja.
 *
 * <ul>
 *   <li>{@code POST /api/auth/registro}: no hay identidad, así que el actor es el hash del correo
 *       normalizado y no la IP (docs/especificacion/microservicios/identity/base-de-datos.md § Tablas).
 *   <li>Las rutas cuyo cuerpo lleva credenciales ({@code registro} y {@code password}) hashean el
 *       canónico con HMAC: el {@code request_hash} guardado no debe permitir recuperar la clave por
 *       fuerza bruta, y la clave nunca se guarda en el payload de idempotencia
 *       (docs/especificacion/03-contrato-comun.md § Headers).
 * </ul>
 */
@Component
public class IdentityIdempotencyCustomizer implements IdempotencyCustomizer {

  public static final String RUTA_REGISTRO = "/api/auth/registro";
  public static final String RUTA_PASSWORD = "/api/auth/password";

  private static final Logger log = LoggerFactory.getLogger(IdentityIdempotencyCustomizer.class);

  private final IdentityProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Environment environment;

  private String claveHmac;
  private boolean efimera;

  public IdentityIdempotencyCustomizer(
      IdentityProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void cargarClave() {
    String declarada = properties.getIdempotencyHmacKey();
    if (declarada != null && !declarada.isBlank()) {
      claveHmac = declarada;
    } else if (esProduccion()) {
      throw new IllegalStateException(
          "IDENTITY_IDEMPOTENCY_HMAC_KEY (o SERVICE_CLIENT_SECRET) es obligatoria fuera de dev/demo:"
              + " sin ella el request_hash de un registro o un cambio de clave no lleva HMAC");
    } else {
      byte[] aleatorio = new byte[32];
      new SecureRandom().nextBytes(aleatorio);
      claveHmac = java.util.HexFormat.of().formatHex(aleatorio);
      efimera = true;
      log.warn(
          "idempotency_hmac_efimero aviso=sin_clave_HMAC_solo_valido_en_dev_y_demo"
              + " efecto=los_reintentos_tras_reiniciar_no_se_reproducen");
    }
    log.info("idempotency_hmac origen={}", efimera ? "efimera-en-memoria" : "configuracion");
  }

  private boolean esProduccion() {
    for (String perfil : environment.getActiveProfiles()) {
      if (perfil != null && perfil.startsWith("prod")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String actorFor(CachedBodyHttpServletRequest request) {
    if (!RUTA_REGISTRO.equals(request.getRequestURI())) {
      return null;
    }
    String correo = correoDelBody(request);
    // El actor es un hash: la columna guarda la identidad de la intención sin el correo en claro.
    return IdempotencyHashes.sha256Hex(correo == null ? "" : correo);
  }

  @Override
  public String hashFor(CachedBodyHttpServletRequest request) {
    String uri = request.getRequestURI();
    if (!RUTA_REGISTRO.equals(uri) && !RUTA_PASSWORD.equals(uri)) {
      return null;
    }
    return IdempotencyHashes.hmacSha256Hex(claveHmac, IdempotencyFilter.canonical(request));
  }

  private String correoDelBody(CachedBodyHttpServletRequest request) {
    try {
      JsonNode cuerpo = mapper.readTree(new String(request.body(), StandardCharsets.UTF_8));
      JsonNode valor = cuerpo == null ? null : cuerpo.get("correo");
      if (valor == null || valor.isNull()) {
        return null;
      }
      String normalizado = Claves.normalizarCorreo(valor.asText());
      return normalizado == null ? "" : normalizado;
    } catch (Exception cuerpoIlegible) {
      return null;
    }
  }
}