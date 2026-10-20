package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /api/estadohabitacion/actualizar/{id}}: solo la etiqueta descripcion. */
public record EstadoHabitacionUpdate(
    @NotBlank(message = "descripcion es obligatoria")
    @Size(max = 80, message = "descripcion no puede superar 80 caracteres")
    String descripcion) {}
