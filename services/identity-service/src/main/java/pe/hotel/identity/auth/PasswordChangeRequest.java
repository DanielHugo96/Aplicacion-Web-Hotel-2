package pe.hotel.identity.auth;

/**
 * Cuerpo de {@code PUT /api/auth/password}. Ambas claves son write-only: se verifican, no se
 * devuelven, no se registran y nunca se guardan en claro ni en el payload de idempotencia.
 * Ver docs/contracts/openapi/identity.yaml § PasswordChange.
 */
public record PasswordChangeRequest(String claveActual, String claveNueva) {}