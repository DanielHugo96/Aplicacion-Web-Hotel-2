package pe.hotel.reception.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hotel.reception.domain.dto.CalculoCierre;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.domain.dto.ResumenCierreSales;

/** Fórmulas del cierre con HALF_UP: demo 70/20/7 => saldo 50 y cobro 57. */
class CierreCalculoTests {

  private final CierreService service =
      new CierreService(null, null, null, null, new ObjectMapper(), null);

  @Test
  void demo70Adelanto20Consumos7DaCobro57() {
    CalculoCierre calculo = service.calcular(view("70.00", "20.00"), resumen("7.00", "0.00"), new BigDecimal("0.00"));
    assertThat(calculo.alojamientoTotal()).isEqualByComparingTo("70.00");
    assertThat(calculo.saldoAlojamiento()).isEqualByComparingTo("50.00");
    assertThat(calculo.consumosPendientes()).isEqualByComparingTo("7.00");
    assertThat(calculo.cobroAhora()).isEqualByComparingTo("57.00");
    assertThat(calculo.totalGeneral()).isEqualByComparingTo("77.00");
    assertThat(calculo.expectedPendingTotal()).isEqualByComparingTo("7.00");
  }

  @Test
  void penalidadSeSumaAlAlojamientoYAlCobro() {
    CalculoCierre calculo = service.calcular(view("70.00", "20.00"), resumen("7.00", "3.00"), new BigDecimal("10.00"));
    assertThat(calculo.alojamientoTotal()).isEqualByComparingTo("80.00");
    assertThat(calculo.saldoAlojamiento()).isEqualByComparingTo("60.00");
    assertThat(calculo.consumosTotal()).isEqualByComparingTo("10.00");
    assertThat(calculo.cobroAhora()).isEqualByComparingTo("67.00");
    assertThat(calculo.totalGeneral()).isEqualByComparingTo("90.00");
  }

  @Test
  void sinConsumosElCobroEsSoloElSaldo() {
    CalculoCierre calculo = service.calcular(view("70.00", "20.00"), resumen("0.00", "0.00"), new BigDecimal("0.00"));
    assertThat(calculo.cobroAhora()).isEqualByComparingTo("50.00");
    assertThat(calculo.totalGeneral()).isEqualByComparingTo("70.00");
  }

  private static ResumenCierreSales resumen(String pendiente, String pagado) {
    return new ResumenCierreSales(
        1001, 1, new BigDecimal(pendiente), new BigDecimal(pagado), List.of(), false);
  }

  private static RecepcionView view(String precio, String adelanto) {
    return new RecepcionView(
        1001, 1, 100, 101, "101", "Individual", "Primer piso", "Habitación de demostración",
        new BigDecimal("70.00"), "Cliente", "Uno", "DNI", "90000100", "cliente.uno@hotel.test",
        LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), null,
        new BigDecimal(precio), new BigDecimal(adelanto), "EFECTIVO", new BigDecimal("50.00"),
        new BigDecimal(adelanto), BigDecimal.ZERO, null, null, true, "ACTIVA", false, false, 1L,
        null, null, null);
  }
}
