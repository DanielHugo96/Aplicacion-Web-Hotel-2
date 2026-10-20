package pe.hotel.sales.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Línea pedida en el alta: sin precios, subtotales ni nombre de producto. */
public record VentaDetalleAlta(
    @NotNull @Positive Integer idProducto,
    @NotNull @Min(1) @Max(9999) Integer cantidad) {}
