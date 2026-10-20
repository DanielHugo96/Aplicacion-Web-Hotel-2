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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

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
      MessagingProperties properties) {
    String group = properties.getMessaging().getKafka().getConsumerGroup();
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + "." + group + ".dlt", record.partition()));
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
