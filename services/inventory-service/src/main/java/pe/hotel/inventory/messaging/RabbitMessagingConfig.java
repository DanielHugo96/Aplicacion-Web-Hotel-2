package pe.hotel.inventory.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de la plataforma (docs/especificacion/05-mensajeria.md § Reintentos y DLQ
 * Rabbit). Declara los exchanges durables {@code hotel.commands.v1} y {@code hotel.dlx.v1}, las
 * colas principales de este servicio con sus tres reintentos con TTL y su DLQ. Declarar es
 * idempotente; cada servicio declara las colas que produce o consume.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMessagingConfig {

  private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

  public static final String COMMANDS_EXCHANGE = "hotel.commands.v1";
  public static final String DLX_EXCHANGE = "hotel.dlx.v1";

  public static final List<String> QUEUES = List.of("stock.restituir");

  @Bean
  public OutboxPublisher rabbitOutboxPublisher(RabbitTemplate rabbitTemplate) {
    return new RabbitOutboxPublisher(rabbitTemplate);
  }

  @Bean
  public Declarables hotelMessagingTopology() {
    return topology(QUEUES);
  }

  /** Declara la topología al arranque; si el broker está caído no bloquea (readiness degradado). */
  @Bean
  public ApplicationRunner hotelTopologyDeclarer(AmqpAdmin amqpAdmin) {
    return args -> {
      try {
        amqpAdmin.initialize();
      } catch (Exception ex) {
        log.warn("rabbit_topology_declare_error error={}", ex.getMessage());
      }
    };
  }

  static Declarables topology(List<String> queues) {
    List<Declarable> declarables = new ArrayList<>();
    declarables.add(new DirectExchange(COMMANDS_EXCHANGE, true, false));
    declarables.add(new DirectExchange(DLX_EXCHANGE, true, false));
    for (String queue : queues) {
      Map<String, Object> mainArgs = new HashMap<>();
      mainArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
      mainArgs.put("x-dead-letter-routing-key", queue + ".dlq");
      declarables.add(QueueBuilder.durable(queue).withArguments(mainArgs).build());
      declarables.add(
          new Binding(queue, Binding.DestinationType.QUEUE, COMMANDS_EXCHANGE, queue, null));
      declarables.addAll(retryQueues(queue));
      declarables.add(QueueBuilder.durable(queue + ".dlq").build());
      declarables.add(
          new Binding(queue + ".dlq", Binding.DestinationType.QUEUE, DLX_EXCHANGE, queue + ".dlq", null));
    }
    return new Declarables(declarables.toArray(new Declarable[0]));
  }

  private static List<Declarable> retryQueues(String queue) {
    Map<Integer, String> retries = new LinkedHashMap<>();
    retries.put(5000, "5s");
    retries.put(30000, "30s");
    retries.put(120000, "120s");
    List<Declarable> declarables = new ArrayList<>();
    for (Map.Entry<Integer, String> retry : retries.entrySet()) {
      Map<String, Object> args = new HashMap<>();
      args.put("x-message-ttl", retry.getKey());
      args.put("x-dead-letter-exchange", "");
      args.put("x-dead-letter-routing-key", queue);
      declarables.add(
          QueueBuilder.durable(queue + ".retry." + retry.getValue()).withArguments(args).build());
    }
    return declarables;
  }
}
