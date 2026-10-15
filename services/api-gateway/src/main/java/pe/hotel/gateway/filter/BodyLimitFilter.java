package pe.hotel.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pe.hotel.gateway.config.GatewayProperties;
import pe.hotel.gateway.error.GatewayErrorWriter;
import reactor.core.publisher.Mono;

/**
 * Limita el cuerpo de la petición a 1MiB (GATEWAY_MAX_BODY_BYTES).
 * Al excederse responde 413 PAYLOAD_TOO_LARGE con envelope.
 */
@Component
public class BodyLimitFilter implements GlobalFilter, Ordered {

  private final GatewayProperties properties;
  private final GatewayErrorWriter errors;

  public BodyLimitFilter(GatewayProperties properties, GatewayErrorWriter errors) {
    this.properties = properties;
    this.errors = errors;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long max = properties.getMaxBodyBytes();
    long length = exchange.getRequest().getHeaders().getContentLength();
    if (length > max) {
      return errors.payloadTooLarge(exchange);
    }
    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
    if (header != null) {
      try {
        if (Long.parseLong(header.trim()) > max) {
          return errors.payloadTooLarge(exchange);
        }
      } catch (NumberFormatException ignored) {
        // Sin Content-Length legible: deja pasar; el downstream decide.
      }
    }
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 30;
  }
}
