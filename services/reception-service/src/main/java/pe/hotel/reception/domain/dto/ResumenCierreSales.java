package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;

/** Totales de ventas que devuelve sales en GET /internal/cierres/{idRecepcion}/resumen. */
public record ResumenCierreSales(
    Integer idRecepcion,
    Integer idHotel,
    BigDecimal totalPendiente,
    BigDecimal totalPagado,
    java.util.List<Integer> ventasPendientes,
    boolean hayOperacionesAbiertas) {}
