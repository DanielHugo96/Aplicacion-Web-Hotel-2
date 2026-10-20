package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Línea del carrito enriquecida con el precio y nombre autoritativos de inventory. */
public record CarritoItem(
    Integer idCarrito,
    Integer idRecepcion,
    Integer idPersona,
    Integer idHotel,
    Integer idProducto,
    String nombreProducto,
    BigDecimal precioUnitario,
    String imagenUrl,
    Integer cantidad,
    Instant fechaRegistro,
    Long version) {}
