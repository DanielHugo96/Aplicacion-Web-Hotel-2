package pe.hotel.hotel.domain.dto;

/** Payload de la ruta interna GET /internal/hoteles/{id} (para validar sedes activas). */
public record HotelInterno(Integer idHotel, String nombre, Boolean estado, Long version) {}
