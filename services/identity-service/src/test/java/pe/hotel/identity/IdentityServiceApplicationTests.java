package pe.hotel.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de contexto. No exige una base de datos viva: la validación de esquema (Flyway/JPA) corre
 * en ejecución real, no en esta prueba, porque la CI no levanta PostgreSQL.
 */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
      "spring.datasource.hikari.initialization-fail-timeout=-1"
    })
class IdentityServiceApplicationTests {

  @Test
  void contextLoads() {}
}
