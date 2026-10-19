package pe.hotel.hotel.domain.dto;

/** Vista de estado fisico del catalogo (EstadoFisico del contrato). */
public record EstadoFisicoView(
    Integer idEstadoHabitacion, String codigo, String descripcion, Boolean estado, Long version) {}
