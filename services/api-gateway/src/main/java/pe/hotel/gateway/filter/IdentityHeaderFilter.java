package pe.hotel.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Elimina cabeceras de identidad falsificables antes de reenviar.
 * La identidad solo llega por JWT validado; nunca por headers.
 */
@Component
public class IdentityHeaderFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest mutated = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove("X-User-Id");
          headers.remove("X-Role");
          headers.remove("X-Hotel-Id");
        })
        .build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 20;
  }
}
