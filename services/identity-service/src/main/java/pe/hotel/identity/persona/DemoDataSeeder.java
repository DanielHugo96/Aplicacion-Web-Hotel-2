package pe.hotel.identity.persona;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.identity.config.DemoProperties;
import pe.hotel.identity.messaging.Destination;
import pe.hotel.identity.messaging.OutboxStore;

/**
 * Seeds del perfil dev: catálogo fijo de roles, personas demo con sus sedes y clientes de servicio.
 *
 * <p>Corre en Java y no en SQL porque el hash BCrypt se calcula fuera de la base
 * ({@code clave_hash} ya BCrypt desde Java,
 * docs/especificacion/microservicios/identity/base-de-datos.md § Procedimientos y funciones) y
 * porque la semilla de credenciales depende de {@code DEMO_PASSWORD} del entorno: sin ella no se
 * siembran usuarios.
 *
 * <p>Es idempotente por construcción: solo {@code INSERT ... ON CONFLICT DO NOTHING}, así que
 * reejecutarlo no duplica filas ni restablece claves o permisos ya modificados
 * (§ Seeds). Los datos son ficticios y no hay secretos en el repositorio: entran por entorno.
 *
 * <p>El ajuste de la secuencia de {@code persona} tras los ids explícitos va en la migración V4, no
 * aquí: el usuario runtime no tiene permiso para {@code setval} sobre las secuencias.
 */
@Component
@Profile({"dev", "demo"})
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  /** Roles del catálogo fijo: id estable, código inmutable y etiqueta canónica. */
  private static final List<Object[]> ROLES =
      List.of(
          new Object[] {1, "ADMIN", "Administrador"},
          new Object[] {2, "EMPLEADO", "Empleado"},
          new Object[] {3, "CLIENTE", "Cliente"});

  /** Personas demo: id, correo, id_tipo_persona, nombre, apellido, documento y sedes. */
  private static final List<PersonaSeed> PERSONAS =
      List.of(
          new PersonaSeed(1, "admin@hotel.test", 1, "Demo", "Admin", "90000001", List.of()),
          new PersonaSeed(2, "recepcion.lima@hotel.test", 2, "Demo", "Empleado Lima", "90000002", List.of(1)),
          new PersonaSeed(
              3, "recepcion.miraflores@hotel.test", 2, "Demo", "Empleado Miraflores", "90000003", List.of(2)),
          new PersonaSeed(
              4, "recepcion.arequipa@hotel.test", 2, "Demo", "Empleado Arequipa", "90000004", List.of(3)),
          new PersonaSeed(100, "cliente.uno@hotel.test", 3, "Cliente", "Uno", "90000100", List.of()),
          new PersonaSeed(101, "cliente.dos@hotel.test", 3, "Cliente", "Dos", "90000101", List.of()));

  /**
   * Clientes de servicio con la allowlist de audiencia y scopes del contrato común
   * (docs/especificacion/03-contrato-comun.md § Servicio a servicio). Cada uno con su propio secreto
   * del entorno: no hay client secret de reporting ni de notification porque no hacen Feign.
   */
  private static final List<ClientSeed> CLIENTES =
      List.of(
          new ClientSeed("identity", "identity-service", Map.of("hotel", List.of("hoteles:read"))),
          new ClientSeed(
              "reception",
              "reception-service",
              Map.of(
                  "identity", List.of("clientes:read"),
                  "hotel", List.of("habitaciones:read"),
                  "sales", List.of("cierres:write"))),
          new ClientSeed("hotel", "hotel-service", Map.of("reception", List.of("bloqueos:write"))),
          new ClientSeed("inventory", "inventory-service", Map.of("hotel", List.of("hoteles:read"))),
          new ClientSeed(
              "sales",
              "sales-service",
              Map.of(
                  "reception", List.of("recepciones:read", "operaciones:write"),
                  "inventory", List.of("stock:read", "stock:debit"))));

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final DemoProperties demo;
  private final ObjectMapper mapper = new ObjectMapper();
  private final ObjectProvider<OutboxStore> outboxProvider;

  public DemoDataSeeder(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      DemoProperties demo,
      ObjectProvider<OutboxStore> outboxProvider) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
    this.demo = demo;
    this.outboxProvider = outboxProvider;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // El catálogo fijo de roles no depende de credenciales, así que va siempre.
    sembrarRoles();
    if (demo.getPassword() == null || demo.getPassword().isBlank()) {
      log.info("seed_demo personas_omitidas motivo=demo_password_ausente");
    } else {
      // Personas y sedes van juntas: `persona_sede.id_persona` tiene FK a `persona`, así que sembrar
      // sedes sin personas rompe el arranque por FK y la transacción de `run()` no llega a confirmar.
      sembrarPersonas();
      sembrarSedes();
      // Snapshot de bootstrap en outbox por persona (hecho global, idHotel nulo).
      encolarSnapshotsBootstrap();
    }
    // Los clientes de servicio no dependen de personas y sí tienen su propio secreto por entorno.
    sembrarClientesDeServicio();
  }

  private void sembrarRoles() {
    int[] inserted = new int[ROLES.size()];
    for (int i = 0; i < ROLES.size(); i++) {
      Object[] rol = ROLES.get(i);
      inserted[i] =
          jdbcTemplate.update(
              """
              INSERT INTO tipo_persona (id, codigo, descripcion, estado, version)
              VALUES (?, ?, ?, true, 1)
              ON CONFLICT DO NOTHING
              """,
              rol);
    }
    log.info("seed_demo roles declarados={} nuevos={}", ROLES.size(), sum(inserted));
  }

  private void sembrarPersonas() {
    String hash = passwordEncoder.encode(demo.getPassword());
    int nuevas = 0;
    for (PersonaSeed persona : PERSONAS) {
      nuevas +=
          jdbcTemplate.update(
              """
              INSERT INTO persona (id, tipo_documento, documento, nombre, apellido, correo,
                                   clave_hash, foto_url, id_tipo_persona, estado, version,
                                   created_at, updated_at)
              VALUES (?, 'DNI', ?, ?, ?, ?, ?, NULL, ?, true, 1, now(), now())
              ON CONFLICT DO NOTHING
              """,
              persona.id(),
              persona.documento(),
              persona.nombre(),
              persona.apellido(),
              persona.correo(),
              hash,
              persona.idTipoPersona());
    }
    log.info("seed_demo personas declaradas={} nuevas={}", PERSONAS.size(), nuevas);
  }

  private void sembrarSedes() {
    int nuevas = 0;
    for (PersonaSeed persona : PERSONAS) {
      for (Integer sede : persona.sedes()) {
        nuevas +=
            jdbcTemplate.update(
                """
                INSERT INTO persona_sede (id_persona, id_hotel) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """,
                persona.id(),
                sede);
      }
    }
    log.info("seed_demo sedes nuevas={}", nuevas);
  }

  private void sembrarClientesDeServicio() {
    int nuevos = 0;
    for (ClientSeed cliente : CLIENTES) {
      String secreto = demo.secretOf(cliente.nombre());
      if (secreto == null || secreto.isBlank()) {
        log.warn("seed_demo cliente_omitido client_id={} motivo=secreto_ausente", cliente.clientId());
        continue;
      }
      nuevos +=
          jdbcTemplate.update(
              """
              INSERT INTO service_client (client_id, secret_hash, audiencias_scopes, estado)
              VALUES (?, ?, CAST(? AS jsonb), true)
              ON CONFLICT (client_id) DO NOTHING
              """,
              cliente.clientId(),
              passwordEncoder.encode(secreto),
              json(cliente.audienciasScopes()));
    }
    log.info("seed_demo clientes_servicio declarados={} nuevos={}", CLIENTES.size(), nuevos);
  }

  /**
   * Encola un {@code PersonaSnapshot} de bootstrap por cada persona demo (solo las que se
   * insertaron realmente, no las que ya existían por {@code ON CONFLICT DO NOTHING}).
   */
  private void encolarSnapshotsBootstrap() {
    if (outboxProvider == null) {
      return;
    }
    OutboxStore outbox = outboxProvider.getIfAvailable();
    if (outbox == null) {
      return;
    }
    for (PersonaSeed persona : PERSONAS) {
      String json;
      try {
        json =
            mapper.writeValueAsString(
                Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "type", "PersonaSnapshot",
                    "schemaVersion", 1,
                    "aggregateType", "persona",
                    "aggregateId", String.valueOf(persona.id()),
                    "aggregateVersion", 1,
                    "idHotel", null,
                    "occurredAt", Instant.now().toString(),
                    "correlationId", UUID.randomUUID().toString(),
                    "payload",
                    Map.of(
                        "idPersona", persona.id(),
                        "nombre", persona.nombre(),
                        "apellido", persona.apellido(),
                        "idTipoPersona", persona.idTipoPersona(),
                        "estado", true,
                        "version", 1)));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("no se pudo serializar el snapshot de bootstrap", e);
      }
      outbox.enqueue(
          Destination.KAFKA,
          "identity.persona.v1",
          String.valueOf(persona.id()),
          String.valueOf(persona.id()),
          1,
          "PersonaSnapshot",
          json);
    }
    log.info("seed_demo snapshots_bootstrap personas={}", PERSONAS.size());
  }

  private String json(Map<String, List<String>> audienciasScopes) {
    try {
      return mapper.writeValueAsString(audienciasScopes);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("no se pudo serializar audiencias_scopes", e);
    }
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }

  private record PersonaSeed(
      int id,
      String correo,
      int idTipoPersona,
      String nombre,
      String apellido,
      String documento,
      List<Integer> sedes) {}

  private record ClientSeed(
      String nombre, String clientId, Map<String, List<String>> audienciasScopes) {}
}