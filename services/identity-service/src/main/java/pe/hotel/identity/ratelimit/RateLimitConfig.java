package pe.hotel.identity.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.hotel.identity.config.IdentityProperties;

/**
 * Registra el rate limit de las rutas públicas de identity. Corre después de la seguridad (que deja
 * pasar login y registro) y antes del filtro de idempotencia: una petición rechazada por límite no
 * crea ni completa ninguna operación durable.
 */
@Configuration
public class RateLimitConfig {

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      RateLimiter limiter, IdentityProperties properties) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(
            new RateLimitFilter(
                limiter,
                properties.getLoginRateLimitPerMinute(),
                properties.getRegistroRateLimitPerMinute()));
    registration.setOrder(-60);
    registration.addUrlPatterns(RateLimitFilter.LOGIN, RateLimitFilter.REGISTRO);
    return registration;
  }
}