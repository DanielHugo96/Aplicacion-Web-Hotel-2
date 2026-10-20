package pe.hotel.hotel.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.hotel.hotel.domain.service.OperacionHabitacionService;

/**
 * Job de recuperación de operaciones PREPARANDO (runbook RB3). Reintenta con el mismo blockId;
 * nunca libera por timeout ni modifica el gate de reception por SQL. La periodicidad y el backoff
 * son configurables por entorno.
 */
@Component
public class OperacionRecoveryJob {

  private static final Logger log = LoggerFactory.getLogger(OperacionRecoveryJob.class);

  private final OperacionHabitacionService service;
  private final int batchSize;
  private final long maxBackoffSeconds;
  private final int alertPendingMinutes;

  public OperacionRecoveryJob(
      OperacionHabitacionService service,
      @Value("${hotel.operaciones.recovery-batch-size:20}") int batchSize,
      @Value("${hotel.operaciones.recovery-max-backoff-seconds:300}") long maxBackoffSeconds,
      @Value("${hotel.operaciones.alert-pending-minutes:10}") int alertPendingMinutes) {
    this.service = service;
    this.batchSize = batchSize;
    this.maxBackoffSeconds = maxBackoffSeconds;
    this.alertPendingMinutes = alertPendingMinutes;
  }

  @Scheduled(fixedDelayString = "${hotel.operaciones.recovery-interval-ms:15000}")
  public void recuperar() {
    try {
      int procesadas = service.reintentarPendientes(batchSize, maxBackoffSeconds);
      if (procesadas > 0) {
        log.info("operacion_recovery_batch procesadas={}", procesadas);
      }
      service.alertarPreparando(alertPendingMinutes);
    } catch (Exception ex) {
      log.warn("operacion_recovery_error error={}", ex.getMessage());
    }
  }
}
