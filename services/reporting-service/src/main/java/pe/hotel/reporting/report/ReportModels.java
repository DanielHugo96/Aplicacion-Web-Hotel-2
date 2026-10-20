package pe.hotel.reporting.report;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** DTO de los cinco reportes (docs/contracts/openapi/reporting.yaml § schemas). */
public final class ReportModels {

  private ReportModels() {}

  public record BajoStock(
      int idProducto, String nombreProducto, int cantidad, BigDecimal precio, boolean estado) {}

  public record Venta(
      int idProducto, String nombreProducto, long cantidadTotal, BigDecimal totalIngresado) {}

  public record Ocupacion(
      int idHabitacion, String numeroHabitacion, String descripcionCategoria, long vecesAlquilada) {}

  public record Cobro(
      int idRecepcion,
      String numeroHabitacion,
      String nombreCliente,
      BigDecimal totalAlojamiento,
      BigDecimal totalConsumos,
      BigDecimal totalGeneral,
      String medioPagoAdelanto,
      String medioPagoCierre,
      OffsetDateTime fechaCierre) {}

  public record Dashboard(
      int habitacionesOcupadas,
      int habitacionesDisponibles,
      int productosBajoStock,
      BigDecimal ingresosHoy,
      String moneda) {}
}
