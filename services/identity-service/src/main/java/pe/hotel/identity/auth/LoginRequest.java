package pe.hotel.identity.auth;

/**
 * Cuerpo de {@code POST /api/auth/login}. JSON cerrado: correo y clave requeridos. La clave es
 * write-only, nunca se registra ni se persiste en claro.
 * Ver docs/contracts/openapi/identity.yaml § Login.
 */
public record LoginRequest(String correo, String clave) {}