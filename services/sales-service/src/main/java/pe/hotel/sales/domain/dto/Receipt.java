package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Recibo único del cierre de consumos; se devuelve tal cual en reintentos. */
public record Receipt(
    UUID closureId,
    Integer idRecepcion,
    Integer idHotel,
    BigDecimal totalPagadoPrevio,
    BigDecimal totalPagadoAhora,
    BigDecimal totalConsumos,
    String medioPago,
    List<Integer> ventasPagadas,
    Instant confirmedAt) {}
