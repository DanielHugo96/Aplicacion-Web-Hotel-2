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
    return new DemoDataSeeder(jdbcTemplate, encoder, demo);
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
}