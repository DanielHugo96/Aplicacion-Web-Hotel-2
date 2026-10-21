package pe.hotel.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.sales.client.ReceptionClient;
import pe.hotel.sales.domain.repository.SalesRepository.Recuperable;
import pe.hotel.sales.support.SalesRepositoryFalso;
import pe.hotel.sales.support.SalesRepositoryFalso.Operacion;
import pe.hotel.sales.support.SalesRepositoryFalso.Venta;
import pe.hotel.sales.support.TransaccionFalsa;

/**
 * Recuperador de operaciones ([04-flujos.md §2](docs/especificacion/04-flujos.md#2-venta-y-stock)):
 * la venta alta vencida se rechaza sin stock y las operaciones PAGO y ANULACION en PREPARANDO
 * reanudan su admisión y su CAS en vez de rechazar la venta.
 */
class RecuperadorVentasTests {

  private SalesRepositoryFalso repo;
  private ReceptionClient reception;
  private SalesOutbox outbox;
  private RecuperadorVentas recuperador;

  @BeforeEach
  void setUp() {
    this.repo = new SalesRepositoryFalso();
    this.reception = mock(ReceptionClient.class);
    this.outbox = mock(SalesOutbox.class);
    this.recuperador =
        new RecuperadorVentas(
            repo, outbox, reception, new TransactionTemplate(new TransaccionFalsa()), 120);
  }

  @Test
  void elPagoEnPreparandoSeReintentaYTerminaPagado() {
    Venta venta = venta("PENDIENTE", "CONFIRMADA");
    repo.venta(venta);
    Operacion operacion = operacion(venta.id, "PAGO", "{\"medioPago\":\"TARJETA\"}");
    repo.operacion(operacion);

    recuperador.recuperar();

    assertThat(repo.venta(venta.id).orElseThrow().estado()).isEqualTo("PAGADO");
    assertThat(repo.venta(venta.id).orElseThrow().medioPago()).isEqualTo("TARJETA");
    assertThat(repo.operacionDeLaVenta(venta.id).estado).isEqualTo("TERMINAL");
    verify(reception).admitir(any(), any());
    verify(outbox).emitirVenta(any(), any());
    verify(outbox)
        .emitirFinalizar(
            operacion.operationId, venta.idHotel, venta.idRecepcion, venta.id, "PAGO",
            "PAGO_REGISTRADO", 3L);
  }

  @Test
  void laVentaAltaViegaSeRechazaYNoSePaga() {
    Venta venta = venta("PENDIENTE", "PENDIENTE");
    repo.venta(venta);
    Operacion operacion = operacion(venta.id, "VENTA", "{}");
    repo.operacion(operacion);

    recuperador.recuperar();

    assertThat(repo.venta(venta.id).orElseThrow().estadoOperacion()).isEqualTo("RECHAZADA");
    assertThat(repo.venta(venta.id).orElseThrow().estado()).isEqualTo("PENDIENTE");
    verify(reception, never()).admitir(any(), any());
  }

  @Test
  void elPagoDeUnaVentaQueYaPagoTerminaLaOperacionSinAdmitirNiCobrar() {
    // La venta se paga entre la selección y el reintento (el cierre la pagó): la recuperación
    // solo asegura el finalizar, no vuelve a admitir ni a cobrar.
    Venta venta = venta("PENDIENTE", "CONFIRMADA");
    repo.venta(venta);
    Operacion operacion = operacion(venta.id, "PAGO", "{\"medioPago\":\"TARJETA\"}");
    repo.operacion(operacion);
    repo.alSeleccionar(() -> venta.estado = "PAGADO");

    recuperador.recuperar();

    verify(reception, never()).admitir(any(), any());
    assertThat(repo.operacionDeLaVenta(venta.id).estado).isEqualTo("TERMINAL");
    assertThat(repo.venta(venta.id).orElseThrow().estado()).isEqualTo("PAGADO");
    assertThat(repo.venta(venta.id).orElseThrow().medioPago()).isNull();
    verify(outbox)
        .emitirFinalizar(
            operacion.operationId, venta.idHotel, venta.idRecepcion, venta.id, "PAGO",
            "PAGO_REGISTRADO", 2L);
  }

  @Test
  void laAnulacionEnCancelacionPendienteNoVuelveAdmitirNiRechaza() {
    Venta venta = venta("PENDIENTE", "CONFIRMADA");
    repo.venta(venta);
    Operacion operacion = operacion(venta.id, "ANULACION", "{\"motivo\":\"Error del cliente\"}");
    repo.operacion(operacion);
    repo.alSeleccionar(() -> venta.estadoOperacion = "CANCELACION_PENDIENTE");

    recuperador.recuperar();

    verify(reception, never()).admitir(any(), any());
    assertThat(repo.venta(venta.id).orElseThrow().estadoOperacion())
        .isEqualTo("CANCELACION_PENDIENTE");
    assertThat(repo.operacionDeLaVenta(venta.id).estado)
        .as("la restitución sigue a cargo de cancelacionesPendientes")
        .isEqualTo("PREPARANDO");
  }

  @Test
  void laAnulacionDeUnaVentaYaCanceladaTerminaLaOperacionSinAdmitir() {
    Venta venta = venta("PENDIENTE", "CONFIRMADA");
    repo.venta(venta);
    Operacion operacion = operacion(venta.id, "ANULACION", "{\"motivo\":\"Error del cliente\"}");
    repo.operacion(operacion);
    repo.alSeleccionar(() -> venta.estadoOperacion = "CANCELADA");

    recuperador.recuperar();

    verify(reception, never()).admitir(any(), any());
    assertThat(repo.operacionDeLaVenta(venta.id).estado).isEqualTo("TERMINAL");
    verify(outbox)
        .emitirFinalizar(
            operacion.operationId, venta.idHotel, venta.idRecepcion, venta.id, "ANULACION",
            "CANCELADA", 2L);
  }

  private Venta venta(String estado, String estadoOperacion) {
    Venta venta = new Venta();
    venta.id = 1;
    venta.idRecepcion = 22;
    venta.idHotel = 1;
    venta.estado = estado;
    venta.estadoOperacion = estadoOperacion;
    venta.version = 2;
    return venta;
  }

  private Operacion operacion(int idVenta, String tipo, String request) {
    Operacion operacion = new Operacion();
    operacion.operationId = UUID.randomUUID();
    operacion.idVenta = idVenta;
    operacion.tipo = tipo;
    operacion.estado = "PREPARANDO";
    operacion.request = request;
    return operacion;
  }
}
