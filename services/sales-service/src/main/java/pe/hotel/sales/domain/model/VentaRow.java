package pe.hotel.sales.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla venta. */
public record VentaRow(
    Integer id,
    Integer idRecepcion,
    Integer idCliente,
    Integer idHotel,
    String estado,
    String estadoOperacion,
    boolean admitida,
    String origen,
    long inventoryVersion,
    BigDecimal total,
    String medioPago,
    String requestItems,
    UUID currentOperationId,
    String motivoCancelacion,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    Instant paidAt,
    UUID closureId) {}
