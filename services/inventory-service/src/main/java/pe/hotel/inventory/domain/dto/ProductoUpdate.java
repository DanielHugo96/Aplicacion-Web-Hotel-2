package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Actualización de producto/sede. nombre/detalle/imagenUrl son globales;
 * precio/umbralBajo/estado pertenecen a la sede indicada en idHotel (query).
 * No permite cantidad. productoVersion es obligatorio.
 * Ver docs/contracts/openapi/inventory.yaml schema ProductoUpdate.
 */
public record ProductoUpdate(
    @Size(max = 120) String nombre,
    @Size(max = 500) String detalle,
    @Size(max = 500) String imagenUrl,
    @DecimalMin("0.01") BigDecimal precio,
    @Min(0) Integer umbralBajo,
    Boolean estado,
    @NotNull @Min(1) Integer productoVersion) {}
