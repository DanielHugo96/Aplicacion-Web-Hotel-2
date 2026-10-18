package pe.hotel.identity.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.hotel.identity.auth.Claves;
import pe.hotel.identity.platform.CachedBodyHttpServletRequest;
import pe.hotel.identity.platform.ErrorCode;
import pe.hotel.identity.platform.ResponseWriter;

/**
 * Rate limit de las rutas públicas de identity: 5 intentos/min por IP+cuenta en login y 3/min por IP
 * en registro (docs/especificacion/03-contrato-comun.md § Identidad y sedes y
 * docs/contracts/errors.md § Genéricos, {@code RATE_LIMITED}).
 *
 * <p>Al superarse responde 429 con {@code Retry-After} y el envelope del catálogo. El contador es
 * por par remoto real: no se lee {@code X-Forwarded-For}, porque un cliente podría falsearlo para
 * saltarse justamente el límite que le protege.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  public static final String LOGIN = "/api/auth/login";
  public static final String REGISTRO = "/api/auth/registro";

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimiter limiter;
  private final int loginPorMinuto;
  private final int registroPorMinuto;
  private final ObjectMapper mapper = new ObjectMapper();

  public RateLimitFilter(RateLimiter limiter, int loginPorMinuto, int registroPorMinuto) {
    this.limiter = limiter;
    this.loginPorMinuto = loginPorMinuto;
    this.registroPorMinuto = registroPorMinuto;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return true;
    }
    String uri = request.getRequestURI();
    return !LOGIN.equals(uri) && !REGISTRO.equals(uri);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
    boolean login = LOGIN.equals(cached.getRequestURI());
    String clave =
        (login ? "login|" + ip(cached) + "|" + correo(cached) : "registro|" + ip(cached));
    RateLimiter.Decision decision =
        limiter.registrar(clave, login ? loginPorMinuto : registroPorMinuto);
    if (decision.permitido()) {
      filterChain.doFilter(cached, response);
      return;
    }
    log.warn("rate_limited ruta={} limite_por_minuto={}", cached.getRequestURI(), login ? loginPorMinuto : registroPorMinuto);
    response.setHeader("Retry-After", String.valueOf(decision.retryAfterSegundos()));
    ResponseWriter.writeError(response, ErrorCode.RATE_LIMITED);
  }

  /** Par remoto real; no se confía en cabeceras de identidad ni de reenvío. */
  private String ip(HttpServletRequest request) {
    String remoto = request.getRemoteAddr();
    return remoto == null ? "desconocida" : remoto;
  }

  /** Correo normalizado del cuerpo de login; si el cuerpo no se puede leer, la clave es la IP. */
  private String correo(CachedBodyHttpServletRequest request) {
    try {
      JsonNode cuerpo =
          mapper.readTree(new String(request.body(), StandardCharsets.UTF_8));
      JsonNode valor = cuerpo == null ? null : cuerpo.get("correo");
      String correo = valor == null || valor.isNull() ? "" : Claves.normalizarCorreo(valor.asText());
      return correo == null ? "" : correo;
    } catch (IOException cuerpoIlegible) {
      return "";
    }
  }
}