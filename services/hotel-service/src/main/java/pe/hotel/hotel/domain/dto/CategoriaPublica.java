package pe.hotel.hotel.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/** Vista publica de categoria (CategoriaPublica del contrato, solo lectura). */
public record CategoriaPublica(
    Integer idCategoria,
    String descripcion,
    String detalle,
    Integer capacidad,
    BigDecimal precioDesde,
    String moneda,
    List<ImagenPublica> imagenes) {}
