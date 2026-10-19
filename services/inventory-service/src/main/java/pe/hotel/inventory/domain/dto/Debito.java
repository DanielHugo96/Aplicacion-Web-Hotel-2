package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Débito solicitado por sales. Sin precios, nombres, total ni recepción.
 * Ver docs/contracts/openapi/inventory.yaml schema Debito.
 */
public record Debito(
    @NotNull @Positive Integer idVenta,
    @NotNull @Positive Integer idHotel,
    @NotNull @NotEmpty @Size(min = 1, max = 50) List<DebitoItem> items) {}
