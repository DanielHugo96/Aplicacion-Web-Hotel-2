package pe.hotel.hotel.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Acepta el {@code X-Correlation-Id} del cliente (si es un UUID), genera uno cuando falta y lo
 * propaga en la respuesta y en el MDC. Corre antes que la cadena de seguridad para que las
 * respuestas {@code 401/403} también devuelvan la correlación.
 * Ver docs/especificacion/03-contrato-comun.md § Headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private final String serviceName;

  public CorrelationIdFilter(@Value("${spring.application.name:service}") String serviceName) {
    this.serviceName = serviceName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = CorrelationId.resolve(request.getHeader(CorrelationId.HEADER));
    MDC.put(CorrelationId.MDC_KEY, correlationId);
    MDC.put(CorrelationId.SERVICE_MDC_KEY, serviceName);
    response.setHeader(CorrelationId.HEADER, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
      MDC.remove(CorrelationId.SERVICE_MDC_KEY);
    }
  }
}
