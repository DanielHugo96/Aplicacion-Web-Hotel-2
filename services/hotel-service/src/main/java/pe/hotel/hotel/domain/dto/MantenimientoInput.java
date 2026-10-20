package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de POST /api/habitacion/{id}/mantenimiento (motivo 1..300). */
public record MantenimientoInput(
    @NotBlank(message = "obligatorio") @Size(max = 300, message = "máximo 300 caracteres") String motivo) {}
