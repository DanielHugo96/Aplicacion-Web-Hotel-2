package pe.hotel.reception.platform;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error genéricos del catálogo único
 * (<a href="file:../../../../../../../docs/contracts/errors.md">docs/contracts/errors.md</a> § Genéricos).
 *
 * <p>Solo se declaran aquí los códigos comunes que aplica esta convención de plataforma. Los códigos
 * propios de un dominio (por ejemplo {@code DUPLICATE_RESOURCE}) los añade el plan del servicio que
 * los necesita; este enum no inventa códigos ni mensajes nuevos.
 */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Revisa los datos marcados."),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Tu sesión expiró. Vuelve a iniciar sesión."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "No encontramos lo que buscas."),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera un momento."),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "La operación ya se envió con otros datos."),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Los datos enviados son demasiado grandes."),
  PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, "Otra persona modificó este registro. Recarga para ver los cambios."),
  PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "Falta la versión del registro."),
  DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "El servicio no está disponible. Reintenta en unos segundos."),
  // Reception (docs/contracts/errors.md § Reception y § Hotel)
  ROOM_NOT_READY(HttpStatus.CONFLICT, "La habitación no admite ingreso todavía (limpieza o mantenimiento)."),
  ROOM_OCCUPIED(HttpStatus.CONFLICT, "La habitación ya está ocupada."),
  ROOM_NOT_SYNCED(HttpStatus.CONFLICT, "La habitación aún no está sincronizada con recepción."),
  GATE_NOT_AVAILABLE(HttpStatus.CONFLICT, "No se puede bloquear la habitación: está ocupada o en limpieza."),
  STAY_NOT_ACTIVE(HttpStatus.CONFLICT, "La estadía no está activa para esa operación."),
  OPERATIONS_IN_PROGRESS(HttpStatus.CONFLICT, "Hay consumos en proceso. Espera a que terminen."),
  ADVANCE_EXCEEDS_NEW_TOTAL(HttpStatus.CONFLICT, "La nueva fecha deja el alojamiento por debajo del adelanto registrado."),
  CLOSURE_IN_PROGRESS(HttpStatus.CONFLICT, "Ya hay un cierre en curso; no vuelvas a cobrar."),
  AMOUNT_MISMATCH(HttpStatus.CONFLICT, "El monto no coincide con el calculado. Revisa la nueva cotización."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");

  private final HttpStatus status;
  private final String uiMessage;

  ErrorCode(HttpStatus status, String uiMessage) {
    this.status = status;
    this.uiMessage = uiMessage;
  }

  public HttpStatus status() {
    return status;
  }

  /** Mensaje para UI del catálogo; la UI nunca lo redacta por su cuenta. */
  public String uiMessage() {
    return uiMessage;
  }
}
