package pe.hotel.notification.worker;

/**
 * Privacidad operativa: los logs y la auditoría nunca imprimen el destinatario completo ni el
 * contenido privado (docs/especificacion/03-contrato-comun.md § Privacidad y retención y
 * microservicios/notification/base-de-datos.md § Validación).
 */
public final class Privacy {

  private Privacy() {}

  /** {@code cliente.uno@hotel.test} → {@code c***@hotel.test}; sin correo, solo la longitud. */
  public static String maskEmail(String correo) {
    if (correo == null || correo.isBlank()) {
      return "(vacío)";
    }
    int arroba = correo.indexOf('@');
    if (arroba <= 0) {
      return "***";
    }
    return correo.charAt(0) + "***" + correo.substring(arroba);
  }
}