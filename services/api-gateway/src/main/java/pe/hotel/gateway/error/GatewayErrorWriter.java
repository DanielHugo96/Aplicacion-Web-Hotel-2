package pe.hotel.gateway.error;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pe.hotel.gateway.filter.CorrelationIdFilter;
import reactor.core.publisher.Mono;

/**
 * Respuestas de error propias del Gateway con la forma del envelope
 * (03-contrato-comun §Respuestas) y los códigos de errors.md §Reporting y Gateway.
 * Sin stacktrace ni SQL.
 */
@Component
public class GatewayErrorWriter {

  public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
    String correlationId = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);
    if (correlationId == null) {
      correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
    }
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    String json = "{\"success\":false"
        + ",\"message\":" + quoted(message)
        + ",\"data\":null"
        + ",\"error\":{\"code\":" + quoted(code) + ",\"fields\":{}}"
        + ",\"meta\":{\"correlationId\":" + quoted(correlationId) + "}}";
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    exchange.getResponse().getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
    return exchange.getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
  }

  private static String quoted(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "'") + "\"";
  }

  public Mono<Void> unauthenticated(ServerWebExchange exchange) {
    return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
        "Tu sesión expiró. Vuelve a iniciar sesión.");
  }

  public Mono<Void> payloadTooLarge(ServerWebExchange exchange) {
    return write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
        "Los datos enviados son demasiado grandes.");
  }

  public Mono<Void> rateLimited(ServerWebExchange exchange, long retryAfterSeconds) {
    exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
    Mono<Void> pending = write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
        "Demasiados intentos. Espera un momento.");
    return pending;
  }

  public Mono<Void> dependencyUnavailable(ServerWebExchange exchange) {
    return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
        "El servicio no está disponible. Reintenta en unos segundos.");
  }
}
