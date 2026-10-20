package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Asignación inicial de un producto existente a una sede nueva; alta única.
 * Ver docs/contracts/openapi/inventory.yaml schema SedeAsignacion.
 */
public record SedeAsignacion(
    @NotNull Integer idHotel,
    @NotNull @DecimalMin("0.01") BigDecimal precio,
    @NotNull @Min(0) Integer cantidadInicial,
    @NotNull @Min(0) Integer umbralBajo) {}
