package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado autoritativo del débito de stock con precios y nombres congelados.
 * Ver docs/contracts/openapi/inventory.yaml schema DebitoResultado.
 */
public record DebitoResultado(
    @NotNull @Positive Integer idVenta,
    @NotNull @Positive Integer idHotel,
    @NotNull String estado,
    @NotNull String moneda,
    @NotNull @DecimalMin("0.01") BigDecimal total,
    @NotNull List<DebitoResultadoItem> items,
    @NotNull @Positive Integer version) {}
