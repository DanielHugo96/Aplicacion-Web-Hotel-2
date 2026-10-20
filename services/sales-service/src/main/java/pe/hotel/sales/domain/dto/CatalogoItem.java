package pe.hotel.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Catálogo privado de la sede de la estadía (schema CatalogoItem de sales.yaml). */
public record CatalogoItem(
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
