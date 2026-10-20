package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Ítem del resultado autoritativo de un débito de stock.
 * Ver docs/contracts/openapi/inventory.yaml schema DebitoResultadoItem.
 */
public record DebitoResultadoItem(
    @NotNull @Positive Integer idProducto,
    @NotNull @Size(max = 120) String nombreProducto,
    @NotNull @Min(1) @Max(9999) Integer cantidad,
    @NotNull @DecimalMin("0.01") BigDecimal precioUnitario,
    @NotNull @DecimalMin("0.01") BigDecimal subTotal) {}
