package pe.hotel.hotel.domain.dto;

import java.math.BigDecimal;

/** Vista de habitacion enviada al cliente (Habitacion del contrato). */
public record HabitacionView(
    Integer idHabitacion,
    Integer idHotel,
    String numero,
    Integer idPiso,
    String pisoNombre,
    Integer idCategoria,
    String categoriaNombre,
    String detalle,
    BigDecimal precio,
    Boolean publicada,
    Boolean estado,
    Integer idEstadoHabitacion,
    EstadoFisicoView estadoFisico,
    OcupacionView ocupacion,
    String blockId,
    String endpoint,
    Long version) {}
