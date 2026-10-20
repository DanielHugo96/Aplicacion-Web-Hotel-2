package pe.hotel.reception.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Recuperador de cierres cada 30 s sobre ({@code estado}, {@code next_attempt_at})
 * (docs/especificacion/04-flujos.md § 4): {@code VALIDANDO} repite lectura y cálculo,
 * {@code CONFIRMANDO} reenvía el payload congelado y los terminales se devuelven guardados.
 */
@Configuration
public class CierreRecoveryJob {

  private static final Logger log = LoggerFactory.getLogger(CierreRecoveryJob.class);

  private final CierreService service;

  public CierreRecoveryJob(CierreService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${hotel.cierre.recovery-interval-ms:30000}")
  public void recuperar() {
    try {
      service.recuperarPendientes();
    } catch (RuntimeException e) {
      log.warn("cierre_recuperacion_ciclo_error motivo={}", e.getMessage());
    }
  }
}
