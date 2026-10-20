package pe.hotel.sales.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Petición de cierre de consumos (jsonb cerrado). */
public record ConfirmacionCierre(
    @NotNull UUID closureId,
    @NotNull @Positive Integer idRecepcion,
    @NotNull @Positive Integer idHotel,
    @NotNull @DecimalMin("0.00") BigDecimal expectedPendingTotal,
    @NotNull String medioPago) {}
