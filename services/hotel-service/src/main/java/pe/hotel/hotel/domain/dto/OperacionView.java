package pe.hotel.hotel.domain.dto;

import java.util.UUID;

/** OperacionRef/OperacionHabitacion del contrato: polling de una operación administrativa. */
public record OperacionView(UUID blockId, Integer idHabitacion, String tipo, String estado) {}
