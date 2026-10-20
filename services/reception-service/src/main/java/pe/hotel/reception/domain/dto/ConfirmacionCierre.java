package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** POST /internal/cierres/confirmar de sales; siempre con el payload congelado e idéntico. */
public record ConfirmacionCierre(
    UUID closureId,
    Integer idRecepcion,
    Integer idHotel,
    BigDecimal expectedPendingTotal,
    String medioPago) {}
