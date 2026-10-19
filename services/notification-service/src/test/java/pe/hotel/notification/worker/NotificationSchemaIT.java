package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Evidencia de esquema y procedimientos de {@code notification_db} contra PostgreSQL real:
 * CHECK de lease y de destinatario, unicidad de {@code message_id}, idempotencia de
 * {@code sp_notificacion_encolar}, lease de {@code fn_notificacion_tomar} y backoff de
 * {@code sp_notificacion_resultado}.
 *
 * <p>Prueba de integración: la CI no la ejecuta (surefire solo corre {@code *Test}/{@code *Tests}).
 * Se lanza a mano contra el Compose con las credenciales del runtime:
 *
 * <pre>
 *   IT_DATASOURCE_URL=jdbc:postgresql://localhost:55432/notification_db \
 *   IT_DATASOURCE_USER=notification_app IT_DATASOURCE_PASSWORD=*** \
 *   bash scripts/mvn.sh -f services/pom.xml -pl notification-service test \
 *     -Dtest=NotificationSchemaIT -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class NotificationSchemaIT {

  private static final String URL =
      System.getenv()
          .getOrDefault(
              "IT_DATASOURCE_URL", "jdbc:postgresql://localhost:55432/notification_db");
  private static final String USER =
      System.getenv().getOrDefault("IT_DATASOURCE_USER", "notification_app");
  private static final String PASSWORD = System.getenv().getOrDefault("IT_DATASOURCE_PASSWORD", "");

  private final DriverManagerDataSource dataSource = dataSource();
  private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
  private final TransactionTemplate tx =
      new TransactionTemplate(new DataSourceTransactionManager(dataSource));

  private final List<UUID> usados = new ArrayList<>();

  static DriverManagerDataSource dataSource() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(URL);
    ds.setUsername(USER);
    ds.setPassword(PASSWORD);
    return ds;
  }

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM notification_job");
    jdbc.update("DELETE FROM inbox WHERE consumer = 'notification-worker'");
    jdbc.update("DELETE FROM audit_log WHERE action LIKE 'NOTIFICATION_%'");
  }

  @AfterEach
  void limpiarDespues() {
    for (UUID id : usados) {
      jdbc.update("DELETE FROM notification_job WHERE command_id = ?", id);
      jdbc.update("DELETE FROM inbox WHERE message_id = ?", id);
    }
  }

  @Test
  void enviandoSinLeaseFallaPorCheck() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO notification_job (command_id, tipo, id_persona, destinatario,"
                        + " template_version, variables, payload_hash, estado, message_id)"
                        + " VALUES (?, 'BIENVENIDA', 100, 'cliente.uno@hotel.test', 1,"
                        + " '{\"nombre\":\"Cliente Uno\"}'::jsonb, repeat('a',64), 'ENVIANDO', ?)",
                    UUID.randomUUID(),
                    UUID.randomUUID() + "@hotel.test"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("notification_job");
  }

  @Test
  void pendienteSinDestinatarioFallaYEnviadaSinDestinatarioPasa() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO notification_job (command_id, tipo, id_persona, template_version,"
                        + " payload_hash, estado, message_id)"
                        + " VALUES (?, 'BIENVENIDA', 100, 1, repeat('b',64), 'PENDIENTE', ?)",
                    UUID.randomUUID(),
                    UUID.randomUUID() + "@hotel.test"))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID enviada = UUID.randomUUID();
    usados.add(enviada);
    jdbc.update(
        "INSERT INTO notification_job (command_id, tipo, id_persona, template_version,"
            + " payload_hash, estado, message_id, sent_at)"
            + " VALUES (?, 'BIENVENIDA', 100, 1, repeat('c',64), 'ENVIADA', ?, now())",
        enviada,
        enviada + "@hotel.test");

    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM notification_job WHERE command_id = ?", String.class, enviada))
        .isEqualTo("ENVIADA");
  }

  @Test
  void dosJobsConElMismoMessageIdFallaPorUnicidad() {
    UUID primero = UUID.randomUUID();
    String messageId = primero + "@hotel.test";
    encolar(primero, "BIENVENIDA", cuerpo(primero, null, "BIENVENIDA", payloadBienvenida()));

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_job WHERE message_id = ?", Integer.class, messageId);
    assertThat(filas).isEqualTo(1);

    UUID segundo = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO notification_job (command_id, tipo, id_persona, destinatario,"
                        + " template_version, variables, payload_hash, estado, message_id)"
                        + " VALUES (?, 'BIENVENIDA', 100, 'otro@hotel.test', 1, '{}'::jsonb,"
                        + " repeat('d',64), 'PENDIENTE', ?)",
                    segundo,
                    messageId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void encolarEsIdempotenteYRechazaHascoDistintoYPlantillaDesconocida() {
    UUID id = UUID.randomUUID();
    String cuerpo = cuerpo(id, null, "BIENVENIDA", payloadBienvenida());
    encolar(id, "BIENVENIDA", cuerpo);
    encolar(id, "BIENVENIDA", cuerpo);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_job WHERE command_id = ?", Integer.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inbox WHERE message_id = ?", Integer.class, id))
        .isEqualTo(1);

    assertThatThrownBy(
            () -> encolar(id, "BIENVENIDA", cuerpo.replace("Cliente Uno", "Cliente Otro")))
        .hasMessageContaining("distinto");

    UUID otro = UUID.randomUUID();
    assertThatThrownBy(
            () -> encolar(otro, "PROMOCION", cuerpo(otro, null, "NotificacionBienvenida", payloadBienvenida())))
        .hasMessageContaining("plantilla desconocida");
  }

  @Test
  void tomarAplicaLeaseYResultadoProgramaUnoCincoYTreintaMinutosHastaError() {
    UUID id = UUID.randomUUID();
    encolar(id, "BIENVENIDA", cuerpo(id, null, "BIENVENIDA", payloadBienvenida()));

    Map<String, Object> tomado = tomar();
    assertThat(tomado.get("estado")).isEqualTo("ENVIANDO");
    assertThat(tomado.get("attempts")).isEqualTo(1);
    UUID token = (UUID) tomado.get("lease_token");
    assertThat(token).isNotNull();
    assertThat(((java.sql.Timestamp) tomado.get("lease_until")).toInstant())
        .isAfter(java.time.Instant.now().plusSeconds(50));

    // Primer fallo: vuelve a PENDIENTE con la primera espera.
    resultado(id, token, false);
    assertThat(estado(id)).isEqualTo("PENDIENTE");
    assertThat(segundosHastaElProximoIntento(id)).isBetween(50L, 70L);

    // Segundo y tercer fallo: 5 y 30 minutos.
    for (long esperadoMin : new long[] {4, 29}) {
      jdbc.update("UPDATE notification_job SET next_attempt_at = now() WHERE command_id = ?", id);
      Map<String, Object> repetido = tomar();
      assertThat(repetido.get("command_id")).isEqualTo(id);
      resultado(id, (UUID) repetido.get("lease_token"), false);
      assertThat(estado(id)).isEqualTo("PENDIENTE");
      assertThat(minutosHastaElProximoIntento(id)).isBetween(esperadoMin, esperadoMin + 2);
    }

    // Cuarto intento agotado: ERROR.
    jdbc.update("UPDATE notification_job SET next_attempt_at = now() WHERE command_id = ?", id);
    Map<String, Object> ultimo = tomar();
    resultado(id, (UUID) ultimo.get("lease_token"), false);
    assertThat(estado(id)).isEqualTo("ERROR");
  }

  @Test
  void leaseVencidoSeRecuperaYLaRespuestaTardiaNoModificaElJob() {
    UUID id = UUID.randomUUID();
    encolar(id, "CHECKIN", cuerpo(id, 1, "NotificacionCheckin", payloadCheckin()));

    Map<String, Object> primero = tomar();
    UUID tokenViejo = (UUID) primero.get("lease_token");
    // El lease vence: otro worker lo retoma.
    jdbc.update(
        "UPDATE notification_job SET lease_until = now() - interval '1 minute' WHERE command_id = ?",
        id);
    Map<String, Object> segundo = tomar();
    UUID tokenNuevo = (UUID) segundo.get("lease_token");
    assertThat(tokenNuevo).isNotEqualTo(tokenViejo);
    assertThat((Integer) segundo.get("attempts")).isEqualTo(2);

    // La respuesta tardía del poseedor anterior no modifica nada.
    resultado(id, tokenViejo, true);
    assertThat(estado(id)).isEqualTo("ENVIANDO");
    assertThat(
            jdbc.queryForObject(
                "SELECT lease_token FROM notification_job WHERE command_id = ?", UUID.class, id))
        .isEqualTo(tokenNuevo);

    // El poseedor vigente sí decide el estado final.
    resultado(id, tokenNuevo, true);
    assertThat(estado(id)).isEqualTo("ENVIADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT sent_at FROM notification_job WHERE command_id = ?",
                java.time.OffsetDateTime.class,
                id))
        .isNotNull();
  }

  @Test
  void purgaAnonimizaSoloEnviadaAntiguaYNoTocaPendienteNiError() {
    UUID vieja = UUID.randomUUID();
    usados.add(vieja);
    jdbc.update(
        "INSERT INTO notification_job (command_id, tipo, id_persona, destinatario, template_version,"
            + " variables, payload_hash, estado, message_id, sent_at)"
            + " VALUES (?, 'BIENVENIDA', 100, 'cliente.uno@hotel.test', 1,"
            + " '{\"nombre\":\"Cliente Uno\"}'::jsonb, repeat('e',64), 'ENVIADA', ?,"
            + " now() - interval '40 days')",
        vieja,
        vieja + "@hotel.test");
    encolar(UUID.randomUUID(), "BIENVENIDA", cuerpo(UUID.randomUUID(), null, "BIENVENIDA", payloadBienvenida()));

    NotificationJobStore store = new NotificationJobStore(jdbc);
    int purgados = store.purgar(java.time.Instant.now().minus(java.time.Duration.ofDays(30)));

    assertThat(purgados).isEqualTo(1);
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT estado, payload_hash, destinatario, variables, sent_at FROM notification_job"
                + " WHERE command_id = ?",
            vieja);
    assertThat(fila.get("estado")).isEqualTo("ENVIADA");
    assertThat(fila.get("destinatario")).isNull();
    assertThat(fila.get("variables")).isNull();
    assertThat(String.valueOf(fila.get("payload_hash"))).hasSize(64);
    assertThat(fila.get("sent_at")).isNotNull();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_job WHERE estado = 'PENDIENTE'"
                    + " AND destinatario IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
  }

  // --- utilidades ---

  private void encolar(UUID id, String tipo, String cuerpo) {
    usados.add(id);
    tx.execute(
        status -> {
          jdbc.queryForObject(
              "SELECT set_config('hotel.notification.max_attempts', '3', true)", String.class);
          jdbc.update("CALL sp_notificacion_encolar(?::uuid, ?, ?::jsonb)", id.toString(), tipo, cuerpo);
          return null;
        });
  }

  private Map<String, Object> tomar() {
    return tx.execute(
        status -> {
          jdbc.queryForObject(
              "SELECT set_config('hotel.notification.lease_seconds', '60', true)", String.class);
          return jdbc.queryForMap(
              "SELECT command_id, estado, attempts, lease_token, lease_until"
                  + " FROM fn_notificacion_tomar(now())");
        });
  }

  private void resultado(UUID id, UUID token, boolean exito) {
    tx.execute(
        status -> {
          jdbc.queryForObject(
              "SELECT set_config('hotel.notification.max_attempts', '3', true)", String.class);
          jdbc.update(
              "CALL sp_notificacion_resultado(?::uuid, ?::uuid, ?, ?)",
              id.toString(),
              token.toString(),
              exito,
              exito ? null : "SMTP_ERROR");
          return null;
        });
  }

  private String estado(UUID id) {
    return jdbc.queryForObject(
        "SELECT estado FROM notification_job WHERE command_id = ?", String.class, id);
  }

  private long segundosHastaElProximoIntento(UUID id) {
    return jdbc.queryForObject(
        "SELECT extract(epoch FROM (next_attempt_at - now()))::bigint FROM notification_job"
            + " WHERE command_id = ?",
        Long.class,
        id);
  }

  private long minutosHastaElProximoIntento(UUID id) {
    return segundosHastaElProximoIntento(id) / 60;
  }

  private static String payloadBienvenida() {
    return "{\"idPersona\":100,\"destinatario\":\"cliente.uno@hotel.test\","
        + "\"nombre\":\"Cliente Uno\",\"templateVersion\":1}";
  }

  private static String payloadCheckin() {
    return "{\"idPersona\":100,\"idRecepcion\":1001,\"destinatario\":\"cliente.uno@hotel.test\","
        + "\"nombre\":\"Cliente Uno\",\"hotel\":\"Hotel Demo Lima Centro\","
        + "\"fechaSalida\":\"2026-10-05\",\"templateVersion\":1}";
  }

  private static String cuerpo(UUID commandId, Integer idHotel, String type, String payload) {
    return "{\"commandId\":\"" + commandId + "\",\"type\":\"" + type + "\",\"schemaVersion\":1,"
        + "\"idHotel\":" + (idHotel == null ? "null" : idHotel) + ",\"correlationId\":\""
        + UUID.randomUUID() + "\",\"issuedAt\":\"2026-10-04T14:00:00Z\",\"payload\":" + payload + "}";
  }
}