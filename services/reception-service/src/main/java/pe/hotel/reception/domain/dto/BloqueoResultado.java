package pe.hotel.reception.domain.dto;

import java.util.UUID;

/** Respuesta 200 del bloqueo administrativo. */
public record BloqueoResultado(UUID blockId, Integer idHabitacion, String estado) {}
