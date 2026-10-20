package pe.hotel.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla cierre_consumos. */
public record CierreRow(
    UUID closureId,
    Integer idRecepcion,
    Integer idHotel,
    String requestHash,
    String receipt,
    Instant confirmedAt) {}
