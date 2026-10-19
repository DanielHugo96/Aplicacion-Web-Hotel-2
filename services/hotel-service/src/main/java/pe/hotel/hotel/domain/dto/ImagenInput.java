package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada de una imagen de la galería (ImagenHabitacion del contrato). */
public record ImagenInput(
    @NotBlank
    @Size(max = 500)
    String url,

    @NotBlank
    @Size(max = 150)
    String alt,

    Integer orden) {}
