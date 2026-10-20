package pe.hotel.reception.domain.dto;

import java.util.UUID;

/** Respuesta 200 de admisión de operación. */
public record OperacionEstado(UUID operationId, String tipo, String estado) {}
