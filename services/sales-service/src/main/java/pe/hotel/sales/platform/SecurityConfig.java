package pe.hotel.sales.platform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de sales-service: resource server JWT RS256 con validación explícita de {@code iss},
 * {@code aud} y {@code exp}, denegación por defecto y autorización desde el JWT validado.
 *
 * <p>Rutas {@code /api/**}: solo token humano con rol (ADMIN/EMPLEADO/CLIENTE); el alcance de sede
 * y propiedad se valida en el servicio sobre la estadía o la venta. Rutas {@code /internal/**}:
 * solo token máquina con el scope {@code cierres:write} que emite identity para reception.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** Audiencia de los tokens humanos. */
  public static final String HUMAN_AUDIENCE = "hotel-api";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Feign entrante de reception: solo máquina y scope cierres:write.
                    .requestMatchers("/internal/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(
                                esMaquinaConScope(authentication.get(), "cierres:write")))
                    // API de negocio: siempre token humano con rol; nunca un token máquina.
                    .requestMatchers("/api/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(esHumano(authentication.get())))
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  private boolean esMaquina(Authentication current) {
    return current != null
        && current.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.startsWith("SCOPE_"));
  }

  private boolean esMaquinaConScope(Authentication current, String scope) {
    if (!esMaquina(current)) {
      return false;
    }
    String buscado = "SCOPE_" + scope;
    return current.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(buscado::equals);
  }

  private boolean esHumano(Authentication current) {
    if (current == null || esMaquina(current)) {
      return false;
    }
    return current.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(
            a ->
                a.equals("ROLE_ADMIN") || a.equals("ROLE_EMPLEADO") || a.equals("ROLE_CLIENTE"));
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          List<GrantedAuthority> authorities = new ArrayList<>();
          String scope = jwt.getClaimAsString("scope");
          if (scope != null) {
            for (String value : scope.split(" ")) {
              if (!value.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
              }
            }
          }
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            for (String role : roles) {
              authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
          }
          return authorities;
        });
    return converter;
  }

  /**
   * Valida {@code exp}/{@code nbf} y el {@code issuer} fijo (sin descubrimiento OIDC) y la
   * {@code audience}: token humano ({@code hotel-api}), destino corto emitido por identity
   * ({@code sales}) o nombre del servicio (compatibilidad aditiva).
   */
  @Bean
  OAuth2TokenValidator<Jwt> hotelJwtValidator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.application.name}") String serviceName) {
    String destino =
        serviceName.endsWith("-service")
            ? serviceName.substring(0, serviceName.length() - "-service".length())
            : serviceName;
    Set<String> audiences = new HashSet<>();
    audiences.add(HUMAN_AUDIENCE);
    audiences.add(serviceName);
    audiences.add(destino);
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      OAuth2TokenValidator<Jwt> hotelJwtValidator) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(hotelJwtValidator);
    return decoder;
  }
}
