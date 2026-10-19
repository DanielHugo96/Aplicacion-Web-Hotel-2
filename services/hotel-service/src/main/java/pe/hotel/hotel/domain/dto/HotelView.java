package pe.hotel.hotel.domain.dto;

/** Vista de sede enviada al cliente (Hotel del contrato). */
public record HotelView(
    Integer idHotel,
    String slug,
    String nombre,
    String descripcion,
    String direccion,
    String ciudad,
    String telefono,
    String correoContacto,
    Boolean estado,
    Boolean publicado,
    Long version) {}
