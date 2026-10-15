package pe.hotel.gateway.filter;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pe.hotel.gateway.config.GatewayProperties;
import pe.hotel.gateway.error.GatewayErrorWriter;
import reactor.core.publisher.Mono;

/**
 * Rate limit público de 60 req/min por IP (RATE_LIMIT_PUBLIC_RPM) sobre las
 * rutas públicas documentadas. En memoria del proceso (una instancia, sin Redis).
 * Al excederse responde 429 RATE_LIMITED con Retry-After.
 * Las rutas privadas no consumen este contador.
 */
@Component
public class PublicRateLimitFilter implements GlobalFilter, Ordered {

  private final GatewayProperties properties;
  private final GatewayErrorWriter errors;
  private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

  public PublicRateLimitFilter(GatewayProperties properties, GatewayErrorWriter errors) {
    this.properties = properties;
    this.errors = errors;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!isPublicRoute(exchange.getRequest())) {
      return chain.filter(exchange);
    }
    int limit = properties.getRateLimit().getPublicRpm();
    String ip = clientIp(exchange.getRequest());
    String key = ip + "|" + publicBucket(exchange.getRequest());
    Instant now = Instant.now();
    Deque<Instant> bucket = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (bucket) {
      Instant cutoff = now.minus(Duration.ofMinutes(1));
      while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) {
        bucket.pollFirst();
      }
      if (bucket.size() >= limit) {
        long retryAfter = Duration.between(bucket.peekFirst(), bucket.peekFirst().plus(Duration.ofMinutes(1)))
            .getSeconds();
        retryAfter = Math.max(1, Math.min(60, retryAfter));
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfter));
        return errors.rateLimited(exchange, retryAfter);
      }
      bucket.addLast(now);
    }
    return chain.filter(exchange);
  }

  /** Agrupa por recurso público para que el contador sea por IP y recurso/minuto. */
  static String publicBucket(ServerHttpRequest request) {
    String path = request.getPath().value();
    if (path.startsWith("/api/public/")) {
      return "public";
    }
    if (path.equals("/api/auth/login")) {
      return "login";
    }
    if (path.equals("/api/auth/registro")) {
      return "registro";
    }
    if (path.equals("/api/auth/social")) {
      return "social";
    }
    if (path.equals("/.well-known/jwks.json")) {
      return "jwks";
    }
    return "public";
  }

  static boolean isPublicRoute(ServerHttpRequest request) {
    String path = request.getPath().value();
    if (path.startsWith("/api/public/")) {
      return true;
    }
    if (request.getMethod() == HttpMethod.POST
        && (path.equals("/api/auth/login")
            || path.equals("/api/auth/registro")
            || path.equals("/api/auth/social"))) {
      return true;
    }
    return request.getMethod() == HttpMethod.GET && path.equals("/.well-known/jwks.json");
  }

  private static String clientIp(ServerHttpRequest request) {
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    InetSocketAddress remote = request.getRemoteAddress();
    return remote != null && remote.getAddress() != null
        ? remote.getAddress().getHostAddress()
        : "unknown";
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 40;
  }
}
