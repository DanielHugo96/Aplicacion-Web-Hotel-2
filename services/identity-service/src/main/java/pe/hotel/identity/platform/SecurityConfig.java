package pe.hotel.identity.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pe.hotel.identity.auth.MachineTokenBasicAuthenticationFilter;
import pe.hotel.identity.auth.SigningKeys;

/**
 * Convención de seguridad común: resource server JWT RS256 con validación explícita de
 * {@code iss}, {@code aud} y {@code exp}, denegación por defecto y autorización desde el JWT
 * validado. La identidad nunca se lee de cabeceras falsificables: solo del JWT.
 * Ver docs/especificacion/03-contrato-comun.md § Identidad y sedes y § Servicio a servicio.
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
      RestAccessDeniedHandler accessDeniedHandler,
      MachineTokenBasicAuthenticationFilter machineTokenBasicFilter)
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
                    // Rutas públicas del contrato (docs/contracts/openapi/identity.yaml):
                    // login, registro y social no llevan token; el JWKS es público.
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro",
                            "/api/auth/social")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json")
                    .permitAll()
                    // Token máquina: lo autentica su propio filtro Basic, que corre antes de esta
                    // cadena y responde 401 si la credencial de servicio no vale.
                    .requestMatchers(HttpMethod.POST, "/internal/auth/token")
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
                    // Denegación por defecto: todo lo demás exige autenticación. GET/PUT
                    // /api/auth/me y PUT /api/auth/password caen aquí y solo aceptan JWT humano.
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(authenticationEntryPoint))
        // La emisión del token máquina se autentica con Basic de cliente de servicio, antes de que
        // el filtro de token intente interpretar esa cabecera como un JWT.
        .addFilterBefore(machineTokenBasicFilter, UsernamePasswordAuthenticationFilter.class)
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
   * {@code audience}: un token humano ({@code hotel-api}) o un token máquina dirigido a este
   * servicio. Un token con aud incorrecta se rechaza con 401 (caso `SEC06`).
   */
  @Bean
  OAuth2TokenValidator<Jwt> hotelJwtValidator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.application.name}") String serviceName) {
    Set<String> audiences = Set.of(HUMAN_AUDIENCE, serviceName);
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
  }

  /**
   * Valida contra el JWKS explícito (nunca con descubrimiento OIDC) y exige {@code exp}, issuer y
   * audiencia.
   *
   * <p>identity es el emisor de estos tokens: cuando tiene su propia clave de firma (en el propio
   * servicio y en las pruebas) valida contra la clave pública local, sin un salto por la red. Sin
   * clave local cae al {@code jwk-set-uri} declarado.
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      ObjectProvider<SigningKeys> signingKeys,
      OAuth2TokenValidator<Jwt> hotelJwtValidator) {
    SigningKeys keys = signingKeys.getIfAvailable();
    NimbusJwtDecoder decoder =
        keys == null
            ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
            : NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();
    decoder.setJwtValidator(hotelJwtValidator);
    return decoder;
  }
}