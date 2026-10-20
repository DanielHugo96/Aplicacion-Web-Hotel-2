package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/** Totales de ventas CONFIRMADAS de la estadía para el cierre de consumos. */
public record ResumenCierre(
    Integer idRecepcion,
    Integer idHotel,
    BigDecimal totalPendiente,
    BigDecimal totalPagado,
    List<Integer> ventasPendientes,
    boolean hayOperacionesAbiertas) {}
