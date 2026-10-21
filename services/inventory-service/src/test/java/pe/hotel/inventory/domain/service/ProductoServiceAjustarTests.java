package pe.hotel.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hotel.inventory.client.HotelClient;
import pe.hotel.inventory.domain.dto.AjusteRequest;
import pe.hotel.inventory.domain.dto.AjusteResultado;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;
import pe.hotel.inventory.support.ProductoJdbcFalso;

/**
 * Regresión del ajuste de stock: la sede del ajuste es la del cuerpo del contrato
 * ({@code AjusteRequest.idHotel}), el saldo nunca queda negativo, la versión de la sede sube y el
 * actor queda auditado. El ajuste sigue usando {@code fn_stock_ajustar} con su lock.
 *
 * <p>Ver docs/contracts/openapi/inventory.yaml § /api/producto/{id}/ajustes.
 */
class ProductoServiceAjustarTests {

  private final ProductoJdbcFalso jdbc = new ProductoJdbcFalso(new ProductoJdbcFalso.Fila(1, 1, 3));

  private final ProductoService service =
      new ProductoService(jdbc, mock(OutboxService.class), mock(HotelClient.class));

  @Test
  void ajusteConLaSedeDelCuerpoSubeElStockYAuditaElActor() {
    AjusteResultado resultado =
        service.ajustar(1, 1, "\"1\"", new AjusteRequest(1, 2, "Reposición"), 42);

    assertThat(resultado.cantidad()).isEqualTo(5);
    assertThat(resultado.version()).isEqualTo(2);
    assertThat(resultado.ajusteId()).isNotNull();
    assertThat(jdbc.fila(1, 1).cantidad).isEqualTo(5);
    assertThat(jdbc.fila(1, 1).versionSede).isEqualTo(2);
    assertThat(jdbc.actoresAuditados()).containsExactly(42);
  }

  @Test
  void ajusteLlamaAFnStockAjustarConLaSedeElDeltaElMotivoYElActor() {
    service.ajustar(1, 1, "\"1\"", new AjusteRequest(1, 2, "Reposición"), 42);

    assertThat(jdbc.ultimaConsultaQueContenga("fn_stock_ajustar")).contains("fn_stock_ajustar");
    Object[] parametros = jdbc.ultimosParametrosDe("fn_stock_ajustar");
    assertThat(parametros[0]).isInstanceOf(UUID.class);
    assertThat(parametros[1]).isEqualTo(1);
    assertThat(parametros[2]).isEqualTo(1);
    assertThat(parametros[3]).isEqualTo(2);
    assertThat(parametros[4]).isEqualTo("Reposición");
    assertThat(parametros[5]).isEqualTo(42);
  }

  @Test
  void ajusteSinIfMatchDevuelvePreconditionRequired() {
    assertThatThrownBy(() -> service.ajustar(1, 1, null, new AjusteRequest(1, 2, "Reposición"), 42))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PRECONDITION_REQUIRED));
  }

  @Test
  void ajusteConVersionViejaDevuelvePreconditionFailed() {
    assertThatThrownBy(
            () -> service.ajustar(1, 1, "\"9\"", new AjusteRequest(1, 2, "Reposición"), 42))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PRECONDITION_FAILED));
  }

  @Test
  void ajusteConDeltaCeroDevuelveValidationError() {
    assertThatThrownBy(
            () -> service.ajustar(1, 1, "\"1\"", new AjusteRequest(1, 0, "Reposición"), 42))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }

  @Test
  void ajusteQueDejariaSaldoNegativoDevuelveValidationError() {
    assertThatThrownBy(
            () -> service.ajustar(1, 1, "\"1\"", new AjusteRequest(1, -10, "Reposición"), 42))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    assertThat(jdbc.fila(1, 1).cantidad).isEqualTo(3);
  }

  @Test
  void ajusteDeUnProductoQueNoEstaEnLaSedeDevuelveResourceNotFound() {
    assertThatThrownBy(
            () -> service.ajustar(99, 1, "\"1\"", new AjusteRequest(1, 2, "Reposición"), 42))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }
}
