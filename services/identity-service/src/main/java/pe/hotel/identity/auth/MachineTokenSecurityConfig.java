package pe.hotel.identity.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Proporciona el filtro que autentica con Basic la emisión del token máquina. Se inyecta en la
 * cadena de seguridad (no se registra como filtro de servlet): dentro de la cadena corre después de
 * {@code SecurityContextHolderFilter}, así la identidad que deja puesta llega al controlador.
 */
@Configuration
public class MachineTokenSecurityConfig {

  @Bean
  MachineTokenBasicAuthenticationFilter machineTokenBasicFilter(
      ServiceClientRepository repository, PasswordEncoder passwordEncoder) {
    return new MachineTokenBasicAuthenticationFilter(repository, passwordEncoder);
  }
}