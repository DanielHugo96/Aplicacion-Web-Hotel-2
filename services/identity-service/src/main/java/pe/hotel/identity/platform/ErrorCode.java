package pe.hotel.identity.platform;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error del catálogo único
 * (<a href="file:../../../../../../../docs/contracts/errors.md">docs/contracts/errors.md</a>).
 *
 * <p>Los genéricos los declara la convención de plataforma; los de dominio ({@code
 * DUPLICATE_RESOURCE}) los añade el plan del servicio que los necesita. Este enum no inventa
 * códigos ni mensajes nuevos: cada uno copia su HTTP y su mensaje para UI del catálogo.
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
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado."),
  // Código propio de identity (docs/contracts/errors.md § Identity).
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Ese valor ya está registrado."),
  FIXED_ROLE_CATALOG(HttpStatus.CONFLICT, "Los roles del sistema no se crean ni eliminan."),
  STAFF_CREDENTIAL_REQUIRED(
      HttpStatus.CONFLICT, "Para crear personal, la persona necesita credenciales de acceso."),
  LAST_ADMIN_PROTECTED(
      HttpStatus.CONFLICT, "Debe quedar al menos un administrador activo."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."),
  PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8)."),
  CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta."),
  SOCIAL_LOGIN_DISABLED(HttpStatus.GONE, "El acceso con redes sociales ya no está disponible.");

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
