package pe.hotel.sales.domain.dto;

import java.util.List;

/** Débito solicitado a inventory: sin precios, nombres, total ni recepción. */
public record DebitoRequest(Integer idVenta, Integer idHotel, List<DebitoItem> items) {}
