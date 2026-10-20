package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto de la sede tal como lo publica el catálogo interno de inventory
 * (/internal/productos). El precio es el autoritativo; sales nunca lo recalcula.
 */
public record ProductoStock(
    Integer idProducto,
    String sku,
    String nombre,
    String detalle,
    String imagenUrl,
    Integer idHotel,
    BigDecimal precio,
    Integer cantidad,
    Integer umbralBajo,
    Boolean estado,
    Instant fechaCreacion,
    Integer version,
    Integer productoVersion) {}
