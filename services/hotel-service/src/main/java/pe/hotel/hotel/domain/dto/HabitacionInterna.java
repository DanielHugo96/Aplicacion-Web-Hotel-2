package pe.hotel.hotel.domain.dto;

import java.math.BigDecimal;

/** Payload de la ruta interna GET /internal/habitaciones/{id} (para admission de operaciones). */
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
