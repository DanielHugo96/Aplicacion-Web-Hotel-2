package pe.hotel.sales.domain.dto;

/** Body cerrado de PUT /api/venta/{id}: solo registrar el pago. */
public record PagoRequest(String estado, String medioPago) {}
