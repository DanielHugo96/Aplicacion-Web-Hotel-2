package pe.hotel.reporting.messaging;

import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import pe.hotel.reporting.projection.ProjectionStore;

/**
 * Consumidor Kafka del read model. Reintenta hasta 5 veces con esperas de 1s, 5s, 30s, 120s y
 * 300s y deriva a {@code <topic>.<consumerGroup>.dlt} conservando envelope, key, partición/offset,
 * grupo y error no sensible (docs/especificacion/05-mensajeria.md).
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaMessagingConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory,
      KafkaTemplate<String, String> kafkaTemplate,
      MessagingProperties properties,
      ProjectionStore store) {
    String group = properties.getMessaging().getKafka().getConsumerGroup();
    DeadLetterPublishingRecoverer publisher =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + "." + group + ".dlt", record.partition()));
    // Ante DLT la proyección queda degradada: la UI muestra la advertencia hasta reparar.
    ConsumerRecordRecoverer recoverer =
        (record, ex) -> {
          try {
            store.markDegraded(record.topic(), record.partition());
          } catch (Exception ignored) {
            // Si la base tampoco responde, readiness lo refleja; el DLT no se pierde.
          }
          publisher.accept(record, ex);
        };
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer, new ListBackOff(properties.getMessaging().getKafka().getRetryIntervalsMs()));
    // Un payload que no valida contra su esquema es un error permanente: no se reintenta.
    errorHandler.addNotRetryableExceptions(MessageInvalidException.class);
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
