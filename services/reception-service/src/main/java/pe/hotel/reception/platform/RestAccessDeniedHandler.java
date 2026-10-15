package pe.hotel.reception.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responde {@code 403 FORBIDDEN} con el envelope común: rol o scope insuficientes. Un token humano
 * sin {@code scope} que llama a {@code /internal/**} cae aquí.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
      throws IOException {
    // Log estructurado con `service` y `correlationId` (del MDC); nunca el token.
    log.warn("access denied: {} {}", request.getMethod(), request.getRequestURI());
    ResponseWriter.writeError(response, ErrorCode.FORBIDDEN);
  }
}
