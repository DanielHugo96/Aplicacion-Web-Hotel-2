package pe.hotel.hotel.client;

import java.util.UUID;

/** Respuesta 200 de POST /internal/habitaciones/{id}/bloqueos. */
public record BloqueoResultado(UUID blockId, Integer idHabitacion, String estado) {}
