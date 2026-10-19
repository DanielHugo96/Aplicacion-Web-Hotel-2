package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada de crear/editar categoria (CategoriaInput del contrato). */
public record CategoriaInput(
    @NotBlank
    @Size(max = 80)
    String descripcion,

    @Size(max = 500)
    String detalle,

    Integer capacidad,

    Boolean estado) {}
