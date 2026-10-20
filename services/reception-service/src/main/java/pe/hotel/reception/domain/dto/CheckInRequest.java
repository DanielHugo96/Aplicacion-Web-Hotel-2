package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body de POST /api/recepcion/registrar. JSON cerrado: los campos calculados o derivados se
 * declaran solo para poder rechazarlos con su nombre en {@code error.fields} (400), nunca se usan.
 */
public record CheckInRequest(
    Integer idHotel,
    Integer idCliente,
    Integer idHabitacion,
    LocalDate fechaSalida,
    BigDecimal adelanto,
    String medioPago,
    String observacion,
    LocalDate fechaEntrada,
    BigDecimal precioInicial,
    BigDecimal precioRestante,
    BigDecimal precioHabitacion,
    BigDecimal tarifa,
    BigDecimal totalPagado,
    String nombre,
    String apellido,
    String tipoDocumento,
    String documento,
    String correo,
    String estadoEstadia,
    Long version) {}
