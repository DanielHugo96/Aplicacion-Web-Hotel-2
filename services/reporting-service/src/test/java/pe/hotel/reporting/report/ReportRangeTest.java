package pe.hotel.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;

class ReportRangeTest {

  @Test
  void aceptaRangoIsoConOffset() {
    ReportRange.Range range =
        ReportRange.parse("2026-10-01T00:00:00-05:00", "2026-10-08T00:00:00-05:00", 366, false);
    assertThat(range.inicioLima()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(range.finLima()).isEqualTo(LocalDate.of(2026, 10, 8));
  }

  @Test
  void rechazaInicioMayorOIgualQueFin() {
    assertThatThrownBy(
            () ->
                ReportRange.parse(
                    "2026-10-08T00:00:00-05:00", "2026-10-01T00:00:00-05:00", 366, false))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void rechazaIntervaloMayorA366Dias() {
    assertThatThrownBy(
            () ->
                ReportRange.parse(
                    "2026-01-01T00:00:00-05:00", "2027-01-03T00:00:00-05:00", 366, false))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void ocupacionExigeMedianocheDeLima() {
    assertThatThrownBy(
            () ->
                ReportRange.parse(
                    "2026-10-01T01:00:00-05:00", "2026-10-08T00:00:00-05:00", 366, true))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void ocupacionAceptaMedianocheDeLimaConOtroOffsetEquivalente() {
    ReportRange.Range range =
        ReportRange.parse("2026-10-01T05:00:00Z", "2026-10-08T05:00:00Z", 366, true);
    assertThat(range.inicioLima()).isEqualTo(LocalDate.of(2026, 10, 1));
  }

  @Test
  void paginaFueraDeRangoEsValidacion() {
    assertThatThrownBy(() -> ReportService.validarPagina(-1, 20))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> ReportService.validarPagina(0, 101))
        .isInstanceOf(ApiException.class);
  }
}
