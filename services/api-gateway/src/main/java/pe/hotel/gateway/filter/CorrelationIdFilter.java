package pe.hotel.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Genera {@code X-Correlation-Id} (UUID) cuando el cliente no lo envía,
 * lo propaga aguas abajo y lo devuelve en la respuesta.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-Correlation-Id";
  public static final String ATTRIBUTE = "hotel.correlationId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    String finalId = correlationId.trim();
    ServerHttpRequest mutated = exchange.getRequest().mutate()
        .header(HEADER, finalId)
        .build();
    ServerWebExchange mutatedExchange = exchange.mutate()
        .request(mutated)
        .build();
    mutatedExchange.getAttributes().put(ATTRIBUTE, finalId);
    mutatedExchange.getResponse().getHeaders().set(HEADER, finalId);
    return chain.filter(mutatedExchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
