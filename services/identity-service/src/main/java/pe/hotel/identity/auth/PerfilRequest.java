package pe.hotel.identity.auth;

/**
 * Cuerpo de {@code PUT /api/auth/me}: solo nombre, apellido y fotoUrl. JSON cerrado, así que rol
 * ({@code idTipoPersona}), sedes, documento y estado no son propiedades y si se envían se rechazan
 * con 400 {@code VALIDATION_ERROR} en lugar de ignorarse
 * (docs/contracts/openapi/identity.yaml § Perfil).
 */
public record PerfilRequest(String nombre, String apellido, String fotoUrl) {}