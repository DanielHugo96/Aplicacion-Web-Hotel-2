package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.messaging.Destination;
import pe.hotel.reception.messaging.OutboxStore;
import pe.hotel.reception.platform.CorrelationId;

/**
 * Salidas del servicio por outbox (docs/especificacion/05-mensajeria.md): una fila por destino en la
 * misma transacción del cambio local. Sin efectos remotos desde el procedimiento.
 */
@Service
public class ReceptionOutbox {

  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  private final OutboxStore outbox;
  private final ObjectMapper mapper;

  public ReceptionOutbox(OutboxStore outbox, ObjectMapper mapper) {
    this.outbox = outbox;
    this.mapper = mapper;
  }

  public void emitirCambio(RecepcionView view, long roomVersion, String type) {
    try {
      Map<String, Object> payload = snapshot(view, roomVersion);
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventId", UUID.randomUUID().toString());
      envelope.put("type", type);
      envelope.put("schemaVersion", 1);
      envelope.put("aggregateType", "habitacion-ocupacion");
      envelope.put("aggregateId", String.valueOf(view.idHabitacion()));
      envelope.put("aggregateVersion", roomVersion);
      envelope.put("idHotel", view.idHotel());
      envelope.put("occurredAt", TS.format(Instant.now()));
      envelope.put("correlationId", correlation());
      envelope.put("payload", payload);
      outbox.enqueue(
          Destination.KAFKA,
          "reception.recepcion.v1",
          String.valueOf(view.idHabitacion()),
          String.valueOf(view.idHabitacion()),
          roomVersion,
          type,
          mapper.writeValueAsString(envelope));
    } catch (Exception e) {
      throw new IllegalStateException("no se pudo encolar " + type, e);
    }
  }

  public void emitirNotificacionCheckin(RecepcionView view, String hotel) {
    try {
      String nombre = ((view.nombre() == null ? "" : view.nombre()) + " "
              + (view.apellido() == null ? "" : view.apellido()))
          .trim();
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idPersona", view.idCliente());
      payload.put("idRecepcion", view.idRecepcion());
      payload.put("destinatario", view.correo());
      payload.put("nombre", nombre);
      payload.put("hotel", hotel);
      payload.put("fechaSalida", view.fechaSalida().toString());
      payload.put("templateVersion", 1);

      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("commandId", UUID.randomUUID().toString());
      envelope.put("type", "NotificacionCheckin");
      envelope.put("schemaVersion", 1);
      envelope.put("idHotel", view.idHotel());
      envelope.put("correlationId", correlation());
      envelope.put("issuedAt", TS.format(Instant.now()));
      envelope.put("payload", payload);
      outbox.enqueue(
          Destination.RABBIT,
          "notificacion.checkin",
          String.valueOf(view.idHabitacion()),
          String.valueOf(view.idHabitacion()),
          0,
          "NotificacionCheckin",
          mapper.writeValueAsString(envelope));
    } catch (Exception e) {
      throw new IllegalStateException("no se pudo encolar notificacion.checkin", e);
    }
  }

  private Map<String, Object> snapshot(RecepcionView view, long roomVersion) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("idRecepcion", view.idRecepcion());
    payload.put("idHotel", view.idHotel());
    payload.put("idHabitacion", view.idHabitacion());
    payload.put("idCliente", view.idCliente());
    payload.put("numero", view.numero());
    payload.put(
        "nombreCliente",
        ((view.nombre() == null ? "" : view.nombre()) + " "
                + (view.apellido() == null ? "" : view.apellido()))
            .trim());
    payload.put("fechaEntrada", view.fechaEntrada().toString());
    payload.put("fechaSalida", view.fechaSalida().toString());
    payload.put("fechaSalidaConfirmacion", null);
    payload.put("estadoEstadia", view.estadoEstadia());
    payload.put("tarifa", view.precioHabitacion());
    payload.put("precioInicial", view.precioInicial());
    payload.put("adelanto", view.adelanto());
    payload.put("medioPagoAdelanto", view.medioPagoAdelanto());
    payload.put("costoPenalidad", view.costoPenalidad());
    payload.put("motivoPenalidad", view.motivoPenalidad());
    payload.put("totalPagado", view.totalPagado());
    payload.put("observacion", null);
    payload.put("archivada", view.archivada());
    payload.put("version", view.version());
    payload.put("roomVersion", roomVersion);
    payload.put("closureId", null);
    payload.put("cleaningCycleId", null);
    payload.put("cierre", null);
    Map<String, Object> ocupacion = new LinkedHashMap<>();
    ocupacion.put("idRecepcion", view.idRecepcion());
    ocupacion.put("estadoGate", "OCUPADA");
    ocupacion.put("cleaningCycleId", null);
    ocupacion.put("blockId", null);
    payload.put("ocupacionActual", ocupacion);
    return payload;
  }

  private String correlation() {
    String current = CorrelationId.current();
    return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
  }
}
