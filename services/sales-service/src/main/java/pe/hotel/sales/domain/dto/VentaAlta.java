package pe.hotel.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alta de venta; JSON cerrado (fail-on-unknown-properties). idHotel e idCliente se derivan de la
 * recepción y los precios son autoritativos de inventory.
 */
public record VentaAlta(
    @NotNull @Positive Integer idRecepcion,
    String estado,
    String medioPago,
    @NotNull @NotEmpty @Size(min = 1, max = 50) List<@Valid VentaDetalleAlta> detalles) {}
