package pe.hotel.gateway.error;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Destino caído o timeout se traduce en 503 DEPENDENCY_UNAVAILABLE
 * (errors.md §Reporting y Gateway), sin retry ciego y sin caché.
 * No declara filtros de reintento ni de caché en ningún paso.
 */
@Component
@Order(-1)
public class GatewayExceptionHandler implements WebExceptionHandler {

  private final GatewayErrorWriter errors;

  public GatewayExceptionHandler(GatewayErrorWriter errors) {
    this.errors = errors;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }
    Throwable cause = ex;
    while (cause != null) {
      String name = cause.getClass().getName();
      if (name.contains("Timeout")
          || name.contains("ConnectException")
          || name.contains("PrematureClose")
          || name.contains("ServiceUnavailable")
          || name.contains("NotFoundException")
          || name.contains("UnavailableException")
          || name.contains("NameResolution")
          || name.contains("UnknownHost")) {
        return errors.dependencyUnavailable(exchange);
      }
      cause = cause.getCause();
    }
    if (ex instanceof ResponseStatusException rse) {
      HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
      if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) {
        return errors.dependencyUnavailable(exchange);
      }
    }
    return Mono.error(ex);
  }
}
