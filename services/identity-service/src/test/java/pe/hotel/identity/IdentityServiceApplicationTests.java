package pe.hotel.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de contexto. No exige una base de datos viva: la validación de esquema (Flyway/JPA) corre
 * en ejecución real, no en esta prueba, porque la CI no levanta PostgreSQL.
 *
 * <p>El perfil es {@code test} y no el {@code dev} que trae {@code application.yml}: en dev corren
 * los seeds de demo, que sí necesitan base de datos.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
          + ",org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "spring.datasource.hikari.initialization-fail-timeout=-1",
      "hotel.messaging.enabled=false"
    })
@ActiveProfiles("test")
class IdentityServiceApplicationTests {

  @Test
  void contextLoads() {}
}
