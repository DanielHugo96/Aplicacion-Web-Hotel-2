package pe.hotel.identity.auth;

/**
 * Cuerpo de {@code POST /api/auth/registro}: cuerpo JSON cerrado con
 * {@code tipoDocumento,documento,nombre,apellido,correo,clave}, todos requeridos.
 *
 * <p>El rol no viaja: el registro público fuerza CLIENTE, y por eso {@code idTipoPersona},
 * {@code roles}, {@code sedes} y {@code estado} no son propiedades de este DTO. Si el cliente los
 * envía, el JSON cerrado los rechaza con 400 {@code VALIDATION_ERROR} y no hay elevación
 * (docs/contracts/openapi/identity.yaml § Registro).
 */
public record RegistroRequest(
    String tipoDocumento, String documento, String nombre, String apellido, String correo, String clave) {}