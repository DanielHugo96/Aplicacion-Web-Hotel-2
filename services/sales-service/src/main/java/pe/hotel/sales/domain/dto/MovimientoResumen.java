package pe.hotel.sales.domain.dto;

/** Desenlace del movimiento de stock de una venta (consulta interna de inventory). */
public record MovimientoResumen(
    Integer idVenta, Integer idHotel, String estado, Object resultado, Integer version) {}
