package pe.hotel.sales.domain.model;

import java.time.Instant;

/** Fila de la tabla carrito. */
public record CarritoRow(
    Integer id,
    Integer idRecepcion,
    Integer idCliente,
    Integer idHotel,
    Integer idProducto,
    Integer cantidad,
    Instant createdAt,
    Long version) {}
