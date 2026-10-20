package pe.hotel.hotel.domain.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.dto.LimpiezaResultadoView;
import pe.hotel.hotel.domain.dto.LimpiezaView;
import pe.hotel.hotel.domain.model.Habitacion;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.LimpiezaRepository;
import pe.hotel.hotel.domain.repository.OperacionHabitacionRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.messaging.InboxStore;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;
import pe.hotel.hotel.platform.Pagination;

/**
 * Ciclo de limpieza: listado por sede, completado con If-Match de la tarea y efecto del comando
 * {@code limpieza.solicitada}. La comparación de ciclos y el orden de locks están en
 * V5__hotel_operaciones.sql.
 */
@Service
public class LimpiezaService {

  @Autowired(required = false)
  private LimpiezaRepository limpiezaRepository;
  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private OperacionHabitacionRepository operacionRepository;
  @Autowired(required = false)
  private HabitacionService habitacionService;
  @Autowired(required = false)
  private OutboxService outboxService;
  @Autowired(required = false)
  private InboxStore inboxStore;
  @Autowired(required = false)
  private CurrentUser currentUser;

  public record Listado(List<LimpiezaView> data, long totalElements) {}

  public Listado listar(Integer idHotel, String estado, int page, int size) {
    if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isEmpleado())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (idHotel == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("idHotel", "obligatorio"));
    }
    if (!currentUser.puedeAccederASede(idHotel)) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("idHotel", String.valueOf(idHotel)));
    }
    if (estado != null && !List.of("PENDIENTE", "COMPLETADA").contains(estado)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("estado", "PENDIENTE o COMPLETADA"));
    }
    Pagination.validar(page, size);
    List<LimpiezaView> data =
        limpiezaRepository.listar(idHotel, estado, page, size).stream().map(this::view).toList();
    return new Listado(data, limpiezaRepository.contar(idHotel, estado));
  }

  @Transactional
  public LimpiezaResultadoView completar(
      Integer idHabitacion, UUID cleaningCycleId, String ifMatch) {
    if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isEmpleado())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    LimpiezaRepository.Limpieza tarea =
        limpiezaRepository
            .find(cleaningCycleId)
            .filter(t -> t.idHabitacion().equals(idHabitacion))
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("cleaningCycleId", String.valueOf(cleaningCycleId))));
    if (!currentUser.puedeAccederASede(tarea.idHotel())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
    }
    long version = parseVersion(ifMatch);
    if (version != tarea.version()) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", "versión de la tarea"));
    }

    Map<String, Object> result =
        limpiezaRepository.completar(cleaningCycleId, idHabitacion, version);
    if (!Boolean.TRUE.equals(result.get("ok"))) {
      String code = String.valueOf(result.getOrDefault("code", "TASK_NOT_PENDING"));
      throw new ApiException(
          switch (code) {
            case "PRECONDITION_FAILED" -> ErrorCode.PRECONDITION_FAILED;
            case "RESOURCE_NOT_FOUND" -> ErrorCode.RESOURCE_NOT_FOUND;
            default -> ErrorCode.GATE_NOT_AVAILABLE;
          },
          Map.of("cleaningCycleId", String.valueOf(cleaningCycleId)));
    }

    LimpiezaRepository.Limpieza completada =
        limpiezaRepository
            .find(cleaningCycleId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
    Habitacion h =
        habitacionRepository
            .findById(idHabitacion)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
    outboxService.emitirHabitacionLista(
        h,
        h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
        h.getPiso() == null ? null : h.getPiso().getDescripcion(),
        completada);
    HabitacionView vista = habitacionService.vistaInterna(idHabitacion);
    return new LimpiezaResultadoView(view(completada), vista);
  }

  /**
   * Efecto del comando en la misma transacción que el inbox. Devuelve {@code false} si el inbox ya
   * lo había procesado. Las contradicciones permanentes las traduce el consumidor a DLQ.
   */
  @Transactional
  public ResultadoSolicitud solicitar(
      UUID commandId,
      int idHotel,
      int idHabitacion,
      int idRecepcion,
      UUID cleaningCycleId,
      long roomVersion) {
    Habitacion habitacion = habitacionRepository.findById(idHabitacion).orElse(null);
    if (habitacion == null || !habitacion.getIdHotel().equals(idHotel)) {
      return new ResultadoSolicitud(true, "TUPLE_MISMATCH", "idHotel o habitación contradictorios");
    }
    if (operacionRepository != null && operacionRepository.existsVigente(idHabitacion)) {
      return new ResultadoSolicitud(
          true, "ROOM_OPERATION_IN_PROGRESS", "la habitación tiene una operación vigente");
    }
    if (inboxStore != null && !inboxStore.claim("hotel-limpieza-solicitada-v1", commandId)) {
      return new ResultadoSolicitud(false, null, "duplicado");
    }
    Map<String, Object> result =
        limpiezaRepository.solicitar(cleaningCycleId, idHabitacion, idRecepcion, roomVersion);
    if (!Boolean.TRUE.equals(result.get("ok"))) {
      return new ResultadoSolicitud(
          true, String.valueOf(result.getOrDefault("code", "TUPLE_MISMATCH")), null);
    }
    return new ResultadoSolicitud(true, null, null);
  }

  public record ResultadoSolicitud(boolean aplicado, String code, String detalle) {}

  private Integer hotelDe(Integer idHabitacion) {
    return habitacionRepository.findById(idHabitacion).map(Habitacion::getIdHotel).orElse(null);
  }

  private LimpiezaView view(LimpiezaRepository.Limpieza l) {
    return new LimpiezaView(
        l.cleaningCycleId(),
        l.idHabitacion(),
        l.idRecepcion(),
        l.estado(),
        l.roomVersion(),
        l.version(),
        l.createdAt(),
        l.completedAt());
  }

  private long parseVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    String value = ifMatch.trim();
    if (value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "versión inválida"));
    }
  }
}
