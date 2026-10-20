package pe.hotel.hotel.domain.dto;

/** Respuesta de PUT /api/habitacion/{id}/limpieza-completada: {limpieza, habitacion}. */
public record LimpiezaResultadoView(LimpiezaView limpieza, HabitacionView habitacion) {}

