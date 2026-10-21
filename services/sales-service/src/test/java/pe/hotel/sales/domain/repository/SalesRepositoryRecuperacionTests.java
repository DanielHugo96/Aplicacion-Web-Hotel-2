package pe.hotel.sales.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.hotel.sales.domain.repository.SalesRepository.Recuperable;
import pe.hotel.sales.support.SalesJdbcFalso;
import pe.hotel.sales.support.SalesJdbcFalso.OperacionFila;
import pe.hotel.sales.support.SalesJdbcFalso.VentaFila;

/**
 * Selección de operaciones PREPARANDO por parte de la recuperación
 * ([04-flujos.md §2](docs/especificacion/04-flujos.md#2-venta-y-stock)): la venta alta vencida se
 * rechaza por edad, mientras que las operaciones PAGO y ANULACION se reintentan por su
 * {@code next_attempt_at}.
 */
class SalesRepositoryRecuperacionTests {

  private SalesJdbcFalso jdbc;
  private SalesRepository repo;

  @BeforeEach
  void setUp() {
    this.jdbc = new SalesJdbcFalso();
    this.repo = new SalesRepository(jdbc);
  }

  @Test
  void elPagoEnPreparandoSobreVentaConfirmadaSeSelecciona() {
    UUID operationId = UUID.randomUUID();
    VentaFila venta = venta(1, "PENDIENTE", "CONFIRMADA", operationId);
    jdbc.ventas(venta);
    jdbc.operacion(operacion(operationId, 1, "PAGO", Instant.now().minusSeconds(300)));

    List<Recuperable> candidatas = repo.preparandoVencidas(120);

    assertThat(candidatas)
        .as("el PAGO en PREPARANDO con next_attempt_at vencido se recupera")
        .extracting(Recuperable::tipo)
        .contains("PAGO");
    assertThat(candidatas).extracting(Recuperable::idVenta).contains(1);
  }

  @Test
  void elPagoEnPreparandoDeUnaVentaYaPagadaNoSeSelecciona() {
    UUID operationId = UUID.randomUUID();
    VentaFila venta = venta(1, "PAGADO", "CONFIRMADA", operationId);
    jdbc.ventas(venta);
    jdbc.operacion(operacion(operationId, 1, "PAGO", Instant.now().minusSeconds(300)));

    List<Recuperable> candidatas = repo.preparandoVencidas(120);

    assertThat(candidatas).as("una venta ya pagada no se vuelve a cobrar").isEmpty();
  }

  @Test
  void laAnulacionEnPreparandoSeSeleccionaPorNextAttemptAt() {
    UUID operationId = UUID.randomUUID();
    VentaFila venta = venta(1, "PENDIENTE", "CONFIRMADA", operationId);
    jdbc.ventas(venta);
    jdbc.operacion(operacion(operationId, 1, "ANULACION", Instant.now().minusSeconds(300)));

    List<Recuperable> candidatas = repo.preparandoVencidas(120);

    assertThat(candidatas).extracting(Recuperable::tipo).contains("ANULACION");
  }

  @Test
  void laVentaAltaViegaSeSigueSeleccionandoParaRechazo() {
    UUID operationId = UUID.randomUUID();
    VentaFila venta = venta(1, "PENDIENTE", "PENDIENTE", operationId);
    jdbc.ventas(venta);
    jdbc.operacion(operacion(operationId, 1, "VENTA", Instant.now().minusSeconds(300)));

    List<Recuperable> candidatas = repo.preparandoVencidas(120);

    assertThat(candidatas).extracting(Recuperable::tipo).contains("VENTA");
  }

  @Test
  void laVentaAltaRecienteNoSeSelecciona() {
    UUID operationId = UUID.randomUUID();
    VentaFila venta = venta(1, "PENDIENTE", "PENDIENTE", operationId);
    jdbc.ventas(venta);
    jdbc.operacion(operacion(operationId, 1, "VENTA", Instant.now().minusSeconds(10)));

    List<Recuperable> candidatas = repo.preparandoVencidas(120);

    assertThat(candidatas).as("la venta alta se rechaza a los 120 s, no antes").isEmpty();
  }

  private VentaFila venta(int id, String estado, String estadoOperacion, UUID operationId) {
    VentaFila venta = new VentaFila();
    venta.id = id;
    venta.estado = estado;
    venta.estadoOperacion = estadoOperacion;
    venta.currentOperationId = operationId;
    return venta;
  }

  private OperacionFila operacion(UUID operationId, int idVenta, String tipo, Instant createdAt) {
    OperacionFila operacion = new OperacionFila();
    operacion.operationId = operationId;
    operacion.idVenta = idVenta;
    operacion.tipo = tipo;
    operacion.estado = "PREPARANDO";
    operacion.createdAt = createdAt;
    operacion.nextAttemptAt = Instant.now().minusSeconds(5);
    return operacion;
  }
}
