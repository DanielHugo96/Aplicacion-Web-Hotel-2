package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Recibo único que persiste sales al confirmar el cierre de consumos. */
public record ReceiptSales(
    UUID closureId,
    Integer idRecepcion,
    Integer idHotel,
    BigDecimal totalPagadoPrevio,
    BigDecimal totalPagadoAhora,
    BigDecimal totalConsumos,
    String medioPago,
    List<Integer> ventasPagadas,
    Instant confirmedAt) {}
