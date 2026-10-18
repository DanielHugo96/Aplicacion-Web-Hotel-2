package pe.hotel.identity.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta estándar de token máquina, sin envelope: {@code {access_token, token_type, expires_in}}
 * (docs/contracts/openapi/identity.yaml § TokenResponse).
 */
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn) {

  public static TokenResponse de(String token, long expiresInSegundos) {
    return new TokenResponse(token, "Bearer", expiresInSegundos);
  }
}