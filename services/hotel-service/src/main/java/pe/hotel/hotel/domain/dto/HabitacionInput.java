package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Entrada de alta de habitacion (HabitacionInput del contrato). */
public record HabitacionInput(
    @NotNull Integer idHotel,

    @NotBlank
    @Size(max = 10)
    String numero,

    @NotNull Integer idPiso,

    @NotNull Integer idCategoria,

    @NotBlank
    @Size(max = 500)
    String detalle,

    @NotNull
    @DecimalMin(value = "0.01", inclusive = false)
    BigDecimal precio,

    Boolean publicada,

    @Size(max = 10) List<ImagenInput> urlsImagenes) {}
