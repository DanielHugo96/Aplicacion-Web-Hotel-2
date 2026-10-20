package pe.hotel.reception.domain.dto;

/** Resumen mínimo de admisión (GET /internal/recepciones/{id}); sin documento ni correo. */
public record ResumenRecepcion(
    Integer idRecepcion,
    Integer idHotel,
    Integer idCliente,
    Integer idHabitacion,
    String estadoEstadia,
    Long version) {}
