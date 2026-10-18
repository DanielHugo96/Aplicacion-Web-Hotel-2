package pe.hotel.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.hotel.identity.persona.DatosPersona;

/** ETag y formato de datos de persona: reglas puras, sin base de datos. */
class ETagYDatosTests {

  @Test
  void elEtagSeEmiteEntreComillasYSeLeeConComillasOWGuionDebil() {
    assertThat(ETag.de(3L)).isEqualTo("\"3\"");
    assertThat(ETag.versionDe("\"3\"")).isEqualTo(3);
    assertThat(ETag.versionDe("3")).isEqualTo(3);
    assertThat(ETag.versionDe("W/\"3\"")).isEqualTo(3);
  }

  @Test
  void unIfMatchQueNoEsVersionNoCasaConNingunaFila() {
    assertThat(ETag.versionDe(null)).isEqualTo(-1);
    assertThat(ETag.versionDe("")).isEqualTo(-1);
    assertThat(ETag.versionDe("\"abc\"")).isEqualTo(-1);
  }

  @Test
  void elDocumentoDependeDelTipo() {
    assertThat(DatosPersona.normalizarTipoDocumento("dni")).isEqualTo("DNI");
    assertThat(DatosPersona.tipoDocumentoValido("PASAPORTE")).isTrue();
    assertThat(DatosPersona.tipoDocumentoValido("NIT")).isFalse();

    assertThat(DatosPersona.documentoValido("DNI", "90000100")).isTrue();
    assertThat(DatosPersona.documentoValido("DNI", "9000010")).as("DNI de 8 dígitos").isFalse();
    assertThat(DatosPersona.documentoValido("DNI", "900001000")).isFalse();
    assertThat(DatosPersona.documentoValido("CE", "AB123456")).isTrue();
    assertThat(DatosPersona.documentoValido("CE", "AB12")).as("mínimo 6").isFalse();
    assertThat(DatosPersona.documentoValido("PASAPORTE", "AB12CD3456")).isTrue();
    assertThat(DatosPersona.documentoValido("PASAPORTE", "AB-123456")).as("solo alfanuméricos").isFalse();
  }

  @Test
  void nombreYApellidoNoPuedenVenirVaciosNiExcederElLimite() {
    assertThat(DatosPersona.textoValido("Cliente", 100)).isTrue();
    assertThat(DatosPersona.textoValido("   ", 100)).isFalse();
    assertThat(DatosPersona.textoValido(null, 100)).isFalse();
    assertThat(DatosPersona.textoValido("a".repeat(101), 100)).isFalse();
  }

  @Test
  void elNombreCanonicoDelRolVieneDelCodigoInmutable() {
    assertThat(pe.hotel.identity.persona.Persona.tipoPersonaDe("ADMIN")).isEqualTo("Administrador");
    assertThat(pe.hotel.identity.persona.Persona.tipoPersonaDe("EMPLEADO")).isEqualTo("Empleado");
    assertThat(pe.hotel.identity.persona.Persona.tipoPersonaDe("CLIENTE")).isEqualTo("Cliente");
  }
}