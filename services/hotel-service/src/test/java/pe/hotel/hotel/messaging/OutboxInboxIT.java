package pe.hotel.hotel.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Evidencia de mecanismo de la plataforma: las tablas técnicas {@code outbox} e {@code inbox} con la
 * misma transacción local. La mutación deja una fila de outbox pendiente ({@code published_at} nulo) y
 * el consumidor deduplica por {@code (consumer, message_id)}.
 *
 * <p>Prueba de integración con PostgreSQL real; la CI no la ejecuta (surefire solo corre
 * {@code *Test}/{@code *Tests}). Se lanza a mano contra el Compose con las credenciales del runtime:
 *
 * <pre>
 *   IT_DATASOURCE_URL=jdbc:postgresql://localhost:55432/hotel_db \
 *   IT_DATASOURCE_USER=hotel_app IT_DATASOURCE_PASSWORD=*** \
 *   ./mvnw -o -f services/pom.xml -pl hotel-service test -Dtest=OutboxInboxIT \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class OutboxInboxIT {

  private static final String URL =
      System.getenv().getOrDefault("IT_DATASOURCE_URL", "jdbc:postgresql://localhost:55432/hotel_db");
  private static final String USER = System.getenv().getOrDefault("IT_DATASOURCE_USER", "hotel_app");
  private static final String PASSWORD = System.getenv().getOrDefault("IT_DATASOURCE_PASSWORD", "");

  private final JdbcTemplate jdbc = new JdbcTemplate(dataSource());
  private final InboxStore inbox = new InboxStore(jdbc);
  private final OutboxStore outbox = new OutboxStore(jdbc);

  private final List<UUID> inboxRows = new ArrayList<>();
  private final List<String> outboxAggregates = new ArrayList<>();

  static DriverManagerDataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(URL);
    dataSource.setUsername(USER);
    dataSource.setPassword(PASSWORD);
    return dataSource;
  }

  @Test
  void inboxDeduplicaElMismoMensajePorConsumidor() {
    UUID messageId = UUID.randomUUID();
    inboxRows.add(messageId);

    assertThat(inbox.claim("hotel-ocupacion-v1", messageId)).isTrue();
    assertThat(inbox.claim("hotel-ocupacion-v1", messageId))
        .as("segunda entrega del mismo mensaje al mismo consumidor se descarta")
        .isFalse();
    assertThat(inbox.claim("reception-gates-v1", messageId))
        .as("otro grupo de consumidor es una fila distinta")
        .isTrue();

    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM inbox WHERE consumer = ? AND message_id = ?",
            Integer.class,
            "hotel-ocupacion-v1",
            messageId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void mutacionDejaFilaDeOutboxPendiente() {
    String aggregateId = "it-" + UUID.randomUUID();
    outboxAggregates.add(aggregateId);

    outbox.enqueue(
        Destination.KAFKA,
        "hotel.habitacion.v1",
        aggregateId,
        aggregateId,
        4,
        "HabitacionLista",
        "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"HabitacionLista\"}");

    Integer pending =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND published_at IS NULL",
            Integer.class,
            aggregateId);
    assertThat(pending).isEqualTo(1);
  }

  @AfterEach
  void cleanUp() {
    for (UUID id : inboxRows) {
      jdbc.update("DELETE FROM inbox WHERE message_id = ?", id);
    }
    for (String aggregateId : outboxAggregates) {
      jdbc.update("DELETE FROM outbox WHERE aggregate_id = ?", aggregateId);
    }
  }
}
