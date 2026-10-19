package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Ítem de un débito de stock.
 * Ver docs/contracts/openapi/inventory.yaml schema DebitoItem.
 */
public record DebitoItem(
    @NotNull @Positive Integer idProducto,
    @NotNull @Min(1) @Max(9999) Integer cantidad) {}
