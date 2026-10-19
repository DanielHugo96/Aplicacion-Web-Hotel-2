package pe.hotel.identity.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Patrón de idempotencia para mutaciones de negocio: exige {@code Idempotency-Key} (UUID), reserva la
 * clave única {@code (actor, method, plantillaRuta, key)} con el hash del request, devuelve {@code 202}
 * mientras está en curso, {@code 409 IDEMPOTENCY_CONFLICT} ante la misma clave con otro request y el
 * resultado terminal guardado en los reintentos.
 * Ver docs/especificacion/03-contrato-comun.md § Headers y § Persistencia transversal.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE");

  /**
   * Rutas sin idempotencia: no ejecutan una mutación de negocio. El registro público sí la ejecuta,
   * así que no está aquí: exige {@code Idempotency-Key} como fija docs/contracts/openapi/identity.yaml
   * § Registro.
   */
  private static final Set<String> EXEMPT = Set.of("/api/auth/login", "/api/auth/social");

  private static final Pattern ID_SEGMENT =
      Pattern.compile(
          "\\d+|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final ApiIdempotencyRepository repository;
  private final IdempotencyCustomizer customizer;

  public IdempotencyFilter(ApiIdempotencyRepository repository) {
    this(repository, null);
  }

  public IdempotencyFilter(
      ApiIdempotencyRepository repository, IdempotencyCustomizer customizer) {
    this.repository = repository;
    this.customizer = customizer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!applies(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
    String header = cached.getHeader("Idempotency-Key");
    if (header == null || header.isBlank()) {
      ResponseWriter.writeError(
          response,
          ErrorCode.VALIDATION_ERROR,
          Map.of("Idempotency-Key", "obligatorio en POST/PUT/DELETE de negocio"));
      return;
    }

    UUID key;
    try {
      key = UUID.fromString(header.trim());
    } catch (IllegalArgumentException invalid) {
      ResponseWriter.writeError(
          response, ErrorCode.VALIDATION_ERROR, Map.of("Idempotency-Key", "debe ser un UUID"));
      return;
    }

    String actor = actorOf(cached);
    String method = cached.getMethod();
    String route = routeTemplate(cached.getRequestURI());
    String hash = hashOf(cached);

    Optional<ApiIdempotencyRepository.IdempotencyRow> existing =
        repository.find(actor, method, route, key);
    if (existing.isPresent()) {
      ApiIdempotencyRepository.IdempotencyRow row = existing.get();
      if (!row.requestHash().equals(hash)) {
        ResponseWriter.writeError(
            response,
            ErrorCode.IDEMPOTENCY_CONFLICT,
            Map.of("Idempotency-Key", "misma clave con un request distinto"));
        return;
      }
      if ("COMPLETADA".equals(row.status())) {
        ResponseWriter.writeJson(
            response, row.httpStatus() == null ? 200 : row.httpStatus(), row.response());
      } else {
        writeInProgress(response, row);
      }
      return;
    }

    repository.reserve(actor, method, route, key, hash);
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(cached, wrapper);
      int status = wrapper.getStatus();
      if (status >= 500) {
        repository.release(actor, method, route, key);
      } else {
        byte[] body = wrapper.getContentAsByteArray();
        String json = new String(body, StandardCharsets.UTF_8);
        repository.complete(
            actor, method, route, key, status, json.isBlank() ? "null" : json, locationOf(wrapper));
      }
      wrapper.copyBodyToResponse();
    } catch (RuntimeException | IOException | ServletException failure) {
      repository.release(actor, method, route, key);
      throw failure;
    }
  }

  private boolean applies(HttpServletRequest request) {
    String uri = request.getRequestURI();
    // Rutas retiradas del catálogo fijo: responden 409 sin ejecutar efectos, sin Idempotency-Key.
    if (uri.equals("/api/tipopersona/registrar") || uri.startsWith("/api/tipopersona/eliminar/")) {
      return false;
    }
    return MUTATING.contains(request.getMethod())
        && uri.startsWith("/api/")
        && !EXEMPT.contains(uri);
  }

  private void writeInProgress(
      HttpServletResponse response, ApiIdempotencyRepository.IdempotencyRow row) throws IOException {
    if (row.resourceId() != null) {
      response.setHeader("Location", row.resourceId());
    }
    response.setHeader("Retry-After", "1");
    String data = row.resourceId() == null ? "null" : "{\"reference\":\"" + ResponseWriter.escape(row.resourceId()) + "\"}";
    ResponseWriter.writeJson(response, 202, ResponseWriter.successEnvelope("En curso", data));
  }

  private String locationOf(ContentCachingResponseWrapper wrapper) {
    String location = wrapper.getHeader("Location");
    return location == null ? null : location;
  }

  /** Actor por defecto: el {@code sub} del JWT validado; {@code anonymous} en rutas públicas. */
  private String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      if (authentication.getPrincipal() instanceof Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
          return subject;
        }
      }
      return authentication.getName();
    }
    return "anonymous";
  }

  /** Actor que el servicio declara para esta ruta, o el de por defecto. */
  private String actorOf(CachedBodyHttpServletRequest request) {
    String declarado = customizer == null ? null : customizer.actorFor(request);
    return declarado == null || declarado.isBlank() ? currentActor() : declarado;
  }

  /** Hash que el servicio declara para esta ruta, o el SHA-256 del canónico. */
  private String hashOf(CachedBodyHttpServletRequest request) {
    String declarado = customizer == null ? null : customizer.hashFor(request);
    return declarado == null || declarado.isBlank()
        ? IdempotencyHashes.sha256Hex(canonical(request))
        : declarado;
  }

  /** Sustituye segmentos que son ids (numéricos o UUID) por {@code {id}}: plantilla de ruta estable. */
  static String routeTemplate(String uri) {
    String[] segments = uri.split("/", -1);
    StringBuilder out = new StringBuilder(uri.length());
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        out.append('/');
      }
      out.append(ID_SEGMENT.matcher(segments[i]).matches() ? "{id}" : segments[i]);
    }
    return out.toString();
  }

  /** Canónico del request que se hashea para detectar la misma clave con otros datos. */
  public static String canonical(CachedBodyHttpServletRequest request) {
    String query = request.getQueryString();
    String ifMatch = request.getHeader("If-Match");
    return request.getMethod()
        + "\n"
        + request.getRequestURI()
        + "\n"
        + (query == null ? "" : query)
        + "\n"
        + new String(request.body(), StandardCharsets.UTF_8)
        + "\n"
        + (ifMatch == null ? "" : ifMatch);
  }
}
