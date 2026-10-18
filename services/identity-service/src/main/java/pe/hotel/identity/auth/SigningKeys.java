package pe.hotel.identity.auth;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.hotel.identity.config.IdentityProperties;

/**
 * Par RS256 del emisor: firma los tokens de persona y máquina y publica su parte pública en el
 * JWKS. La clave privada vive fuera de Git en el archivo que apunta
 * {@code hotel.identity.signing-key-file} (IDENTITY_SIGNING_KEY_FILE); el repositorio solo guarda
 * la ruta y el {@code kid}.
 *
 * <p>Si la ruta está vacía se genera una clave efímera en memoria, que solo se admite fuera de los
 * perfiles de producción: con dos instancias cada una firmaría con una clave distinta y los tokens
 * de la otra no validarían. En producción el arranque falla en vez de emitir tokens que nadie
 * valida.
 */
@Component
public class SigningKeys {

  private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

  private final IdentityProperties properties;
  private final Environment environment;

  private RSAPublicKey publicKey;
  private RSAPrivateCrtKey privateKey;
  private boolean efimera;

  public SigningKeys(IdentityProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void cargar() {
    String archivo =
        properties.getSigningKeyFile() == null ? "" : properties.getSigningKeyFile().trim();
    if (!archivo.isEmpty()) {
      cargarDeArchivo(archivo);
    } else if (esProduccion()) {
      throw new IllegalStateException(
          "IDENTITY_SIGNING_KEY_FILE es obligatoria fuera de dev/demo: la clave privada RS256 no"
              + " puede generarse en memoria con varias instancias");
    } else {
      generarEnMemoria();
    }
    log.info(
        "signing_key kid={} origen={} bits={}",
        properties.getJwkKid(),
        efimera ? "efimera-en-memoria" : "archivo",
        publicKey.getModulus().bitLength());
  }

  private void cargarDeArchivo(String archivo) {
    try {
      String pem = Files.readString(Path.of(archivo), StandardCharsets.UTF_8);
      byte[] der =
          Base64.getDecoder()
              .decode(
                  pem.replaceAll("-----BEGIN [^-]+-----", "")
                      .replaceAll("-----END [^-]+-----", "")
                      .replaceAll("\\s", ""));
      RSAPrivateCrtKey privada =
          (RSAPrivateCrtKey)
              KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
      this.privateKey = privada;
      this.publicKey =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));
    } catch (Exception e) {
      throw new IllegalStateException(
          "no se pudo leer la clave RS256 de IDENTITY_SIGNING_KEY_FILE (" + archivo + ")", e);
    }
  }

  private void generarEnMemoria() {
    try {
      KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
      generador.initialize(2048);
      KeyPair par = generador.generateKeyPair();
      this.privateKey = (RSAPrivateCrtKey) par.getPrivate();
      this.publicKey = (RSAPublicKey) par.getPublic();
    } catch (Exception e) {
      throw new IllegalStateException("no se pudo generar el par RS256 efímero", e);
    }
    efimera = true;
    log.warn(
        "signing_key_efimera kid={} aviso=sin_IDENTITY_SIGNING_KEY_FILE_solo_valido_en_dev_y_demo",
        properties.getJwkKid());
  }

  private boolean esProduccion() {
    for (String perfil : environment.getActiveProfiles()) {
      if (perfil != null && perfil.startsWith("prod")) {
        return true;
      }
    }
    return false;
  }

  public RSAPublicKey publicKey() {
    return publicKey;
  }

  public RSAPrivateCrtKey privateKey() {
    return privateKey;
  }

  public String kid() {
    return properties.getJwkKid();
  }

  /** True si la clave se generó en memoria: ningún archivo fuera de Git la contiene. */
  public boolean efimera() {
    return efimera;
  }

  /**
   * JWKS estándar {@code {keys:[...]}}: solo material público. La clave privada nunca sale de aquí
   * (docs/contracts/openapi/identity.yaml § /.well-known/jwks.json).
   */
  public Map<String, Object> jwks() {
    return Map.of(
        "keys",
        List.of(
            Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", kid(),
                "n", base64Url(publicKey.getModulus()),
                "e", base64Url(publicKey.getPublicExponent()))));
  }

  /** Entero sin signo en base64url sin relleno, como exige el JWKS (JWK Base64urlUInt). */
  static String base64Url(BigInteger valor) {
    byte[] bytes = valor.toByteArray();
    int inicio = 0;
    while (inicio < bytes.length - 1 && bytes[inicio] == 0) {
      inicio++;
    }
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Arrays.copyOfRange(bytes, inicio, bytes.length));
  }
}