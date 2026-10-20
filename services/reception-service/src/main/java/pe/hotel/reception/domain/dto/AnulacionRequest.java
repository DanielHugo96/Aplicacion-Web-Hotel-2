package pe.hotel.reception.domain.dto;

/** Cuerpo del POST /api/recepcion/{id}/anular-checkin: motivo 1..300. */
public record AnulacionRequest(String motivo) {}
