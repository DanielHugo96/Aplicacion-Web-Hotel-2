package pe.hotel.sales.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.sales.client.ReceptionClient;
import pe.hotel.sales.domain.dto.OperacionAdmisionRequest;
import pe.hotel.sales.domain.model.OperacionRow;
import pe.hotel.sales.domain.model.VentaRow;
import pe.hotel.sales.domain.repository.SalesRepository;
import pe.hotel.sales.platform.ApiException;

/**
 * Recuperador cada 30 s de operaciones y ventas atascadas. Usa el mismo CAS que la respuesta HTTP:
 * PREPARANDO viejo se rechaza sin stock; DEBITO_SOLICITADO viejo se compensa con restitución y
 * nunca se confirma por antigüedad; PAGO y ANULACION se reintentan sin tocar inventario de más.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecuperadorVentas {

  private static final Logger log = LoggerFactory.getLogger(RecuperadorVentas.class);

  private final SalesRepository repo;
  private final SalesOutbox outbox;
  private final ReceptionClient reception;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper = new ObjectMapper();
  private final int staleSeconds;

  public RecuperadorVentas(
      SalesRepository repo,
      SalesOutbox outbox,
      ReceptionClient reception,
      TransactionTemplate tx,
      @Value("${hotel.recuperacion.stale-seconds:120}") int staleSeconds) {
    this.repo = repo;
    this.outbox = outbox;
    this.reception = reception;
    this.tx = tx;
    this.staleSeconds = staleSeconds;
  }

  @Scheduled(fixedDelayString = "${hotel.recuperacion.interval-ms:30000}")
  public void recuperar() {
    try {
      rechazarPreparandoVencidas();
      compensarDebitosVencidos();
      reenviarRestitucionesPendientes();
      reintentarPagos();
      reintentarAnulaciones();
    } catch (Exception ex) {
      log.warn("recuperacion_error error={}", ex.getMessage(), ex);
    }
  }

  private void rechazarPreparandoVencidas() {
    for (SalesRepository.Recuperable rec : repo.preparandoVencidas(staleSeconds)) {
      OperacionRow operacion = repo.operacion(rec.operationId()).orElse(null);
      if (operacion == null) {
        continue;
      }
      if ("VENTA".equals(operacion.tipo())) {
        tx.executeWithoutResult(
            status -> {
              if (repo.rechazarVenta(rec.idVenta(), rec.operationId())) {
                VentaRow venta = repo.venta(rec.idVenta()).orElseThrow();
                outbox.emitirFinalizar(
                    rec.operationId(),
                    venta.idHotel(),
                    venta.idRecepcion(),
                    venta.id(),
                    "VENTA",
                    "RECHAZADA",
                    venta.version());
                log.info("recuperacion_venta_rechazada idVenta={}", rec.idVenta());
              }
            });
      } else {
        reintentarAdmision(operacion);
      }
    }
  }

  private void reintentarAdmision(OperacionRow operacion) {
    VentaRow venta = repo.venta(operacion.idVenta()).orElse(null);
    if (venta == null) {
      return;
    }
    try {
      reception.admitir(
          venta.idRecepcion(),
          new OperacionAdmisionRequest(
              operacion.operationId(),
              venta.id(),
              venta.idHotel(),
              operacion.tipo(),
              operacion.actorId(),
              operacion.actorRol()));
    } catch (FeignException ex) {
      if (ex.status() == 409 || ex.status() == 403 || ex.status() == 404) {
        tx.executeWithoutResult(
            status -> repo.terminarOperacionSiCoincide(operacion.operationId(), venta.id()));
        log.info("recuperacion_admision_rechazada idVenta={} tipo={}", venta.id(), operacion.tipo());
      } else {
        repo.posponerOperacion(operacion.operationId(), 60);
      }
      return;
    }
    if ("PAGO".equals(operacion.tipo())) {
      aplicarPago(operacion, venta);
    } else if ("ANULACION".equals(operacion.tipo())) {
      tx.executeWithoutResult(
          status -> {
            repo.marcarOperacionAdmitida(operacion.operationId());
            if (repo.solicitarCancelacion(venta.id(), motivo(operacion))) {
              VentaRow actual = repo.venta(venta.id()).orElseThrow();
              outbox.emitirRestituir(
                  actual.id(), actual.idRecepcion(), motivo(operacion), actual.idHotel(), actual.version());
            }
          });
    }
  }

  private void compensarDebitosVencidos() {
    for (SalesRepository.Recuperable rec : repo.debitoVencido(staleSeconds)) {
      String motivo = "Recuperación de débito sin respuesta";
      tx.executeWithoutResult(
          status -> {
            if (repo.compensarVenta(rec.idVenta(), rec.operationId(), motivo)) {
              VentaRow venta = repo.venta(rec.idVenta()).orElseThrow();
              if (!repo.existeComandoRestituirPendiente(venta.id())) {
                outbox.emitirRestituir(
                    venta.id(), venta.idRecepcion(), motivo, venta.idHotel(), venta.version());
              }
              log.info("recuperacion_compensacion idVenta={}", venta.id());
            }
          });
    }
  }

  private void reenviarRestitucionesPendientes() {
    for (SalesRepository.Recuperable rec : repo.cancelacionesPendientes()) {
      tx.executeWithoutResult(
          status -> {
            VentaRow venta = repo.venta(rec.idVenta()).orElse(null);
            if (venta == null) {
              return;
            }
            if (!repo.existeComandoRestituirPendiente(venta.id())) {
              outbox.emitirRestituir(
                  venta.id(),
                  venta.idRecepcion(),
                  venta.motivoCancelacion() == null ? "Anulación de venta" : venta.motivoCancelacion(),
                  venta.idHotel(),
                  venta.version());
            }
            repo.posponerOperacion(rec.operationId(), 60);
          });
    }
  }

  private void reintentarPagos() {
    for (SalesRepository.Recuperable rec : repo.operacionesPorReintentar("PAGO")) {
      OperacionRow operacion = repo.operacion(rec.operationId()).orElse(null);
      VentaRow venta = repo.venta(rec.idVenta()).orElse(null);
      if (operacion == null || venta == null) {
        continue;
      }
      if ("PAGADO".equals(venta.estado())) {
        repo.terminarOperacionSiCoincide(operacion.operationId(), venta.id());
        continue;
      }
      if ("CONFIRMADA".equals(venta.estadoOperacion())) {
        aplicarPago(operacion, venta);
      } else {
        repo.posponerOperacion(operacion.operationId(), 30);
      }
    }
  }

  private void aplicarPago(OperacionRow operacion, VentaRow venta) {
    String medio = medioDe(operacion);
    Boolean pagado =
        tx.execute(
            status -> {
              repo.marcarOperacionAdmitida(operacion.operationId());
              boolean ok =
                  repo.pagarVenta(venta.id(), operacion.operationId(), venta.version(), medio, null);
              if (ok) {
                VentaRow actual = repo.venta(venta.id()).orElseThrow();
                outbox.emitirVenta(actual, repo.detalles(venta.id()));
                outbox.emitirFinalizar(
                    operacion.operationId(),
                    actual.idHotel(),
                    actual.idRecepcion(),
                    actual.id(),
                    "PAGO",
                    "PAGO_REGISTRADO",
                    actual.version());
              }
              return ok;
            });
    if (Boolean.TRUE.equals(pagado)) {
      log.info("recuperacion_pago_registrado idVenta={}", venta.id());
    } else {
      repo.posponerOperacion(operacion.operationId(), 30);
    }
  }

  private void reintentarAnulaciones() {
    for (SalesRepository.Recuperable rec : repo.operacionesPorReintentar("ANULACION")) {
      VentaRow venta = repo.venta(rec.idVenta()).orElse(null);
      if (venta == null) {
        continue;
      }
      if ("CANCELADA".equals(venta.estadoOperacion())) {
        repo.terminarOperacionSiCoincide(rec.operationId(), venta.id());
        continue;
      }
      if ("CANCELACION_PENDIENTE".equals(venta.estadoOperacion())) {
        if (!repo.existeComandoRestituirPendiente(venta.id())) {
          outbox.emitirRestituir(
              venta.id(),
              venta.idRecepcion(),
              venta.motivoCancelacion() == null ? "Anulación de venta" : venta.motivoCancelacion(),
              venta.idHotel(),
              venta.version());
        }
        repo.posponerOperacion(rec.operationId(), 60);
      } else {
        repo.posponerOperacion(rec.operationId(), 30);
      }
    }
  }

  private String medioDe(OperacionRow operacion) {
    try {
      String medio = mapper.readTree(operacion.request()).path("medioPago").asText("");
      return medio.isBlank() ? "OTRO" : medio;
    } catch (Exception ex) {
      return "OTRO";
    }
  }

  private String motivo(OperacionRow operacion) {
    try {
      String motivo = mapper.readTree(operacion.request()).path("motivo").asText("");
      return motivo.isBlank() ? "Anulación de venta" : motivo;
    } catch (Exception ex) {
      return "Anulación de venta";
    }
  }
}
