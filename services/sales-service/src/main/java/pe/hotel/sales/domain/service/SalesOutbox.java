package pe.hotel.sales.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pe.hotel.sales.domain.model.DetalleRow;
import pe.hotel.sales.domain.model.VentaRow;
import pe.hotel.sales.messaging.Destination;
import pe.hotel.sales.messaging.OutboxStore;

/**
 * Emite por outbox el snapshot de venta y los comandos durables de sales en la misma transacción
 * local que la mutación terminal. La plataforma (OutboxPoller + publishers) publica y reintenta.
 */
@Service
public class SalesOutbox {

  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

  private final ObjectMapper mapper = new ObjectMapper();
  private final ObjectProvider<OutboxStore> outboxProvider;

  public SalesOutbox(ObjectProvider<OutboxStore> outboxProvider) {
    this.outboxProvider = outboxProvider;
  }

  /** {@code sales.venta.v1} con clave de venta y versión de venta. */
  public void emitirVenta(VentaRow venta, List<DetalleRow> detalles) {
    OutboxStore outbox = outboxProvider.getIfAvailable();
    if (outbox == null) {
      return;
    }
    try {
      List<Map<String, Object>> lineas = new ArrayList<>();
      for (DetalleRow detalle : detalles) {
        Map<String, Object> linea = new LinkedHashMap<>();
        linea.put("idProducto", detalle.idProducto());
        linea.put("nombreProducto", detalle.nombreProducto());
        linea.put("cantidad", detalle.cantidad());
        linea.put("precioUnitario", detalle.precioUnitario());
        linea.put("subTotal", detalle.subTotal());
        lineas.add(linea);
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idVenta", venta.id());
      payload.put("idRecepcion", venta.idRecepcion());
      payload.put("idCliente", venta.idCliente());
      payload.put("idHotel", venta.idHotel());
      payload.put("estado", venta.estado());
      payload.put("estadoOperacion", venta.estadoOperacion());
      payload.put("total", venta.total());
      payload.put("medioPago", venta.medioPago());
      payload.put("createdAt", TS.format(venta.createdAt()));
      payload.put("paidAt", venta.paidAt() == null ? null : TS.format(venta.paidAt()));
      payload.put("closureId", venta.closureId() == null ? null : venta.closureId().toString());
      payload.put("version", venta.version());
      payload.put("detalles", lineas);

      String eventId = UUID.randomUUID().toString();
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", eventId);
      envelope.put("type", "VentaSnapshot");
      envelope.put("schemaVersion", 1);
      envelope.put("aggregateType", "venta");
      envelope.put("aggregateId", String.valueOf(venta.id()));
      envelope.put("aggregateVersion", venta.version());
      envelope.put("idHotel", venta.idHotel());
      envelope.put("occurredAt", TS.format(Instant.now()));
      envelope.put("correlationId", eventId);
      envelope.put("payload", payload);
      outbox.enqueue(
          Destination.KAFKA,
          "sales.venta.v1",
          String.valueOf(venta.id()),
          String.valueOf(venta.id()),
          venta.version(),
          "VentaSnapshot",
          mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar VentaSnapshot", ex);
    }
  }

  /** {@code recepcion.operacion-finalizar} tras persistir el terminal de la operación. */
  public void emitirFinalizar(
      UUID operationId,
      int idHotel,
      int idRecepcion,
      int idVenta,
      String tipo,
      String resultado,
      long aggregateVersion) {
    OutboxStore outbox = outboxProvider.getIfAvailable();
    if (outbox == null) {
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("operationId", operationId.toString());
      payload.put("idRecepcion", idRecepcion);
      payload.put("idVenta", idVenta);
      payload.put("tipo", tipo);
      payload.put("resultado", resultado);
      String commandId = UUID.randomUUID().toString();
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("commandId", commandId);
      envelope.put("type", "RecepcionOperacionFinalizar");
      envelope.put("schemaVersion", 1);
      envelope.put("idHotel", idHotel);
      envelope.put("correlationId", commandId);
      envelope.put("issuedAt", TS.format(Instant.now()));
      envelope.put("payload", payload);
      outbox.enqueue(
          Destination.RABBIT,
          "recepcion.operacion-finalizar",
          String.valueOf(idVenta),
          String.valueOf(idVenta),
          aggregateVersion,
          "RecepcionOperacionFinalizar",
          mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar RecepcionOperacionFinalizar", ex);
    }
  }

  /** {@code stock.restituir} durable al anular; nunca por HTTP directo. */
  public void emitirRestituir(
      int idVenta, int idRecepcion, String motivo, int idHotel, long aggregateVersion) {
    OutboxStore outbox = outboxProvider.getIfAvailable();
    if (outbox == null) {
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idVenta", idVenta);
      payload.put("idRecepcion", idRecepcion);
      payload.put("motivo", motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
      String commandId = UUID.randomUUID().toString();
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("commandId", commandId);
      envelope.put("type", "StockRestituir");
      envelope.put("schemaVersion", 1);
      envelope.put("idHotel", idHotel);
      envelope.put("correlationId", commandId);
      envelope.put("issuedAt", TS.format(Instant.now()));
      envelope.put("payload", payload);
      outbox.enqueue(
          Destination.RABBIT,
          "stock.restituir",
          String.valueOf(idVenta),
          String.valueOf(idVenta),
          aggregateVersion,
          "StockRestituir",
          mapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar StockRestituir", ex);
    }
  }
}
