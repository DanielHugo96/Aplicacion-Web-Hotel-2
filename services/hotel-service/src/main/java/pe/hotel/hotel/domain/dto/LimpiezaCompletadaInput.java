package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body de PUT /api/habitacion/{id}/limpieza-completada. */
public record LimpiezaCompletadaInput(@NotNull(message = "obligatorio") UUID cleaningCycleId) {}
