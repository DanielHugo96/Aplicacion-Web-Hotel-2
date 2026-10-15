package pe.hotel.gateway.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import pe.hotel.gateway.error.GatewayErrorWriter;

/**
 * Exige JWT válido en rutas privadas; las públicas documentadas pasan sin token.
 * Firma RS256, issuer y audience fijos, exp, contra el jwk-set-uri explícito.
 * Sin token o con token inválido/expirado: 401 UNAUTHENTICATED con envelope.
 * El preflight OPTIONS de origen permitido no exige JWT.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  private final GatewayErrorWriter errors;

  public GatewaySecurityConfig(GatewayErrorWriter errors) {
    this.errors = errors;
  }

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> {
        })
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // Actuator vive en el puerto de gestión interno: por el 8080 no hay
            // ruta y el Gateway responde 404 (paso 12).
            .pathMatchers("/actuator/**").permitAll()
            .pathMatchers(HttpMethod.GET, "/api/public/**").permitAll()
            .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro", "/api/auth/social")
            .permitAll()
            .pathMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
            .anyExchange().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> {
            })
            .authenticationEntryPoint(
                (exchange, ex) -> errors.unauthenticated(exchange))
            .accessDeniedHandler(
                (exchange, denied) -> errors.write(exchange, HttpStatus.FORBIDDEN,
                    "FORBIDDEN", "No tienes permiso para esta acción.")))
        .build();
  }

  /**
   * Valida algoritmo RS256, issuer, audience y exp contra el JWKS explícito.
   * La primera descarga del JWKS ocurre en la primera validación, no en el
   * arranque: el Gateway arranca aunque identity aún no responda.
   */
  @Bean
  ReactiveJwtDecoder jwtDecoder(GatewayProperties properties) {
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
        .withJwkSetUri(properties.getJwt().getJwkSetUri())
        .build();
    OAuth2TokenValidator<Jwt> algorithm = token -> {
      Object alg = token.getHeaders().get("alg");
      if ("RS256".equals(alg)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Algoritmo no permitido", null));
    };
    OAuth2TokenValidator<Jwt> issuer =
        new JwtIssuerValidator(properties.getJwt().getIssuer());
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>("aud",
            aud -> aud != null && aud.contains(properties.getJwt().getAudience()));
    OAuth2TokenValidator<Jwt> timestamps = new JwtTimestampValidator();
    decoder.setJwtValidator(token -> {
      for (OAuth2TokenValidator<Jwt> validator : List.of(algorithm, issuer, audience, timestamps)) {
        OAuth2TokenValidatorResult result = validator.validate(token);
        if (result.hasErrors()) {
          return result;
        }
      }
      return OAuth2TokenValidatorResult.success();
    });
    return decoder;
  }
}
