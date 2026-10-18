package pe.hotel.identity.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.hotel.identity.platform.ErrorCode;
import pe.hotel.identity.platform.ResponseWriter;

/**
 * Autentica {@code POST /internal/auth/token} con Basic {@code clientId:secret} contrastado
 * contra el {@code secret_hash} BCrypt de {@code service_client}.
 *
 * <p>Cualquier otra credencial es {@code 401 UNAUTHENTICATED}: ausente, Basic inválido, cliente
 * desconocido o inactivo, o un JWT de persona (que no sirve para /internal/**). Va dentro de la
 * cadena de seguridad, antes del filtro de token, para que la identidad que deja puesta la vea el
 * resto de la petición.
 * Ver docs/especificacion/03-contrato-comun.md § Servicio a servicio.
 */
public class MachineTokenBasicAuthenticationFilter extends OncePerRequestFilter {

  /** Única ruta de este filtro; el resto de /internal/** se autentica con token máquina. */
  public static final String RUTA = "/internal/auth/token";

  private static final String BASIC = "Basic ";
  private static final Logger log = LoggerFactory.getLogger(MachineTokenBasicAuthenticationFilter.class);

  private final ServiceClientRepository repository;
  private final PasswordEncoder passwordEncoder;

  public MachineTokenBasicAuthenticationFilter(
      ServiceClientRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || !RUTA.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<ServiceClientAuthentication> autenticado = autenticar(request);
    if (autenticado.isEmpty()) {
      // Log con `service` y `correlationId` del MDC; nunca la credencial.
      log.warn("service_client_rejected ruta={}", RUTA);
      ResponseWriter.writeError(response, ErrorCode.UNAUTHENTICATED);
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(autenticado.get());
    filterChain.doFilter(request, response);
  }

  private Optional<ServiceClientAuthentication> autenticar(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
      return Optional.empty();
    }
    String[] credenciales = decodificar(header.substring(BASIC.length()));
    if (credenciales == null) {
      return Optional.empty();
    }
    Optional<ServiceClient> cliente = repository.buscar(credenciales[0]);
    if (cliente.isEmpty() || !cliente.get().estado() || !coincide(credenciales[1], cliente.get())) {
      return Optional.empty();
    }
    log.info("service_client_authenticated client_id={}", cliente.get().clientId());
    return Optional.of(
        new ServiceClientAuthentication(cliente.get().clientId(), cliente.get().audienciasScopes()));
  }

  /** {@code clientId:secret} decodificado de base64; null si el Basic está mal formado. */
  private String[] decodificar(String codificado) {
    String credenciales;
    try {
      credenciales = new String(Base64.getDecoder().decode(codificado.trim()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException base64Invalido) {
      return null;
    }
    int separador = credenciales.indexOf(':');
    if (separador <= 0 || separador == credenciales.length() - 1) {
      return null;
    }
    return new String[] {
      credenciales.substring(0, separador), credenciales.substring(separador + 1)
    };
  }

  private boolean coincide(String secreto, ServiceClient cliente) {
    String hash = cliente.secretHash();
    if (secreto == null || secreto.isEmpty() || hash == null || hash.isEmpty()) {
      return false;
    }
    // BCrypt no acepta más de 72 bytes: nunca se trunca en silencio (docs/especificacion/
    // 03-contrato-comun.md § Límites de campos comunes).
    if (secreto.getBytes(StandardCharsets.UTF_8).length > 72) {
      return false;
    }
    try {
      return passwordEncoder.matches(secreto, hash);
    } catch (RuntimeException hashInvalido) {
      return false;
    }
  }
}