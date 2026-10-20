package pe.hotel.sales.domain.dto;

/** Resumen de admisión de reception (GET /internal/recepciones/{id}). */
public record RecepcionResumen(
    Integer idRecepcion,
    Integer idHotel,
    Integer idCliente,
    Integer idHabitacion,
    String estadoEstadia,
    Long version) {}
