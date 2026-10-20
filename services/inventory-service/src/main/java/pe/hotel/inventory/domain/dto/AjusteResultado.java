package pe.hotel.inventory.domain.dto;

import java.util.UUID;

/**
 * Resultado de un ajuste de stock registrado.
 * Ver docs/contracts/openapi/inventory.yaml schema AjusteResultado.
 */
public record AjusteResultado(UUID ajusteId, Integer cantidad, Integer version) {}
