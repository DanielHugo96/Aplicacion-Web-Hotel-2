package pe.hotel.inventory.platform;

import java.util.ArrayList;
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
import org.springframework.http.HttpMethod;

/**
 * Seguridad de inventory-service: resource server JWT RS256 con validación explícita de
 * {@code iss}, {@code aud} y {@code exp}, denegación por defecto y autorización desde el JWT
 * validado.
 *
 * <p>Rutas internas (/internal/**): solo token máquina (SCOPE_*). Las de lectura
 * ({@code stock:read}) y el débito ({@code stock:debit}) exigen el scope exacto; un token humano
 * nunca basta (caso {@code SEC05}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** Audiencia de los tokens humanos; los tokens máquina usan el nombre del servicio destino. */
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
                    // Salud: sin token.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Catálogo interno de sales: scope stock:read.
                    .requestMatchers(
                            HttpMethod.GET,
                            "/internal/productos",
                            "/internal/productos/{id}",
                            "/internal/stock/movimientos/{idVenta}")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(
                                esMaquinaConScope(authentication.get(), "stock:read")))
                    // Débito de stock: scope stock:debit.
                    .requestMatchers(HttpMethod.POST, "/internal/stock/descontar")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(
                                esMaquinaConScope(authentication.get(), "stock:debit")))
                    // Resto de /internal/** sin el scope exacto: 403.
                    .requestMatchers("/internal/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(esMaquina(authentication.get())))
                    // Denegación por defecto: todo lo demás exige autenticación humana.
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
   * {@code audience}: un token humano ({@code hotel-api}) o un token máquina dirigido a este
   * servicio. Un token con aud incorrecta se rechaza con 401 (caso {@code SEC06}).
   */
  @Bean
  OAuth2TokenValidator<Jwt> hotelJwtValidator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.application.name}") String serviceName) {
    Set<String> audiences = Set.of(HUMAN_AUDIENCE, serviceName);
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
