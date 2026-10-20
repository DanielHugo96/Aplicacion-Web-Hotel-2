package pe.hotel.sales.domain.dto;

import java.util.UUID;

/** Body de POST /internal/recepciones/{id}/operaciones de reception. */
public record OperacionAdmisionRequest(
    UUID operationId,
    Integer idVenta,
    Integer idHotel,
    String tipo,
    Integer actorId,
    String actorRol) {}
