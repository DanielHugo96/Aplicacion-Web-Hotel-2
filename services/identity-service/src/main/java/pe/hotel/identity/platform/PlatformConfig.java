package pe.hotel.identity.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cablea las piezas de plataforma que necesitan la base de datos (idempotencia).
 */
@Configuration
public class PlatformConfig {

  @Bean
  ApiIdempotencyRepository apiIdempotencyRepository(JdbcTemplate jdbcTemplate) {
    return new ApiIdempotencyRepository(jdbcTemplate);
  }

  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(
      ApiIdempotencyRepository repository, ObjectProvider<IdempotencyCustomizer> customizer) {
    FilterRegistrationBean<IdempotencyFilter> registration =
        new FilterRegistrationBean<>(new IdempotencyFilter(repository, customizer.getIfAvailable()));
    // Después de la cadena de seguridad (que llena el SecurityContext) y antes del controlador.
    registration.setOrder(-50);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
