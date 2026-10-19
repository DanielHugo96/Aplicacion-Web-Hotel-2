package pe.hotel.identity.persona;

/** Fila de {@code tipo_persona}: catálogo fijo ADMIN / EMPLEADO / CLIENTE. */
public record TipoPersona(
    int idTipoPersona,
    String descripcion,
    String codigo,
    boolean estado,
    long version) {}
