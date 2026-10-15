package pe.hotel.discovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Protege el registro de Eureka con HTTP Basic y las credenciales propias
 * ({@code EUREKA_USERNAME}/{@code EUREKA_PASSWORD}).
 * Sin credenciales -> 401; con credenciales -> registra.
 * Salud interna sin auth para los healthchecks de Compose.
 */
@Configuration
public class EurekaSecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**").permitAll()
            .requestMatchers("/eureka/**").authenticated()
            .anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
