package pe.hotel.sales.domain.dto;

import java.util.UUID;

/** Respuesta de admisión de reception. */
public record OperacionEstado(UUID operationId, String tipo, String estado) {}
