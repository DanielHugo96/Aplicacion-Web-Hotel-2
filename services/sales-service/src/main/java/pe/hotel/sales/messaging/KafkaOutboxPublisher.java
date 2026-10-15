package pe.hotel.sales.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Productor Kafka del outbox. Publica con {@code key=partitionKey} (aggregateId), headers
 * {@code content-type}, {@code schema-version} y {@code correlation-id}, y solo devuelve si el
 * broker confirma (acks=all + idempotencia configurados en {@code application.yml}).
 */
public class KafkaOutboxPublisher implements OutboxPublisher {

  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String consumerGroup;

  public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate, String consumerGroup) {
    this.kafkaTemplate = kafkaTemplate;
    this.consumerGroup = consumerGroup;
  }

  @Override
  public Destination destination() {
    return Destination.KAFKA;
  }

  @Override
  public void publish(OutboxMessage message) {
    await(kafkaTemplate.send(record(message.channel(), message)));
  }

  @Override
  public void publishToDeadLetter(OutboxMessage message, String reason) {
    await(kafkaTemplate.send(record(deadLetterTopic(message), message)));
  }

  private ProducerRecord<String, String> record(String topic, OutboxMessage message) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(topic, message.partitionKey(), message.payload());
    record.headers().add("content-type", "application/json".getBytes(StandardCharsets.UTF_8));
    record.headers().add("schema-version", "1".getBytes(StandardCharsets.UTF_8));
    record.headers().add("correlation-id", correlationId(message).getBytes(StandardCharsets.UTF_8));
    return record;
  }

  private String correlationId(OutboxMessage message) {
    try {
      return mapper.readTree(message.payload()).path("correlationId").asText("");
    } catch (Exception ex) {
      return "";
    }
  }

  private String deadLetterTopic(OutboxMessage message) {
    String suffix =
        consumerGroup == null || consumerGroup.isBlank() ? "dlt" : consumerGroup + ".dlt";
    return message.channel() + "." + suffix;
  }

  private void await(CompletableFuture<?> future) {
    try {
      future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("publicacion interrumpida", ex);
    } catch (ExecutionException | TimeoutException ex) {
      throw new IllegalStateException("el broker no confirmo la publicacion", ex);
    }
  }
}
