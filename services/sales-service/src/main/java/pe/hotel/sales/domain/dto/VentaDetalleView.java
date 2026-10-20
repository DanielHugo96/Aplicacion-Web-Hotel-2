package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;

/** Línea confirmada con el snapshot autoritativo de inventory. */
public record VentaDetalleView(
    Integer idDetalleVenta,
    Integer idProducto,
    String nombreProducto,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal subTotal) {}
