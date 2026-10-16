package pe.hotel.hotel.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Evidencia de mecanismo de la plataforma (Kafka): un consumidor de prueba que falla siempre agota
 * los cinco reintentos del {@code DefaultErrorHandler} que cablea {@link KafkaMessagingConfig} y el
 * mensaje termina en {@code <topic>.<consumerGroup>.dlt} conservando key y payload.
 *
 * <p>Prueba de integración con brokers reales: no la ejecuta la CI (surefire solo corre {@code *Test}
 * / {@code *Tests}); se lanza a mano contra el Compose:
 *
 * <pre>
 *   ./mvnw -o -f services/pom.xml -pl hotel-service test -Dtest=KafkaDltIT \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Los intervalos de reintento se acortan en la corrida para no esperar 456 s; la secuencia real de
 * la spec (1 s, 5 s, 30 s, 120 s y 300 s) la fija {@link ListBackOff} y la verifica el reenvío real
 * desde la DLT del consumidor dueño.
 */
@SpringBootTest(
    classes = KafkaDltIT.TestApp.class,
    properties = {
      "hotel.messaging.enabled=true",
      "hotel.messaging.kafka.consumer-group=hotel-ocupacion-v1",
      // Acortado en la corrida: la secuencia de la spec la fija ListBackOff.
      "hotel.messaging.kafka.retry-intervals-ms=100,100,100,100,100"
    })
class KafkaDltIT {

  private static final String BOOTSTRAP = "localhost:29092";
  private static final String TOPIC = "hotel.habitacion.v1";
  private static final String GROUP = "hotel-ocupacion-v1";
  private static final String DLT = TOPIC + "." + GROUP + ".dlt";

  @Test
  void consumoFallidoAgotaReintentosYVaALaDlt() throws Exception {
    String key = "101";
    String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"HabitacionLista\"}";

    producer().send(TOPIC, key, payload).get();

    assertThat(readFromDlt(key))
        .as("el mensaje agotado aparece en " + DLT + " con el payload original")
        .isEqualTo(payload);
    assertThat(DltListener.ATTEMPTS.get())
        .as("intento inicial + 5 reintentos = 6 intentos antes de la DLT")
        .isEqualTo(6);
  }

  private KafkaTemplate<String, String> producer() {
    Map<String, Object> props =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  private String readFromDlt(String key) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "hotel-dlt-reader-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(DLT));
      long deadline = System.currentTimeMillis() + 30_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
        for (ConsumerRecord<String, String> record : records) {
          if (key.equals(record.key())) {
            return record.value();
          }
        }
      }
    }
    return null;
  }

  @SpringBootConfiguration
  @EnableConfigurationProperties(MessagingProperties.class)
  @Import(KafkaMessagingConfig.class)
  static class TestApp {

    @Bean
    ConsumerFactory<String, String> consumerFactory() {
      Map<String, Object> props =
          Map.of(
              ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP,
              ConsumerConfig.GROUP_ID_CONFIG, GROUP,
              ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
              ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
              ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate() {
      Map<String, Object> props =
          Map.of(
              ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP,
              ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
              ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
              ProducerConfig.ACKS_CONFIG, "all");
      return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    DltListener dltListener() {
      return new DltListener();
    }
  }

  /** Consumidor de prueba que siempre falla para forzar el agotamiento de reintentos. */
  static class DltListener {

    static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @KafkaListener(topics = TOPIC, containerFactory = "kafkaListenerContainerFactory")
    void onMessage(String message) {
      ATTEMPTS.incrementAndGet();
      throw new IllegalStateException("fallo simulado permanente del consumidor de prueba");
    }
  }
}
