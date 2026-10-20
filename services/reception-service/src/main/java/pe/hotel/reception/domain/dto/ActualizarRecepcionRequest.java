package pe.hotel.reception.domain.dto;

import java.time.LocalDate;

/** Body de PUT /api/recepcion/actualizar/{id}: solo fechaSalida y observación opcional. */
public record ActualizarRecepcionRequest(LocalDate fechaSalida, String observacion) {}
