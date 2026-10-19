package pe.hotel.hotel.domain.dto;

/** Vista publica de sede (HotelPublico del contrato, solo lectura). */
public record HotelPublico(
    Integer idHotel,
    String slug,
    String nombre,
    String descripcion,
    String direccion,
    String ciudad,
    String telefono,
    String correoContacto) {}
