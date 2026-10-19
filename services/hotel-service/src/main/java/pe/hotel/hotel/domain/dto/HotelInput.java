package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Entrada de crear/editar sede (HotelInput del contrato). */
public record HotelInput(
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$")
    @Size(max = 80)
    String slug,

    @NotBlank
    @Size(max = 120)
    String nombre,

    @NotBlank
    @Size(max = 300)
    String descripcion,

    @NotBlank
    @Size(max = 200)
    String direccion,

    @NotBlank
    @Size(max = 80)
    String ciudad,

    @NotBlank
    @Size(max = 20)
    String telefono,

    @NotBlank
    @Email
    @Size(max = 254)
    String correoContacto,

    Boolean publicado,

    Boolean estado) {}
