package pe.hotel.hotel.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.hotel.domain.model.Habitacion;
import pe.hotel.hotel.domain.model.Hotel;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.LimpiezaRepository;
import pe.hotel.hotel.messaging.Destination;
import pe.hotel.hotel.messaging.OutboxStore;
import pe.hotel.hotel.platform.CorrelationId;

/**
 * Emite eventos de dominio al {@code outbox} en la misma transacción local de la mutación de
 * catálogo. La plataforma (OutboxPoller + KafkaOutboxPublisher) se encarga de publicarlos al broker.
 */
@Service
public class OutboxService {

  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired(required = false)
  private OutboxStore outboxStore;
  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;

  @Transactional
  public void emitirSedeSnapshot(Hotel hotel) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idHotel", hotel.getId());
      payload.put("nombre", hotel.getNombre());
      payload.put("ciudad", hotel.getCiudad());
      payload.put("estado", hotel.getEstado());
      payload.put("publicado", hotel.getPublicado());
      payload.put("version", hotel.getVersion());

      Map<String, Object> envelope = sobre("SedeSnapshot", "sede", hotel.getId(), hotel.getVersion(), payload);
      outboxStore.enqueue(
          Destination.KAFKA,
          "hotel.sede.v1",
          String.valueOf(hotel.getId()),
          String.valueOf(hotel.getId()),
          hotel.getVersion(),
          "SedeSnapshot",
          MAPPER.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar SedeSnapshot: " + ex.getMessage(), ex);
    }
  }

  @Transactional
  public void emitirHabitacionCreada(
      Habitacion h, String categoriaNombre, String pisoNombre, String estadoFisicoCodigo) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idHabitacion", h.getId());
      payload.put("idHotel", h.getIdHotel());
      payload.put("numero", h.getNumero());
      payload.put("idCategoria", h.getIdCategoria());
      payload.put("categoriaNombre", categoriaNombre);
      payload.put("pisoNombre", pisoNombre);
      payload.put("precio", h.getPrecio());
      payload.put("estado", h.getEstado());
      payload.put("estadoFisico", estadoFisicoCodigo);
      payload.put("version", h.getVersion());
      payload.put("cleaningCycleId", null);
      payload.put("cleaningRoomVersion", null);
      payload.put("blockId", null);

      Map<String, Object> envelope =
          sobre("HabitacionCreada", "habitacion", h.getIdHotel(), h.getVersion(), payload);
      outboxStore.enqueue(
          Destination.KAFKA,
          "hotel.habitacion.v1",
          String.valueOf(h.getId()),
          String.valueOf(h.getId()),
          h.getVersion(),
          "HabitacionCreada",
          MAPPER.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar HabitacionCreada: " + ex.getMessage(), ex);
    }
  }

  @Transactional
  public void emitirHabitacionSnapshot(
      Habitacion h, String categoriaNombre, String pisoNombre, String estadoFisicoCodigo) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("idHabitacion", h.getId());
      payload.put("idHotel", h.getIdHotel());
      payload.put("numero", h.getNumero());
      payload.put("idCategoria", h.getIdCategoria());
      payload.put("categoriaNombre", categoriaNombre);
      payload.put("pisoNombre", pisoNombre);
      payload.put("precio", h.getPrecio());
      payload.put("estado", h.getEstado());
      payload.put("estadoFisico", estadoFisicoCodigo);
      payload.put("version", h.getVersion());
      payload.put("cleaningCycleId", null);
      payload.put("cleaningRoomVersion", null);
      payload.put("blockId", null);

      Map<String, Object> envelope =
          sobre("HabitacionSnapshot", "habitacion", h.getIdHotel(), h.getVersion(), payload);
      outboxStore.enqueue(
          Destination.KAFKA,
          "hotel.habitacion.v1",
          String.valueOf(h.getId()),
          String.valueOf(h.getId()),
          h.getVersion(),
          "HabitacionSnapshot",
          MAPPER.writeValueAsString(envelope));
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar HabitacionSnapshot: " + ex.getMessage(), ex);
    }
  }

  /**
   * {@code HabitacionLista} al completar una limpieza: lleva el ciclo y el
   * {@code cleaningRoomVersion} de la tarea (nunca la version de la habitación).
   */
  @Transactional
  public void emitirHabitacionLista(
      Habitacion h, String categoriaNombre, String pisoNombre, LimpiezaRepository.Limpieza limpieza) {
    try {
      Map<String, Object> payload = payload(h, categoriaNombre, pisoNombre);
      payload.put("cleaningCycleId", limpieza.cleaningCycleId());
      payload.put("cleaningRoomVersion", limpieza.roomVersion());
      payload.put("blockId", null);
      Map<String, Object> envelope =
          sobreHabitacion("HabitacionLista", h, payload);
      enqueueHabitacion(h, "HabitacionLista", envelope);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar HabitacionLista: " + ex.getMessage(), ex);
    }
  }

  /** {@code HabitacionHabilitada} al liberar un bloqueo: exige el {@code blockId}. */
  @Transactional
  public void emitirHabitacionHabilitada(
      Habitacion h, String categoriaNombre, String pisoNombre, UUID blockId) {
    try {
      Map<String, Object> payload = payload(h, categoriaNombre, pisoNombre);
      payload.put("cleaningCycleId", null);
      payload.put("cleaningRoomVersion", null);
      payload.put("blockId", blockId);
      Map<String, Object> envelope = sobreHabitacion("HabitacionHabilitada", h, payload);
      enqueueHabitacion(h, "HabitacionHabilitada", envelope);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar HabitacionHabilitada: " + ex.getMessage(), ex);
    }
  }

  /** {@code HabitacionSnapshot} completo con estado=false al aplicar una baja. */
  @Transactional
  public void emitirHabitacionBaja(
      Habitacion h, String categoriaNombre, String pisoNombre, UUID blockId) {
    try {
      Map<String, Object> payload = payload(h, categoriaNombre, pisoNombre);
      payload.put("estado", false);
      payload.put("cleaningCycleId", null);
      payload.put("cleaningRoomVersion", null);
      payload.put("blockId", blockId);
      Map<String, Object> envelope = sobreHabitacion("HabitacionSnapshot", h, payload);
      enqueueHabitacion(h, "HabitacionSnapshot", envelope);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar HabitacionSnapshot: " + ex.getMessage(), ex);
    }
  }

  private Map<String, Object> payload(
      Habitacion h, String categoriaNombre, String pisoNombre) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("idHabitacion", h.getId());
    payload.put("idHotel", h.getIdHotel());
    payload.put("numero", h.getNumero());
    payload.put("idCategoria", h.getIdCategoria());
    payload.put("categoriaNombre", categoriaNombre);
    payload.put("pisoNombre", pisoNombre);
    payload.put("precio", h.getPrecio());
    payload.put("estado", h.getEstado());
    payload.put(
        "estadoFisico",
        h.getEstadoHabitacion() == null ? null : h.getEstadoHabitacion().getCodigo());
    payload.put("version", h.getVersion());
    return payload;
  }

  private Map<String, Object> sobreHabitacion(
      String type, Habitacion h, Map<String, Object> payload) {
    String eventId = UUID.randomUUID().toString();
    String correlation =
        CorrelationId.current() == null || CorrelationId.current().isBlank()
            ? UUID.randomUUID().toString()
            : CorrelationId.current();
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", eventId);
    envelope.put("type", type);
    envelope.put("schemaVersion", 1);
    envelope.put("aggregateType", "habitacion");
    envelope.put("aggregateId", String.valueOf(h.getId()));
    envelope.put("aggregateVersion", h.getVersion());
    envelope.put("idHotel", h.getIdHotel());
    envelope.put("occurredAt", TS.format(Instant.now()));
    envelope.put("correlationId", correlation);
    envelope.put("payload", payload);
    return envelope;
  }

  private void enqueueHabitacion(
      Habitacion h, String type, Map<String, Object> envelope) throws Exception {
    outboxStore.enqueue(
        Destination.KAFKA,
        "hotel.habitacion.v1",
        String.valueOf(h.getId()),
        String.valueOf(h.getId()),
        h.getVersion(),
        type,
        MAPPER.writeValueAsString(envelope));
  }

  @Transactional
  public void emitirCategoriaCambio(Integer idCategoria) {
    List<Habitacion> afectadas = habitacionRepository.findByIdCategoriaAndEstadoTrueOrderByIdAsc(idCategoria);
    for (Habitacion h : afectadas) {
      h.setVersion(h.getVersion() + 1);
      habitacionRepository.save(h);
      emitirHabitacionSnapshot(
          h,
          h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
          h.getPiso() == null ? null : h.getPiso().getDescripcion(),
          h.getEstadoHabitacion() == null ? null : h.getEstadoHabitacion().getCodigo());
    }
  }

  @Transactional
  public void emitirPisoCambio(Integer idHotel, Integer idPiso) {
    List<Habitacion> afectadas = habitacionRepository.findByIdPisoAndIdHotelAndEstadoTrueOrderByIdAsc(idPiso, idHotel);
    for (Habitacion h : afectadas) {
      h.setVersion(h.getVersion() + 1);
      habitacionRepository.save(h);
      emitirHabitacionSnapshot(
          h,
          h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
          h.getPiso() == null ? null : h.getPiso().getDescripcion(),
          h.getEstadoHabitacion() == null ? null : h.getEstadoHabitacion().getCodigo());
    }
  }

  private Map<String, Object> sobre(
      String type,
      String aggregateType,
      Integer idHotel,
      Long aggregateVersion,
      Map<String, Object> payload) {
    String eventId = UUID.randomUUID().toString();
    String now = TS.format(Instant.now());
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", eventId);
    envelope.put("type", type);
    envelope.put("schemaVersion", 1);
    envelope.put("aggregateType", aggregateType);
    envelope.put("aggregateId", String.valueOf(idHotel == null ? 0 : idHotel));
    envelope.put("aggregateVersion", aggregateVersion == null ? 0 : aggregateVersion);
    envelope.put("idHotel", idHotel);
    envelope.put("occurredAt", now);
    envelope.put("correlationId", eventId);
    envelope.put("payload", payload);
    return envelope;
  }

  public static String eventIdBootstrap(String aggregateType, Integer aggregateId) {
    return UUID.nameUUIDFromBytes(
            ("bootstrap:" + aggregateType + ":" + aggregateId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }
}
