package pe.hotel.hotel.platform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Convención de seguridad común: resource server JWT RS256 con validación explícita de
 * {@code iss}, {@code aud} y {@code exp}, denegación por defecto y autorización desde el JWT
 * validado. La identidad nunca se lee de cabeceras falsificables: solo del JWT.
 * Ver docs/especificacion/03-contrato-comun.md § Identidad y sedes y § Servicio a servicio.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Audiencia de los tokens humanos. Los tokens máquina usan el destino corto
   * ({@code hotel}, {@code identity}…), que identity emite desde la allowlist; también se acepta
   * el nombre largo del servicio por compatibilidad aditiva.
   */
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
                    // Salud: liveness y readiness no requieren token.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Catálogo público: solo lectura sin token.
                    .requestMatchers("/api/public/**")
                    .permitAll()
                    // Rutas internas: solo un token máquina (con scope). Un token humano sin scope da 403.
                    .requestMatchers("/internal/**")
                    .access(
                        (authentication, context) -> {
                          Authentication current = authentication.get();
                          boolean machine =
                              current != null
                                  && current.getAuthorities().stream()
                                      .map(GrantedAuthority::getAuthority)
                                      .anyMatch(authority -> authority.startsWith("SCOPE_"));
                          return new AuthorizationDecision(machine);
                        })
                    // Denegación por defecto: todo lo demás exige autenticación.
                    .anyRequest()
                    .authenticated())
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
   * {@code audience}: un token humano ({@code hotel-api}), el destino corto que emite identity
   * ({@code hotel}) o el nombre del servicio ({@code hotel-service}, compatibilidad aditiva). Un
   * token con aud incorrecta se rechaza con 401 (caso `SEC06`).
   */
  @Bean
  OAuth2TokenValidator<Jwt> hotelJwtValidator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.application.name}") String serviceName) {
    String destino =
        serviceName.endsWith("-service")
            ? serviceName.substring(0, serviceName.length() - "-service".length())
            : serviceName;
    Set<String> audiences = new HashSet<>(Set.of(HUMAN_AUDIENCE, serviceName));
    audiences.add(destino);
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
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