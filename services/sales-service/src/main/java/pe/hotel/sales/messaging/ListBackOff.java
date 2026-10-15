package pe.hotel.sales.messaging;

import java.util.List;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/** Backoff con la secuencia exacta de esperas de Kafka (1s, 5s, 30s, 120s, 300s) y luego STOP. */
public class ListBackOff implements BackOff {

  private final List<Long> intervalsMs;

  public ListBackOff(List<Long> intervalsMs) {
    this.intervalsMs = List.copyOf(intervalsMs);
  }

  @Override
  public BackOffExecution start() {
    return new BackOffExecution() {
      private int index = 0;

      @Override
      public long nextBackOff() {
        if (index >= intervalsMs.size()) {
          return BackOffExecution.STOP;
        }
        return intervalsMs.get(index++);
      }
    };
  }
}
