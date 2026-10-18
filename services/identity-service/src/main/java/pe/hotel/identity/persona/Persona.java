package pe.hotel.identity.persona;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fila de {@code persona} con su rol canónico y sus sedes autorizadas. El hash de la clave viaja en
 * la entidad porque la verificación es en Java (BCrypt) y nunca en SQL, pero ningún DTO de salida
 * lo expone: la capa de respuesta lo descarta (docs/especificacion/microservicios/identity/api.md
 * § REST).
 *
 * @param tipoPersona nombre canónico derivado del código inmutable del rol, no de la etiqueta
 *     editable (Administrador, Empleado o Cliente)
 */
public record Persona(
    int id,
    String tipoDocumento,
    String documento,
    String nombre,
    String apellido,
    String correo,
    String claveHash,
    String fotoUrl,
    int idTipoPersona,
    String codigoRol,
    String tipoPersona,
    boolean estado,
    long version,
    OffsetDateTime createdAt,
    List<Integer> sedes) {

  /** Nombre canónico del rol a partir de su código inmutable. */
  public static String tipoPersonaDe(String codigo) {
    return switch (codigo) {
      case "ADMIN" -> "Administrador";
      case "EMPLEADO" -> "Empleado";
      case "CLIENTE" -> "Cliente";
      default -> codigo;
    };
  }

  public Persona conSedes(List<Integer> nuevasSedes) {
    return new Persona(
        id,
        tipoDocumento,
        documento,
        nombre,
        apellido,
        correo,
        claveHash,
        fotoUrl,
        idTipoPersona,
        codigoRol,
        tipoPersona,
        estado,
        version,
        createdAt,
        List.copyOf(nuevasSedes));
  }
}