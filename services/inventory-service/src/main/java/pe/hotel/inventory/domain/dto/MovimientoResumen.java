package pe.hotel.inventory.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Desenlace del débito de una venta (consulta interna por idVenta).
 * Ver docs/contracts/openapi/inventory.yaml schema MovimientoResumen.
 */
public record MovimientoResumen(
    @NotNull @Positive Integer idVenta,
    @NotNull @Positive Integer idHotel,
    @NotNull String estado,
    Object resultado,
    @NotNull @Positive Integer version) {}
