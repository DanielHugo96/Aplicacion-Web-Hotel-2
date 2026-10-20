package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;

/** Payload de GET /internal/habitaciones/{id} de hotel. */
public record HabitacionInterna(
    Integer idHabitacion,
    Integer idHotel,
    String numero,
    String detalle,
    Integer idCategoria,
    String categoriaNombre,
    String pisoNombre,
    BigDecimal precio,
    String estadoFisico,
    Boolean estado,
    Boolean hotelActivo,
    String hotelNombre,
    Long version) {}
