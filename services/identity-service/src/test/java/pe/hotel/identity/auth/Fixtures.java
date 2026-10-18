package pe.hotel.identity.auth;

import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.hotel.identity.persona.Persona;

/** Personas y clientes de servicio de prueba, con hashes BCrypt reales. */
final class Fixtures {

  /** Clave en claro de todas las personas de prueba; solo vive en el código de test. */
  static final String CLAVE = "clave-de-prueba-1234";

  static final String CLIENTE_ID = "reception-service";
  static final String SECRETO_CLIENTE = "secreto-de-cliente-de-prueba";

  private Fixtures() {}

  static String hash(PasswordEncoder encoder, String clave) {
    return encoder.encode(clave);
  }

  static Persona persona(int id, String correo, String codigoRol, List<Integer> sedes, String hash) {
    return new Persona(
        id,
        "DNI",
        "9000%04d".formatted(id),
        "Nombre",
        "Apellido",
        correo,
        hash,
        null,
        switch (codigoRol) {
          case "ADMIN" -> 1;
          case "EMPLEADO" -> 2;
          default -> 3;
        },
        codigoRol,
        Persona.tipoPersonaDe(codigoRol),
        true,
        1L,
        java.time.OffsetDateTime.parse("2026-10-01T10:00:00Z"),
        sedes);
  }

  static ServiceClient cliente(String clientId, String secretHash, Map<String, List<String>> scopes) {
    return new ServiceClient(clientId, secretHash, scopes, true);
  }
}