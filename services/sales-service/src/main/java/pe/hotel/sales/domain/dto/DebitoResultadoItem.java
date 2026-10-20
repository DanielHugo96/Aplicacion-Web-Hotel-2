package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;

/** Ítem del resultado autoritativo del débito de inventory. */
public record DebitoResultadoItem(
    Integer idProducto,
    String nombreProducto,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal subTotal) {}
