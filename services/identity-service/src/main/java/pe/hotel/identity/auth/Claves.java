package pe.hotel.identity.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Reglas de credencial del servicio, juntas en un sitio para que login, registro y cambio de clave
 * no se desincronicen.
 *
 * <p>La clave vive entre 12 y 72 bytes UTF-8, el límite de BCrypt. Nunca se trunca en silencio: una
 * clave más larga es un error, no una clave recortada
 * (docs/especificacion/03-contrato-comun.md § Límites de campos comunes).
 */
public final class Claves {

  /** 12–72 bytes UTF-8, el rango que acepta BCrypt. */
  public static final int MIN_BYTES = 12;

  public static final int MAX_BYTES = 72;

  /** Longitud máxima del correo (límite de columna y de DTO). */
  public static final int MAX_CORREO = 254;

  private Claves() {}

  /** Correo recortado y en minúsculas: es la clave del UNIQUE y la del login. */
  public static String normalizarCorreo(String correo) {
    return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
  }

  public static int bytesUtf8(String valor) {
    return valor == null ? 0 : valor.getBytes(StandardCharsets.UTF_8).length;
  }

  /** La clave cumple la política de 12–72 bytes UTF-8. */
  public static boolean cumplePolitica(String clave) {
    int bytes = bytesUtf8(clave);
    return bytes >= MIN_BYTES && bytes <= MAX_BYTES;
  }

  /**
   * Verificación con BCrypt en Java. Nunca se compara en SQL y nunca se recorta: si la clave excede
   * el límite de BCrypt no se llama al encoder y el resultado es directamente que no coincide.
   */
  public static boolean coincide(PasswordEncoder encoder, String clara, String hash) {
    if (clara == null || hash == null || clara.isEmpty() || hash.isEmpty()) {
      return false;
    }
    if (bytesUtf8(clara) > MAX_BYTES) {
      return false;
    }
    try {
      return encoder.matches(clara, hash);
    } catch (RuntimeException hashInvalido) {
      return false;
    }
  }

  /** Formato de correo sintácticamente válido y dentro del límite de 254 caracteres. */
  public static boolean correoValido(String correo) {
    if (correo == null) {
      return false;
    }
    String recortado = correo.trim();
    if (recortado.isEmpty() || recortado.length() > MAX_CORREO || recortado.contains(" ")) {
      return false;
    }
    int arroba = recortado.indexOf('@');
    return arroba > 0
        && arroba == recortado.lastIndexOf('@')
        && arroba < recortado.length() - 1
        && recortado.indexOf('.', arroba) > arroba + 1;
  }
}