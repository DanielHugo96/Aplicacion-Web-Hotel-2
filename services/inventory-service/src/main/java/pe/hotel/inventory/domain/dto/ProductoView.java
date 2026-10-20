package pe.hotel.inventory.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto en una sede (vista de salida). estado es el efectivo (producto AND producto_sede);
 * version es la de producto_sede (el V de If-Match); productoVersion la del catálogo global.
 * Ver docs/contracts/openapi/inventory.yaml schema Producto.
 */
public record ProductoView(
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
