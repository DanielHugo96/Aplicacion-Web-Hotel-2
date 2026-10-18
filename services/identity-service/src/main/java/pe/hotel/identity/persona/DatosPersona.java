package pe.hotel.identity.persona;

import java.util.Locale;

/**
 * Reglas de formato de una persona que no cambian entre rutas: tipo de documento del catálogo,
 * documento según el tipo y longitudes de los textos.
 * Ver docs/especificacion/microservicios/identity/base-de-datos.md § Validación.
 */
public final class DatosPersona {

  public static final String DNI = "DNI";
  public static final String PASAPORTE = "PASAPORTE";
  public static final String CE = "CE";

  public static final int MAX_NOMBRE = 100;
  public static final int MAX_DOCUMENTO = 20;

  private DatosPersona() {}

  /** Tipo de documento del catálogo {@code [DNI, PASAPORTE, CE]}, sin distinguir mayúsculas. */
  public static String normalizarTipoDocumento(String valor) {
    return valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
  }

  public static boolean tipoDocumentoValido(String tipo) {
    return DNI.equals(tipo) || PASAPORTE.equals(tipo) || CE.equals(tipo);
  }

  /**
   * DNI de 8 dígitos; PASAPORTE y CE de 6 a 20 caracteres alfanuméricos. Es el mismo criterio que
   * los CHECK de la tabla, comprobado antes de escribir.
   */
  public static boolean documentoValido(String tipo, String documento) {
    if (tipo == null || documento == null) {
      return false;
    }
    String valor = documento.trim();
    if (valor.length() > MAX_DOCUMENTO) {
      return false;
    }
    if (DNI.equals(tipo)) {
      return valor.matches("[0-9]{8}");
    }
    return valor.matches("[A-Za-z0-9]{6,20}");
  }

  /** Nombre o apellido presente, sin_longitud excesiva. */
  public static boolean textoValido(String valor, int maximo) {
    return valor != null && !valor.isBlank() && valor.trim().length() <= maximo;
  }
}