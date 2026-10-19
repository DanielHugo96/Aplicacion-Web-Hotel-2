package pe.hotel.hotel.domain.dto;

/** Vista de categoria enviada al cliente (Categoria del contrato). */
public record CategoriaView(
    Integer idCategoria,
    String descripcion,
    String detalle,
    Integer capacidad,
    Boolean estado,
    Long version) {}
