package pe.hotel.identity.messaging;

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
 * Productor y consumidor Kafka de la plataforma. El consumidor reintenta hasta 5 veces con esperas
 * de 1s, 5s, 30s, 120s y 300s y deriva a {@code <topic>.<consumerGroup>.dlt} conservando envelope,
 * key, particion/offset y grupo.
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
  public OutboxPublisher kafkaOutboxPublisher(
      KafkaTemplate<String, String> kafkaTemplate, MessagingProperties properties) {
    return new KafkaOutboxPublisher(
        kafkaTemplate, properties.getMessaging().getKafka().getConsumerGroup());
  }

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
        new DefaultErrorHandler(recoverer, new ListBackOff(properties.getMessaging().getKafka().getRetryIntervalsMs()));
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
