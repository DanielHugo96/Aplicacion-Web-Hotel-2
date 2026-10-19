package pe.hotel.hotel.domain.dto;

/** Vista de piso enviada al cliente (Piso del contrato). */
public record PisoView(
    Integer idPiso, Integer idHotel, String descripcion, Boolean estado, Long version) {}
