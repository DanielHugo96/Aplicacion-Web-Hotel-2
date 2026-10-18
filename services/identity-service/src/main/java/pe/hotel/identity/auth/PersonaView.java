package pe.hotel.identity.auth;

import java.time.OffsetDateTime;
import java.util.List;
import pe.hotel.identity.persona.Persona;

/**
 * Persona pública. Nunca lleva {@code clave} ni su hash, ni siquiera a null
 * (docs/especificacion/microservicios/identity/api.md: "Ninguna API devuelve clave ni claveHash, ni
 * con null").
 */
public record PersonaView(
    int idPersona,
    String tipoDocumento,
    String documento,
    String nombre,
    String apellido,
    String correo,
    String fotoUrl,
    int idTipoPersona,
    String tipoPersona,
    List<Integer> sedes,
    boolean estado,
    OffsetDateTime fechaCreacion,
    long version) {

  public static PersonaView de(Persona persona) {
    return new PersonaView(
        persona.id(),
        persona.tipoDocumento(),
        persona.documento(),
        persona.nombre(),
        persona.apellido(),
        persona.correo(),
        persona.fotoUrl(),
        persona.idTipoPersona(),
        persona.tipoPersona(),
        persona.sedes(),
        persona.estado(),
        persona.createdAt(),
        persona.version());
  }
}