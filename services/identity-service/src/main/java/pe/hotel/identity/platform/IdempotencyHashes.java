package pe.hotel.identity.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hashes de idempotencia. El canónico es
 * {@code método \n ruta \n query \n cuerpo \n If-Match} y se hashea con SHA-256, o con
 * HMAC-SHA256 cuando el request lleva credenciales: el valor guardado no debe permitir adivinar la
 * clave por fuerza bruta (docs/especificacion/03-contrato-comun.md § Headers).
 */
public final class IdempotencyHashes {

  private IdempotencyHashes() {}

  public static String sha256Hex(String valor) {
    try {
      return HexFormat.of().formatHex(sha256(valor));
    } catch (NoSuchAlgorithmException inalcanzable) {
      throw new IllegalStateException("SHA-256 no disponible", inalcanzable);
    }
  }

  public static String hmacSha256Hex(String secreto, String valor) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("no se pudo calcular el HMAC de idempotencia", e);
    }
  }

  private static byte[] sha256(String valor) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
  }
}