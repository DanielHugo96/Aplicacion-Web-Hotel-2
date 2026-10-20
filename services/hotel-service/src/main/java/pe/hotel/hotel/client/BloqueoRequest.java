package pe.hotel.hotel.client;

import java.util.UUID;

/** Body de POST /internal/habitaciones/{id}/bloqueos (reception). */
public record BloqueoRequest(UUID blockId, Integer idHotel, String motivo) {}
