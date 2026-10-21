package pe.hotel.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.hotel.sales.client.InventoryClient;
import pe.hotel.sales.client.ReceptionClient;
import pe.hotel.sales.domain.dto.PagoRequest;
import pe.hotel.sales.domain.security.CurrentUser;
import pe.hotel.sales.platform.ApiException;
import pe.hotel.sales.platform.ErrorCode;
import pe.hotel.sales.support.SalesRepositoryFalso;
import pe.hotel.sales.support.SalesRepositoryFalso.Venta;
import pe.hotel.sales.support.TransaccionFalsa;

/**
 * Pago y anulación con la admisión de reception caída ([04-flujos.md §2](docs/especificacion/
 * 04-flujos.md#2-venta-y-stock)): un fallo de transporte deja la operación en PREPARANDO y
 * responde 202; solo un rechazo de negocio (409, 403, 404) termina la operación.
 */
class SalesServicePagoTests {

  private SalesRepositoryFalso repo;
  private ReceptionClient reception;
  private SalesOutbox outbox;
  private SalesService service;

  @BeforeEach
  void setUp() {
    this.repo = new SalesRepositoryFalso();
    this.reception = mock(ReceptionClient.class);
    this.outbox = mock(SalesOutbox.class);
    CurrentUser usuario = mock(CurrentUser.class);
    when(usuario.isAdmin()).thenReturn(true);
    when(usuario.subjectId()).thenReturn(1);
    this.service =
        new SalesService(
            repo,
            reception,
            mock(InventoryClient.class),
            outbox,
            usuario,
            new TransaccionFalsa());
    Venta venta = new Venta();
    venta.id = 1;
    venta.estado = "PENDIENTE";
    venta.estadoOperacion = "CONFIRMADA";
    venta.version = 2;
    repo.venta(venta);
  }

  @Test
  void elPagoConAdmissionCaidaDevuelve202YDejaLaOperacionPreparando() {
    when(reception.admitir(any(), any())).thenThrow(fallo(503, "Service Unavailable"));

    SalesService.Resultado resultado =
        service.pagar(1, new PagoRequest("PAGADO", "TARJETA"), "\"2\"");

    assertThat(resultado.status()).isEqualTo(202);
    assertThat(resultado.venta().idVenta()).isEqualTo(1);
    assertThat(repo.operacionDeLaVenta(1).estado)
        .as("la operación PAGO sigue viva para que la recuperación la reintente")
        .isEqualTo("PREPARANDO");
    verify(outbox, never())
        .emitirFinalizar(any(), anyInt(), anyInt(), anyInt(), any(), any(), anyLong());
  }

  @Test
  void elPagoConRechazoDeNegocioTerminaLaOperacionYDevuelveElError() {
    when(reception.admitir(any(), any())).thenThrow(fallo(409, "Conflict"));

    assertThatThrownBy(() -> service.pagar(1, new PagoRequest("PAGADO", "TARJETA"), "\"2\""))
        .isInstanceOf(ApiException.class)
        .satisfies(
            error ->
                assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.STAY_NOT_ACTIVE));
    assertThat(repo.operacionDeLaVenta(1).estado)
        .as("un rechazo de negocio sí termina la operación")
        .isEqualTo("TERMINAL");
  }

  @Test
  void laAnulacionConAdmissionCaidaDevuelve202YDejaLaOperacionPreparando() {
    when(reception.admitir(any(), any())).thenThrow(fallo(503, "Service Unavailable"));

    SalesService.Resultado resultado = service.anular(1, "Error del cliente", "\"2\"");

    assertThat(resultado.status()).isEqualTo(202);
    assertThat(repo.operacionDeLaVenta(1).tipo).isEqualTo("ANULACION");
    assertThat(repo.operacionDeLaVenta(1).estado)
        .as("la operación ANULACION sigue viva para que la recuperación la reintente")
        .isEqualTo("PREPARANDO");
    verify(outbox, never())
        .emitirFinalizar(any(), anyInt(), anyInt(), anyInt(), any(), any(), anyLong());
  }

  @Test
  void laAnulacionConRechazoDeNegocioTerminaLaOperacionYDevuelveElError() {
    when(reception.admitir(any(), any())).thenThrow(fallo(409, "Conflict"));

    assertThatThrownBy(() -> service.anular(1, "Error del cliente", "\"2\""))
        .isInstanceOf(ApiException.class)
        .satisfies(
            error ->
                assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.STAY_NOT_ACTIVE));
    assertThat(repo.operacionDeLaVenta(1).estado).isEqualTo("TERMINAL");
  }

  /** Error de transporte de reception con el estado HTTP indicado. */
  private static FeignException fallo(int status, String motivo) {
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "/internal/recepciones/22/operaciones",
            Map.of(),
            new byte[0],
            StandardCharsets.UTF_8);
    Response response =
        Response.builder()
            .status(status)
            .reason(motivo)
            .request(request)
            .headers(Map.of())
            .build();
    return FeignException.errorStatus("ReceptionClient#admitir", response);
  }
}
