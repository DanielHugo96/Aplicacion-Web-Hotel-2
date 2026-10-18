package pe.hotel.identity.auth;

import java.util.List;

/**
 * Forma legacy de la respuesta de login: {@code expiresIn} fijo en segundos y sin refresh token
 * (docs/especificacion/microservicios/identity/api.md § Respuestas data).
 */
public record AuthResponse(
    int idPersona,
    String nombre,
    String apellido,
    String correo,
    String tipoPersona,
    String token,
    int expiresIn,
    List<Integer> sedes) {

  public static AuthResponse de(pe.hotel.identity.persona.Persona persona, String token, int expiresIn) {
    return new AuthResponse(
        persona.id(),
        persona.nombre(),
        persona.apellido(),
        persona.correo(),
        persona.tipoPersona(),
        token,
        expiresIn,
        persona.sedes());
  }
}