package pe.hotel.identity.auth;

/**
 * ETag de un recurso con {@code version}: la cabecera es {@code "<version>"} y el cliente la
 * devuelve en {@code If-Match} (docs/especificacion/03-contrato-comun.md § Transporte, notación y
 * tipos, {@code V}).
 */
public final class ETag {

  private ETag() {}

  public static String de(long version) {
    return "\"" + version + "\"";
  }

  /**
   * Versión de un {@code If-Match}; admite comillas, {@code W/} y comillas dobles internas. Con
   * {@code null} o vacío devuelve -1, que ningún {@code version} real tiene, y por tanto no casa con
   * ninguna fila.
   */
  public static long versionDe(String ifMatch) {
    if (ifMatch == null) {
      return -1;
    }
    String valor = ifMatch.trim();
    if (valor.startsWith("W/")) {
      valor = valor.substring(2).trim();
    }
    if (valor.startsWith("\"") && valor.endsWith("\"") && valor.length() >= 2) {
      valor = valor.substring(1, valor.length() - 1);
    }
    try {
      return Long.parseLong(valor.trim());
    } catch (NumberFormatException noEsVersion) {
      return -1;
    }
  }
}