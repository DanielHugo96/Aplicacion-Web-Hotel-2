package pe.hotel.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt es el único hasher de credenciales del servicio: claves de personas y secretos de cliente
 * de servicio. Nunca se compara ni se consulta una clave en claro en SQL
 * (docs/especificacion/03-contrato-comun.md § Identidad y sedes,
 * docs/especificacion/microservicios/identity/base-de-datos.md § Procedimientos y funciones).
 */
@Configuration
public class PasswordConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}