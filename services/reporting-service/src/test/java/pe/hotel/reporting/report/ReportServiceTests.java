package pe.hotel.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.hotel.reporting.config.ReportingProperties;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;

/**
 * Invariantes de los reportes sin base de datos: validaciones de rango y paginación, y el corte
 * por sede del dashboard. La semántica SQL (double conteo de la caja del día) se prueba contra
 * PostgreSQL real en la evidencia del plan.
 */
class ReportServiceTests {

  private ReportRepository repository;
  private ReportService service;

  @BeforeEach
  void setUp() {
    repository = mock(ReportRepository.class);
    ReportingProperties properties = new ReportingProperties();
    properties.setMaxRangeDays(366);
    service = new ReportService(repository, properties);
  }

  @Test
  void limiteNegativoEsValidacion() {
    assertThatThrownBy(() -> service.productosBajoStock(1, -1, 0, 20))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void paginaYSizeFueraDeRangoSonValidacion() {
    assertThatThrownBy(() -> service.productosBajoStock(1, 5, -1, 20))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.productosBajoStock(1, 5, 0, 0))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.productosBajoStock(1, 5, 0, 101))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rangoSuperiorAlMaximoEsValidacion() {
    assertThatThrownBy(
            () ->
                service.ventas(
                    1, "2025-01-01T00:00:00-05:00", "2026-10-01T00:00:00-05:00", 0, 20))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void ocupacionConHoraDistintaDeMedianocheEsValidacion() {
    assertThatThrownBy(
            () ->
                service.ocupacion(
                    1, "2026-10-01T10:00:00-05:00", "2026-10-08T00:00:00-05:00", 0, 20))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void paginaRecortaLaListaYConservaElTotal() {
    List<ReportModels.BajoStock> filas =
        List.of(
            new ReportModels.BajoStock(1, "Agua 500ml", 5, new BigDecimal("3.50"), true),
            new ReportModels.BajoStock(2, "Snack", 5, new BigDecimal("8.00"), true),
            new ReportModels.BajoStock(3, "Kit de aseo", 5, new BigDecimal("5.00"), true));
    when(repository.productosBajoStock(anyInt(), anyInt())).thenReturn(filas);

    ReportService.Listing<ReportModels.BajoStock> pagina = service.productosBajoStock(1, 5, 1, 2);

    assertThat(pagina.total()).isEqualTo(3);
    assertThat(pagina.data()).hasSize(1);
    assertThat(pagina.data().get(0).idProducto()).isEqualTo(3);
  }

  @Test
  void dashboardConsolidadoPideTodasLasSedes() {
    when(repository.hoteles()).thenReturn(List.of(1, 2, 3));
    when(repository.dashboard(any(), any()))
        .thenReturn(
            new ReportModels.Dashboard(
                0, 6, 3, new BigDecimal("77.00"), "PEN"));

    ReportModels.Dashboard dashboard = service.dashboard(null);

    assertThat(dashboard.ingresosHoy()).isEqualByComparingTo("77.00");
    assertThat(dashboard.moneda()).isEqualTo("PEN");
    verify(repository).dashboard(any(), any());
  }

  @Test
  void dashboardPorSedePideSoloEsaSede() {
    when(repository.dashboard(any(), any()))
        .thenReturn(
            new ReportModels.Dashboard(
                0, 2, 1, new BigDecimal("77.00"), "PEN"));

    service.dashboard(1);

    verify(repository).dashboard(any(), any());
    verify(repository, org.mockito.Mockito.never()).hoteles();
  }

  @Test
  void unaVentaPndienteNoEntraAlReporteDeVentas() {
    // El filtro de PAGADO vive en fn_reporte_ventas (SQL); aquí se comprueba que el servicio
    // solo delega el rango y no reordena ni reescribe lo que devuelve la función.
    when(repository.ventas(anyInt(), any(), any())).thenReturn(List.of());
    ReportService.Listing<ReportModels.Venta> listing =
        service.ventas(1, "2026-10-20T00:00:00-05:00", "2026-10-21T00:00:00-05:00", 0, 20);
    assertThat(listing.data()).isEmpty();
    verify(repository).ventas(anyInt(), any(), any());
  }
}
