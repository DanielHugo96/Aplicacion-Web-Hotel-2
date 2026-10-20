package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Venta completa del contrato (schema Venta de sales.yaml). */
public record VentaView(
    Integer idVenta,
    Integer idRecepcion,
    Integer idCliente,
    Integer idHotel,
    BigDecimal total,
    String estado,
    String estadoOperacion,
    UUID operationId,
    String medioPago,
    Long version,
    Instant fechaCreacion,
    Instant paidAt,
    List<VentaDetalleView> detalles) {}
