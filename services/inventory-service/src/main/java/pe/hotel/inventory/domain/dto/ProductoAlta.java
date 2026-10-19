package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de producto global con asignación inicial a la sede indicada.
 * Ver docs/contracts/openapi/inventory.yaml schema ProductoAlta.
 */
public record ProductoAlta(
    @NotBlank @Size(max = 40) String sku,
    @NotBlank @Size(max = 120) String nombre,
    @Size(max = 500) String detalle,
    @Size(max = 500) String imagenUrl,
    @NotNull Integer idHotel,
    @NotNull @DecimalMin("0.01") BigDecimal precio,
    @NotNull @Min(0) Integer cantidadInicial,
    @NotNull @Min(0) Integer umbralBajo) {}
