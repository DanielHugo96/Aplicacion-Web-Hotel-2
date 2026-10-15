package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import pe.hotel.gateway.filter.IdentityHeaderFilter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Paso 6: las cabeceras falsificables nunca llegan al servicio destino.
 */
class IdentityHeaderFilterTest {

  @Test
  void eliminaCabecerasFalsificables() {
    IdentityHeaderFilter filter = new IdentityHeaderFilter();
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotel/listar")
        .header("X-User-Id", "1")
        .header("X-Role", "ADMIN")
        .header("X-Hotel-Id", "9")
        .header("Authorization", "Bearer token")
        .build();
    ServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    final ServerWebExchange[] capturado = new ServerWebExchange[1];
    when(chain.filter(any())).thenAnswer(inv -> {
      capturado[0] = inv.getArgument(0);
      return Mono.empty();
    });

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(capturado[0]).isNotNull();
    assertThat(capturado[0].getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    assertThat(capturado[0].getRequest().getHeaders().getFirst("X-Role")).isNull();
    assertThat(capturado[0].getRequest().getHeaders().getFirst("X-Hotel-Id")).isNull();
    // Authorization sí viaja.
    assertThat(capturado[0].getRequest().getHeaders().getFirst("Authorization"))
        .isEqualTo("Bearer token");
  }
}
