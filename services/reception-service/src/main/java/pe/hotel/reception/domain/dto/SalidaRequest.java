package pe.hotel.reception.domain.dto;

import java.math.BigDecimal;

/** Cuerpo del POST /api/recepcion/registrar-salida (docs/contracts/openapi/reception.yaml § Salida). */
public record SalidaRequest(
    Integer idRecepcion,
    Integer idHabitacion,
    BigDecimal costoPenalidad,
    String motivoPenalidad,
    BigDecimal montoRecibido,
    String medioPago) {}
