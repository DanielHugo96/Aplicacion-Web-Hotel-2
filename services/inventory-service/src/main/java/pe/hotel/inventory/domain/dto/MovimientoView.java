package pe.hotel.inventory.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Movimiento de producto (unión discriminada: VENTA o AJUSTE) para el listado por producto.
 * Ver docs/contracts/openapi/inventory.yaml schemas MovimientoVenta / MovimientoAjuste.
 */
public record MovimientoView(
    String tipo, // VENTA | AJUSTE
    Integer idVenta,
    Integer idHotel,
    String estado, // DESCONTADO | RECHAZADO | RESTITUIDO
    Integer cantidad,
    BigDecimal precioUnitario,
    UUID ajusteId,
    Integer delta,
    String motivo,
    Instant createdAt,
    Instant updatedAt) {}
