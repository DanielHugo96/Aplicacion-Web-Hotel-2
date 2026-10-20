package pe.hotel.reporting.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas del read model mediante las funciones SQL de reporting_db. */
@Repository
public class ReportRepository {

  private final JdbcTemplate jdbc;

  public ReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReportModels.BajoStock> productosBajoStock(int idHotel, int limite) {
    return jdbc.query(
        "SELECT id_producto, nombre_producto, cantidad, precio, estado"
            + " FROM fn_reporte_productos_bajo_stock(?, ?)",
        (rs, i) ->
            new ReportModels.BajoStock(
                rs.getInt("id_producto"),
                rs.getString("nombre_producto"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio"),
                rs.getBoolean("estado")),
        idHotel,
        limite);
  }

  public List<ReportModels.Venta> ventas(int idHotel, OffsetDateTime inicio, OffsetDateTime fin) {
    return jdbc.query(
        "SELECT id_producto, nombre_producto, cantidad_total, total_ingresado"
            + " FROM fn_reporte_ventas(?, ?, ?)",
        (rs, i) ->
            new ReportModels.Venta(
                rs.getInt("id_producto"),
                rs.getString("nombre_producto"),
                rs.getLong("cantidad_total"),
                rs.getBigDecimal("total_ingresado")),
        idHotel,
        inicio,
        fin);
  }

  public List<ReportModels.Ocupacion> ocupacion(int idHotel, OffsetDateTime inicio, OffsetDateTime fin) {
    return jdbc.query(
        "SELECT id_habitacion, numero_habitacion, descripcion_categoria, veces_alquilada"
            + " FROM fn_reporte_ocupacion(?, ?, ?)",
        (rs, i) ->
            new ReportModels.Ocupacion(
                rs.getInt("id_habitacion"),
                rs.getString("numero_habitacion"),
                rs.getString("descripcion_categoria"),
                rs.getLong("veces_alquilada")),
        idHotel,
        inicio,
        fin);
  }

  public List<ReportModels.Cobro> cobros(int idHotel, OffsetDateTime inicio, OffsetDateTime fin) {
    return jdbc.query(
        "SELECT id_recepcion, numero_habitacion, nombre_cliente, total_alojamiento, total_consumos,"
            + " total_general, medio_pago_adelanto, medio_pago_cierre, fecha_cierre"
            + " FROM fn_reporte_cobros(?, ?, ?)",
        (rs, i) ->
            new ReportModels.Cobro(
                rs.getInt("id_recepcion"),
                rs.getString("numero_habitacion"),
                rs.getString("nombre_cliente"),
                rs.getBigDecimal("total_alojamiento"),
                rs.getBigDecimal("total_consumos"),
                rs.getBigDecimal("total_general"),
                rs.getString("medio_pago_adelanto"),
                rs.getString("medio_pago_cierre"),
                rs.getObject("fecha_cierre", OffsetDateTime.class)),
        idHotel,
        inicio,
        fin);
  }

  public ReportModels.Dashboard dashboard(List<Integer> hoteles, LocalDate dia) {
    String array =
        hoteles == null
            ? null
            : "{" + String.join(",", hoteles.stream().map(String::valueOf).toList()) + "}";
    String json =
        jdbc.queryForObject(
            "SELECT fn_dashboard(CAST(? AS int[]), CAST(? AS date))::text", String.class, array, dia);
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
      return new ReportModels.Dashboard(
          node.path("habitacionesOcupadas").asInt(),
          node.path("habitacionesDisponibles").asInt(),
          node.path("productosBajoStock").asInt(),
          new BigDecimal(node.path("ingresosHoy").asText("0")),
          node.path("moneda").asText("PEN"));
    } catch (Exception ex) {
      throw new IllegalStateException("respuesta de fn_dashboard invalida", ex);
    }
  }

  public List<Integer> hoteles() {
    return jdbc.queryForList("SELECT id_hotel FROM hotel_projection ORDER BY id_hotel", Integer.class);
  }
}
