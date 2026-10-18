package pe.hotel.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Reglas de credencial: normalización, límites en bytes y verificación con BCrypt. */
class ClavesTests {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  @Test
  void elCorreoSeNormalizaAMinusculasYSinEspacios() {
    assertThat(Claves.normalizarCorreo("  Cliente.Uno@Hotel.Test ")).isEqualTo("cliente.uno@hotel.test");
    assertThat(Claves.normalizarCorreo(null)).isNull();
  }

  @Test
  void laPoliticaDeClaveMideBytesUtf8NoCaracteres() {
    assertThat(Claves.cumplePolitica("123456789012")).isTrue();
    assertThat(Claves.cumplePolitica("12345678901")).isFalse();
    assertThat(Claves.cumplePolitica("ñ".repeat(24)))
        .as("24 caracteres ñ son 48 bytes: dentro del límite")
        .isTrue();
    assertThat(Claves.cumplePolitica("ñ".repeat(37)))
        .as("37 caracteres ñ son 74 bytes: fuera del límite")
        .isFalse();
    assertThat(Claves.cumplePolitica("a".repeat(72))).isTrue();
    assertThat(Claves.cumplePolitica("a".repeat(73))).isFalse();
  }

  @Test
  void coincideVerificaConBcryptYNuncaTrunca() {
    String hash = encoder.encode("clave-de-prueba-1234");

    assertThat(Claves.coincide(encoder, "clave-de-prueba-1234", hash)).isTrue();
    assertThat(Claves.coincide(encoder, "otra-clave", hash)).isFalse();
    assertThat(Claves.coincide(encoder, null, hash)).isFalse();
    assertThat(Claves.coincide(encoder, "clave-de-prueba-1234", null)).isFalse();
    assertThat(Claves.coincide(encoder, "clave-de-prueba-1234", "no-es-un-hash")).isFalse();

    // Una clave de más de 72 bytes nunca llega al encoder: no se trunca en silencio.
    String larga = "a".repeat(73);
    assertThat(Claves.bytesUtf8(larga)).isGreaterThan(Claves.MAX_BYTES);
    assertThat(Claves.coincide(encoder, larga, hash)).isFalse();
  }

  @Test
  void elCorreoSeValidaPorFormatoYLongitud() {
    assertThat(Claves.correoValido("cliente.uno@hotel.test")).isTrue();
    assertThat(Claves.correoValido("sin-arroba")).isFalse();
    assertThat(Claves.correoValido("a@b")).isFalse();
    assertThat(Claves.correoValido("doble@arroba@hotel.test")).isFalse();
    assertThat(Claves.correoValido("con espacio@hotel.test")).isFalse();
    assertThat(Claves.correoValido("a".repeat(250) + "@hotel.test"))
        .as("el límite de correo es 254")
        .isFalse();
    assertThat(Claves.correoValido("x".repeat(64) + "@hotel.test")).isTrue();
  }

  @Test
  void elHashOcupaMenosDeLoQueOcupaLaClaveYNoLaContiene() {
    String clave = "clave-de-prueba-1234";
    String hash = encoder.encode(clave);
    assertThat(hash).startsWith("$2").doesNotContain(clave);
    assertThat(hash.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
  }
}