package pe.hotel.reception.domain.dto;

/** Payload de GET /internal/clientes/{id} de identity (solo los campos que usa reception). */
public record ClienteVigente(
    Integer idPersona,
    String tipoDocumento,
    String documento,
    String nombre,
    String apellido,
    String correo,
    Boolean estado,
    Integer idTipoPersona,
    Long version) {}
