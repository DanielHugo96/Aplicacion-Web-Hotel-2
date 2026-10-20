package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cotización informativa de salida (docs/contracts/openapi/reception.yaml § Cotizacion). Importes
 * HALF_UP por componente; el frontend no recalcula.
 */
public record Cotizacion(
    Integer idRecepcion,
    BigDecimal alojamiento,
    BigDecimal adelanto,
    BigDecimal penalidad,
    BigDecimal saldoAlojamiento,
    BigDecimal consumosPagados,
    BigDecimal consumosPendientes,
    BigDecimal cobroAhora,
    String moneda,
    Instant calculadoEn) {}
