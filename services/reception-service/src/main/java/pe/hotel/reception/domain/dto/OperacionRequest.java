package pe.hotel.reception.domain.dto;

import java.util.UUID;

/** Body de POST /internal/recepciones/{id}/operaciones (sales). */
public record OperacionRequest(
    UUID operationId,
    Integer idVenta,
    Integer idHotel,
    String tipo,
    Integer actorId,
    String actorRol) {}
