package pe.hotel.identity.auth;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registra el filtro que autentica con Basic la emisión del token máquina. Corre antes de la cadena
 * de Spring Security (orden -100), así que {@code /internal/auth/token} llega al controlador con la
 * identidad de servicio ya puesta y con 401 escrito si la credencial no vale.
 */
@Configuration
public class MachineTokenSecurityConfig {

  @Bean
  FilterRegistrationBean<MachineTokenBasicAuthenticationFilter> machineTokenBasicFilter(
      ServiceClientRepository repository, PasswordEncoder passwordEncoder) {
    FilterRegistrationBean<MachineTokenBasicAuthenticationFilter> registration =
        new FilterRegistrationBean<>(
            new MachineTokenBasicAuthenticationFilter(repository, passwordEncoder));
    registration.setOrder(-110);
    registration.addUrlPatterns(MachineTokenBasicAuthenticationFilter.RUTA);
    return registration;
  }
}