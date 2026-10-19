package pe.hotel.hotel.domain.dto;

/** Vista informativa de ocupacion (ocupacion de Habitacion del contrato). */
public record OcupacionView(
    String estadoEstadia, Integer idRecepcion, java.time.OffsetDateTime updatedAt) {}
