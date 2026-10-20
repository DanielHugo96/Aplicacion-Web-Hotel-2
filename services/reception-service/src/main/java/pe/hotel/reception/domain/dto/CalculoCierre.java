package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;

/**
 * Cálculo congelado del cierre (docs/especificacion/microservicios/reception/base-de-datos.md
 * § Esquemas JSONB). Todos los importes numeric(12,2) HALF_UP; no se recalcula en CONFIRMANDO.
 */
public record CalculoCierre(
    BigDecimal alojamientoTotal,
    BigDecimal saldoAlojamiento,
    BigDecimal consumosPagadosAntes,
    BigDecimal consumosPendientes,
    BigDecimal consumosTotal,
    BigDecimal cobroAhora,
    BigDecimal totalGeneral,
    BigDecimal expectedPendingTotal) {}
