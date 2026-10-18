package pe.hotel.identity.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import pe.hotel.identity.config.IdentityProperties;
import pe.hotel.identity.persona.Persona;
import pe.hotel.identity.platform.SecurityConfig;

/**
 * Emisor de tokens RS256.
 *
 * <p>Token de persona: {@code iss=hotel-identity}, {@code aud=hotel-api}, TTL 15 min y los claims que
 * fija el contrato común ({@code sub}=idPersona, {@code tipoPersona} canónico, {@code roles} con el
 * código inmutable del rol, {@code sedes}, {@code iat}, {@code exp} y {@code jti}).
 *
 * <p>Token máquina: TTL 5 min, {@code sub}=nombre del servicio, {@code aud}=destino y {@code scope}
 * como string separado por espacios, limitado por la audiencia.
 *
 * <p>Ver docs/especificacion/03-contrato-comun.md § Identidad y sedes y § Servicio a servicio.
 */
@Component
public class TokenIssuer {

  private final JwtEncoder encoder;
  private final IdentityProperties properties;
  private final String issuer;

  public TokenIssuer(SigningKeys keys, IdentityProperties properties, @Value(
      "${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
    RSAKey rsaJwk =
        new RSAKey.Builder(keys.publicKey())
            .privateKey(keys.privateKey())
            .keyID(keys.kid())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
            .build();
    JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaJwk));
    this.encoder = new NimbusJwtEncoder(source);
    this.properties = properties;
    this.issuer = issuer;
  }

  /** TTL del token de persona en segundos (900 por defecto). */
  public long ttlPersonaSegundos() {
    return Duration.ofMinutes(properties.getTokenTtlMinutes()).toSeconds();
  }

  /** TTL del token máquina en segundos (300 por defecto). */
  public long ttlMaquinaSegundos() {
    return Duration.ofMinutes(properties.getMachineTokenTtlMinutes()).toSeconds();
  }

  /** JWT de persona con las sedes y el rol que la base declara, nunca lo que dice el cliente. */
  public String emitirPersona(Persona persona) {
    Instant ahora = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(SecurityConfig.HUMAN_AUDIENCE))
            .subject(String.valueOf(persona.id()))
            .issuedAt(ahora)
            .expiresAt(ahora.plusSeconds(ttlPersonaSegundos()))
            .id(UUID.randomUUID().toString())
            .claim("tipoPersona", persona.tipoPersona())
            .claim("roles", List.of(persona.codigoRol()))
            .claim("sedes", persona.sedes())
            .build();
    return firmar(claims);
  }

  /** JWT de servicio: sin roles humanos y con el scope ya limitado por la audiencia. */
  public String emitirMaquina(String servicio, String audiencia, String scope) {
    Instant ahora = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audiencia))
            .subject(servicio)
            .issuedAt(ahora)
            .expiresAt(ahora.plusSeconds(ttlMaquinaSegundos()))
            .id(UUID.randomUUID().toString())
            .claim("scope", scope)
            .build();
    return firmar(claims);
  }

  private String firmar(JwtClaimsSet claims) {
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}