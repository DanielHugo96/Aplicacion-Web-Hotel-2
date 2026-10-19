package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ajuste administrativo de stock con lock; el saldo resultante nunca queda negativo.
 * Ver docs/contracts/openapi/inventory.yaml schema AjusteRequest.
 */
public record AjusteRequest(
    @NotNull Integer idHotel,
    @NotNull Integer delta,
    @NotNull @Size(min = 1, max = 300) String motivo) {}
