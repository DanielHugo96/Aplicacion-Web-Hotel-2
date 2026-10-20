package pe.hotel.sales.domain.dto;

import java.util.UUID;

/** Estado de la operación para polling; no expone el payload interno. */
public record OperacionVentaView(
    UUID operationId, Integer idVenta, String tipo, String estado, String estadoOperacion) {}
