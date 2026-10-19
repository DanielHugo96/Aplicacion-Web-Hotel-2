package pe.hotel.identity.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba de integración contra PostgreSQL real (contenedor Docker en localhost:55432).
 *
 * <p>Reproduce el defecto reportado: {@code GET /api/persona/listar} y {@code
 * GET /api/persona/buscar/{id}} devolvían 500 con {@code NullPointerException} cuando se ejecutan
 * contra la BD real. Este test llama al servicio y al repositorio para que el NPE, si existe, haga
 * fallar la build y quede constancia del stack trace.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.datasource.hikari.initialization-fail-timeout=30",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,"
          + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "hotel.messaging.enabled=false"
    })
@ActiveProfiles("dev")
class PersonaRepositoryIntegrationTest {

  @Autowired PersonaRepository personas;

  @Autowired PersonaService personasService;

  @Test
  @Transactional
  void listarSinFiltrosNoLanzaNpeContraPostgresReal() {
    // El NPE reportado ocurría en el mapper de listar() contra PostgreSQL real.
    assertThatCode(() -> personas.listar(null, null, 0, 20))
        .doesNotThrowAnyException();
    List<Persona> filas = personas.listar(null, null, 0, 20);
    assertThat(filas).isNotNull();
    assertThat(filas).isNotEmpty();
    filas.forEach(
        p -> {
          assertThat(p.id()).isPositive();
          assertThat(p.tipoDocumento()).isNotNull();
          assertThat(p.documento()).isNotNull();
          assertThat(p.nombre()).isNotNull();
          assertThat(p.apellido()).isNotNull();
          assertThat(p.correo()).isNotNull();
          assertThat(p.codigoRol()).isNotNull();
          assertThat(p.tipoPersona()).isNotNull();
          assertThat(p.sedes()).isNotNull();
        });
  }

  @Test
  @Transactional
  void buscarPorIdExistenteNoLanzaNpeContraPostgresReal() {
    assertThatCode(() -> personas.buscarPorId(1)).doesNotThrowAnyException();
    Persona p = personas.buscarPorId(1).orElseThrow();
    assertThat(p.id()).isEqualTo(1);
    assertThat(p.tipoPersona()).isEqualTo("Administrador");
    assertThat(p.sedes()).isNotNull();
  }

  @Test
  @Transactional
  void buscarPorIdInexistenteDevuelveVacíoSinNpe() {
    assertThatCode(() -> personas.buscarPorId(999999))
        .doesNotThrowAnyException();
    assertThat(personas.buscarPorId(999999)).isEmpty();
  }

  @Test
  @Transactional
  void servicioListarSinFiltrosNoLanzaNpe() {
    // Llama al servicio completo (incluye auditoría PERSONA_SEARCH).
    assertThatCode(() -> personasService.listar(0, 20, null, null, "ADMIN", List.of()))
        .doesNotThrowAnyException();
    PersonaService.PersonaList lista =
        personasService.listar(0, 20, null, null, "ADMIN", List.of());
    assertThat(lista.personas()).isNotNull();
    assertThat(lista.personas()).isNotEmpty();
  }

  @Test
  @Transactional
  void servicioBuscarAdminNoLanzaNpe() {
    assertThatCode(() -> personasService.buscar(1, null, "ADMIN", 1))
        .doesNotThrowAnyException();
    Persona p = personasService.buscar(1, null, "ADMIN", 1);
    assertThat(p.id()).isEqualTo(1);
  }

  @Test
  @Transactional
  void auditoriaSearchNoLanzaNpe() {
    String uuid = java.util.UUID.randomUUID().toString();
    assertThatCode(() -> personasService.auditarSearch("1", null, uuid))
        .doesNotThrowAnyException();
  }

  @Test
  @Transactional
  void auditoriaReadNoLanzaNpe() {
    String uuid = java.util.UUID.randomUUID().toString();
    assertThatCode(() -> personasService.auditarRead("1", 100, null, uuid))
        .doesNotThrowAnyException();
  }
}
