package pe.hotel.reception.domain.dto;

import java.util.UUID;

/** Body de POST /internal/habitaciones/{id}/bloqueos (hotel). */
public record BloqueoRequest(UUID blockId, Integer idHotel, String motivo) {}
