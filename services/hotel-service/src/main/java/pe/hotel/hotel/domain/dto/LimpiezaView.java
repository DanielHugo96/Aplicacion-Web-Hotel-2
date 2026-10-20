package pe.hotel.hotel.domain.dto;

import java.time.Instant;
import java.util.UUID;

/** Limpieza del contrato: version es el If-Match de la tarea, no la versión de la habitación. */
public record LimpiezaView(
    UUID cleaningCycleId,
    Integer idHabitacion,
    Integer idRecepcion,
    String estado,
    Long roomVersion,
    Long version,
    Instant createdAt,
    Instant completedAt) {}
