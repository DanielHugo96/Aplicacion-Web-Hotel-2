package pe.hotel.identity.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.hotel.identity.config.DemoProperties;

/**
 * El seeder de demo y su dependencia de {@code DEMO_PASSWORD}.
 *
 * <p>El caso que fija es el del arranque: sin credenciales no se siembran personas, pero tampoco
 * sedes. {@code persona_sede.id_persona} tiene clave foránea a {@code persona}, así que sembrar
 * sedes sin personas hace fallar la transacción del {@code ApplicationRunner} y el servicio no
 * levanta. Aquí se comprueba con el {@code JdbcTemplate} simulado, que es donde se ve qué filas
 * intenta escribir cada rama.
 */
class DemoDataSeederTests {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  private DemoDataSeeder seeder(DemoProperties demo) {
    return new DemoDataSeeder(jdbcTemplate, encoder, demo, null);
  }

  private DemoProperties demo(String clave, String secretoCliente) {
    DemoProperties demo = new DemoProperties();
    demo.setPassword(clave);
    if (secretoCliente != null) {
      demo.getServiceClientSecrets().put("reception", secretoCliente);
    }
    return demo;
  }

  private List<String> sqlEjecutado() {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), any(Object[].class));
    return sql.getAllValues();
  }

  @Test
  void sinDemoPasswordNoSiembraPersonasNiSedesYArranca() {
    DemoDataSeeder seeder = seeder(demo(null, "secreto-de-prueba"));

    assertThatCode(() -> seeder.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

    // Roles: siempre, no dependen de credenciales (los tres del catálogo fijo).
    verify(jdbcTemplate, times(3)).update(contains("INSERT INTO tipo_persona"), any(Object[].class));
    // Personas y sedes: ninguna de las dos, porque la segunda tiene FK a la primera.
    verify(jdbcTemplate, never()).update(contains("INSERT INTO persona (id,"), any(Object[].class));
    verify(jdbcTemplate, never()).update(contains("INSERT INTO persona_sede"), any(Object[].class));
    assertThat(sqlEjecutado())
        .noneMatch(s -> s.contains("persona_sede"))
        .noneMatch(s -> s.contains("INSERT INTO persona ("));
  }

  @Test
  void sinDemoPasswordTambienDejaLosClientesDeServicioFueraSiNoHaySecreto() {
    DemoDataSeeder seeder = seeder(demo("   ", null));

    assertThatCode(() -> seeder.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

    verify(jdbcTemplate, never()).update(contains("INSERT INTO service_client"), any(Object[].class));
  }

  @Test
  void conDemoPasswordSiembraPersonasYSusSedes() {
    DemoDataSeeder seeder = seeder(demo("clave-de-prueba-1234", "secreto-de-prueba"));

    assertThatCode(() -> seeder.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

    verify(jdbcTemplate, times(6))
        .update(contains("INSERT INTO persona (id,"), any(Object[].class));
    // Tres empleados con una sede cada uno (LLima=1, Miraflores=2, Arequipa=3).
    verify(jdbcTemplate, times(3))
        .update(contains("INSERT INTO persona_sede"), any(Object[].class));
    verify(jdbcTemplate).update(contains("INSERT INTO service_client"), any(Object[].class));
  }

  @Test
  void elHashDeLasPersonasEsBcryptYNoLaClaveEnClaro() {
    DemoDataSeeder seeder = seeder(demo("clave-de-prueba-1234", "secreto-de-prueba"));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<Object[]> argumentos = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(6))
        .update(contains("INSERT INTO persona (id,"), argumentos.capture());
    Object[] valores = argumentos.getAllValues().get(0);
    String hash = (String) valores[5];
    assertThat(hash).startsWith("$2").doesNotContain("clave-de-prueba-1234");
    assertThat(encoder.matches("clave-de-prueba-1234", hash)).isTrue();
  }

  /**
   * Los nombres semilla son los de la marca Casa Sillar
   * (docs/especificacion/microservicios/identity/base-de-datos.md § Seeds); id, correo, documento,
   * rol y sedes no cambian.
   */
  @Test
  void lasPersonasSembradasLlevanLaMarcaCasaSillar() {
    DemoDataSeeder seeder = seeder(demo("clave-de-prueba-1234", "secreto-de-prueba"));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<Object[]> argumentos = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(6))
        .update(contains("INSERT INTO persona (id,"), argumentos.capture());
    List<Object[]> filas = argumentos.getAllValues();

    // Columnas del INSERT: id, documento, nombre, apellido, correo, hash, idTipoPersona.
    assertThat(filas)
        .extracting(f -> f[2] + " " + f[3])
        .containsExactly(
            "Sillar Admin",
            "Sillar Recepción Lima",
            "Sillar Recepción Miraflores",
            "Sillar Recepción Arequipa",
            "Cliente Uno",
            "Cliente Dos");
    assertThat(filas).extracting(f -> f[0]).containsExactly(1, 2, 3, 4, 100, 101);
    assertThat(filas)
        .extracting(f -> f[4])
        .containsExactly(
            "admin@hotel.test",
            "recepcion.lima@hotel.test",
            "recepcion.miraflores@hotel.test",
            "recepcion.arequipa@hotel.test",
            "cliente.uno@hotel.test",
            "cliente.dos@hotel.test");
  }

  /**
   * Reiniciar sobre una base ya sembrada con el nombre anterior corrige las cuatro filas de personal
   * y solo ellas: el {@code UPDATE} lleva el valor semilla anterior en el {@code WHERE}, así que una
   * edición humana no coincide y no se pisa (IA-back-identity-presentacion-real-01).
   */
  @Test
  void renombraSoloLasFilasQueConservanElValorSemillaAnterior() {
    DemoDataSeeder seeder = seeder(demo("clave-de-prueba-1234", "secreto-de-prueba"));

    seeder.run(new DefaultApplicationArguments());

    ArgumentCaptor<Object[]> argumentos = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(4)).update(contains("UPDATE persona"), argumentos.capture());

    List<Object[]> renombrados = argumentos.getAllValues();
    assertThat(renombrados)
        .extracting(f -> f[0] + " " + f[1])
        .containsExactly(
            "Sillar Admin",
            "Sillar Recepción Lima",
            "Sillar Recepción Miraflores",
            "Sillar Recepción Arequipa");
    assertThat(renombrados).extracting(f -> f[2]).containsExactly(1, 2, 3, 4);
    // El WHERE reconoce la fila por el nombre y el apellido semilla anteriores.
    assertThat(renombrados).extracting(f -> f[3]).containsOnly("Demo");
    assertThat(renombrados)
        .extracting(f -> f[4])
        .containsExactly("Admin", "Empleado Lima", "Empleado Miraflores", "Empleado Arequipa");
    // Los clientes no llevaban el nombre anterior de la marca: no se renombran.
    assertThat(renombrados).extracting(f -> (Integer) f[2]).doesNotContain(100, 101);
  }
}