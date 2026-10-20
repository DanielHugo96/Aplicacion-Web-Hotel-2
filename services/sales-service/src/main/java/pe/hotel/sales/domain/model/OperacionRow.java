package pe.hotel.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla operacion_venta. */
public record OperacionRow(
    UUID operationId,
    Integer idVenta,
    String tipo,
    String estado,
    String request,
    Integer actorId,
    String actorRol,
    Instant createdAt,
    Instant nextAttemptAt,
    int attempts) {}
