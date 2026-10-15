package pe.hotel.identity.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responde {@code 401 UNAUTHENTICATED} con el envelope común cuando falta el token o es inválido
 * (firma, issuer, audience o expiración). Es el caso `SEC06` de la matriz.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException {
    // Log estructurado con `service` y `correlationId` (del MDC); nunca el token.
    log.warn("authentication rejected: {} {}", request.getMethod(), request.getRequestURI());
    ResponseWriter.writeError(response, ErrorCode.UNAUTHENTICATED);
  }
}
