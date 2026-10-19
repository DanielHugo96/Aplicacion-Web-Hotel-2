package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada de crear/editar piso (PisoInput del contrato). */
public record PisoInput(
    Integer idHotel,

    @NotBlank
    @Size(max = 80)
    String descripcion,

    Boolean estado) {}
