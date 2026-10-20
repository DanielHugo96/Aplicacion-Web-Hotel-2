package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resultado autoritativo del débito de stock con precios y nombres congelados. */
public record DebitoResultado(
    Integer idVenta,
    Integer idHotel,
    String estado,
    String moneda,
    BigDecimal total,
    List<DebitoResultadoItem> items,
    Integer version) {}
