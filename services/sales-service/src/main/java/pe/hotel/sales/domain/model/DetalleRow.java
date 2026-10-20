package pe.hotel.sales.domain.model;

import java.math.BigDecimal;

/** Fila de la tabla detalle_venta. */
public record DetalleRow(
    Integer id,
    Integer idVenta,
    Integer idProducto,
    String nombreProducto,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal subTotal) {}
