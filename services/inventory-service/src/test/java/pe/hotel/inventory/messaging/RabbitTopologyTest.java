package pe.hotel.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

class RabbitTopologyTest {

  @Test
  void declaraColaPrincipalReintentosYDlq() {
    Declarables declarables = RabbitMessagingConfig.topology(RabbitMessagingConfig.QUEUES);
    List<String> queues =
        declarables.getDeclarablesByType(Queue.class).stream()
            .map(Queue::getName)
            .collect(Collectors.toList());
    for (String queue : RabbitMessagingConfig.QUEUES) {
      assertThat(queues)
          .contains(queue, queue + ".retry.5s", queue + ".retry.30s", queue + ".retry.120s", queue + ".dlq");
    }
  }
}
