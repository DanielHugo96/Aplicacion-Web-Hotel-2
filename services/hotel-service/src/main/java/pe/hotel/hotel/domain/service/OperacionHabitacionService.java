package pe.hotel.hotel.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.hotel.client.BloqueoRequest;
import pe.hotel.hotel.client.ReceptionClient;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.dto.OperacionView;
import pe.hotel.hotel.domain.model.Habitacion;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.OperacionHabitacionRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;

/**
 * Mantenimiento, baja, reactivación y consulta de operaciones administrativas.
 *
 * <p>Secuencia del contrato: se persiste la operación PREPARANDO, se pide el bloqueo durable a
 * reception fuera de la transacción SQL y, solo si reception lo admite, se aplica el CAS local.
 * Un 409 de reception deja la operación RECHAZADA con commit antes de responder; un fallo de
 * admisión no confirmado deja PREPARANDO y el job de recuperación reintenta el mismo {@code blockId}.
 */
@Service
public class OperacionHabitacionService {

  private static final Logger log = LoggerFactory.getLogger(OperacionHabitacionService.class);

  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private OperacionHabitacionRepository operacionRepository;
  @Autowired(required = false)
  private HabitacionService habitacionService;
  @Autowired(required = false)
  private OutboxService outboxService;
  @Autowired(required = false)
  private ReceptionClient receptionClient;
  @Autowired(required = false)
  private CurrentUser currentUser;
  @Autowired(required = false)
  private TransactionTemplate transactions;

  private final ObjectMapper mapper = new ObjectMapper();

  /** Resultado de una admisión: la habitación aplicada o la operación aún PREPARANDO. */
  public record Resultado(HabitacionView habitacion, OperacionView operacion, boolean preparando) {}

  public Resultado mantenimiento(Integer idHabitacion, String motivo, String ifMatch) {
    return iniciar(idHabitacion, "MANTENIMIENTO", motivo, ifMatch);
  }

  public Resultado baja(Integer idHabitacion, String ifMatch) {
    return iniciar(idHabitacion, "BAJA", "Baja de habitación", ifMatch);
  }

  private Resultado iniciar(
      Integer idHabitacion, String tipo, String motivo, String ifMatch) {
    Habitacion habitacion = requerirHabitacion(idHabitacion);
    validarVersion(ifMatch, habitacion.getVersion());
    validarMotivo(motivo);
    if (operacionRepository.existsVigente(idHabitacion)) {
      throw new ApiException(
          ErrorCode.ROOM_OPERATION_IN_PROGRESS,
          Map.of("idHabitacion", String.valueOf(idHabitacion)));
    }

    UUID blockId = UUID.randomUUID();
    String request = requestJson(habitacion, tipo, motivo);
    try {
      transactions.executeWithoutResult(
          status -> operacionRepository.crear(blockId, idHabitacion, tipo, request));
    } catch (DataIntegrityViolationException concurrente) {
      throw new ApiException(
          ErrorCode.ROOM_OPERATION_IN_PROGRESS,
          Map.of("idHabitacion", String.valueOf(idHabitacion)));
    }

    try {
      receptionClient.bloquear(
          idHabitacion, new BloqueoRequest(blockId, habitacion.getIdHotel(), motivo));
      Habitacion aplicada =
          transactions.execute(status -> aplicarLocal(blockId, tipo));
      return new Resultado(
          habitacionService.vistaInterna(aplicada.getId()),
          new OperacionView(blockId, idHabitacion, tipo, "APLICADA"),
          false);
    } catch (FeignException.Conflict rechazo) {
      String code = codigoDe(rechazo);
      if ("ROOM_NOT_SYNCED".equals(code)) {
        return preparando(blockId, idHabitacion, tipo);
      }
      transactions.executeWithoutResult(status -> operacionRepository.rechazar(blockId));
      throw new ApiException(codigoError(code), Map.of("idHabitacion", String.valueOf(idHabitacion)));
    } catch (FeignException e) {
      // Timeout, 5xx o circuit breaker abierto: la admisión no se confirmó. No se afirma bloqueo.
      log.warn(
          "operacion_admision_incierta blockId={} idHabitacion={} tipo={} error={}",
          blockId,
          idHabitacion,
          tipo,
          e.getMessage());
      return preparando(blockId, idHabitacion, tipo);
    } catch (RuntimeException e) {
      FeignException conflicto = conflictoDe(e);
      if (conflicto != null) {
        String code = codigoDe(conflicto);
        if ("ROOM_NOT_SYNCED".equals(code)) {
          return preparando(blockId, idHabitacion, tipo);
        }
        transactions.executeWithoutResult(status -> operacionRepository.rechazar(blockId));
        throw new ApiException(
            codigoError(code), Map.of("idHabitacion", String.valueOf(idHabitacion)));
      }
      log.warn(
          "operacion_admision_incierta blockId={} idHabitacion={} tipo={} error={}",
          blockId,
          idHabitacion,
          tipo,
          e.getMessage());
      return preparando(blockId, idHabitacion, tipo);
    }
  }

  private Resultado preparando(UUID blockId, Integer idHabitacion, String tipo) {
    return new Resultado(
        null, new OperacionView(blockId, idHabitacion, tipo, "PREPARANDO"), true);
  }

  public HabitacionView completarMantenimiento(
      Integer idHabitacion, UUID blockId, String ifMatch) {
    Habitacion habitacion = requerirHabitacion(idHabitacion);
    validarVersion(ifMatch, habitacion.getVersion());
    OperacionHabitacionRepository.Operacion op = requerirOperacionAplicada(
        blockId, idHabitacion, "MANTENIMIENTO");
    if (op == null) {
      throw new ApiException(
          ErrorCode.GATE_NOT_AVAILABLE, Map.of("blockId", String.valueOf(blockId)));
    }
    Habitacion liberada =
        transactions.execute(status -> habilitarLocal(blockId, "MANTENIMIENTO"));
    return habitacionService.vistaInterna(liberada.getId());
  }

  public HabitacionView reactivar(Integer idHabitacion, UUID blockId, String ifMatch) {
    Habitacion habitacion = requerirHabitacion(idHabitacion);
    validarVersion(ifMatch, habitacion.getVersion());
    OperacionHabitacionRepository.Operacion op = requerirOperacionAplicada(
        blockId, idHabitacion, "BAJA");
    if (op == null) {
      throw new ApiException(
          ErrorCode.GATE_NOT_AVAILABLE, Map.of("blockId", String.valueOf(blockId)));
    }
    Habitacion liberada = transactions.execute(status -> habilitarLocal(blockId, "BAJA"));
    return habitacionService.vistaInterna(liberada.getId());
  }

  public OperacionView consultar(UUID blockId) {
    return operacionRepository
        .find(blockId)
        .map(op -> new OperacionView(op.blockId(), op.idHabitacion(), op.tipo(), op.estado()))
        .orElseThrow(
            () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("blockId", String.valueOf(blockId))));
  }

  /**
   * Job RB3: reintenta con el mismo `blockId`. Si reception ya tiene el bloqueo ACTIVO aplica el
   * CAS local; si lo rechazó deja RECHAZADA; si sigue incierto incrementa el intento. Nunca libera
   * por timeout ni toca el gate por SQL.
   */
  public int reintentarPendientes(int batchSize, long maxBackoffSeconds) {
    int procesadas = 0;
    for (OperacionHabitacionRepository.Operacion op : operacionRepository.pendientes(batchSize)) {
      procesadas++;
      String motivo = String.valueOf(op.request().getOrDefault("motivo", "Reintento de operación"));
      Integer idHotel = numero(op.request().get("idHotel"));
      try {
        receptionClient.bloquear(
            op.idHabitacion(), new BloqueoRequest(op.blockId(), idHotel, motivo));
        transactions.executeWithoutResult(
            status -> aplicarLocal(op.blockId(), op.tipo()));
        log.info("operacion_recuperada blockId={} idHabitacion={} estado=APLICADA",
            op.blockId(), op.idHabitacion());
      } catch (FeignException.Conflict rechazo) {
        transactions.executeWithoutResult(status -> operacionRepository.rechazar(op.blockId()));
        log.warn("operacion_rechazada blockId={} idHabitacion={} code={}",
            op.blockId(), op.idHabitacion(), codigoDe(rechazo));
      } catch (RuntimeException transitorio) {
        FeignException conflicto = conflictoDe(transitorio);
        if (conflicto != null) {
          transactions.executeWithoutResult(status -> operacionRepository.rechazar(op.blockId()));
          log.warn("operacion_rechazada blockId={} idHabitacion={} code={}",
              op.blockId(), op.idHabitacion(), codigoDe(conflicto));
          continue;
        }
        int attempts = op.attempts() + 1;
        long segundos = Math.min(
            Math.max(1L << Math.min(attempts, 20), 5L), Math.max(maxBackoffSeconds, 5L));
        operacionRepository.registrarIntento(
            op.blockId(), attempts, Instant.now().plus(segundos, ChronoUnit.SECONDS));
        log.warn("operacion_reintento blockId={} attempts={} error={}",
            op.blockId(), attempts, transitorio.getMessage());
      }
    }
    return procesadas;
  }

  public void alertarPreparando(int minutos) {
    int pendientes = operacionRepository.preparandoAntiguas(minutos).size();
    if (pendientes > 0) {
      log.error(
          "operacion_preparando_alerta pendientes={} minutos={}", pendientes, minutos);
    }
  }

  private Habitacion aplicarLocal(UUID blockId, String tipo) {
    Map<String, Object> result = operacionRepository.aplicarBloqueo(blockId, tipo);
    if (!Boolean.TRUE.equals(result.get("ok"))) {
      throw new ApiException(
          codigoError(String.valueOf(result.getOrDefault("code", "GATE_NOT_AVAILABLE"))),
          Map.of("blockId", String.valueOf(blockId)));
    }
    Integer id = numero(result.get("idHabitacion"));
    Habitacion h =
        habitacionRepository
            .findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
    if ("BAJA".equals(tipo)) {
      outboxService.emitirHabitacionBaja(
          h,
          h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
          h.getPiso() == null ? null : h.getPiso().getDescripcion(),
          blockId);
    }
    return h;
  }

  private Habitacion habilitarLocal(UUID blockId, String tipo) {
    Map<String, Object> result = operacionRepository.habilitar(blockId);
    if (!Boolean.TRUE.equals(result.get("ok"))) {
      throw new ApiException(
          codigoError(String.valueOf(result.getOrDefault("code", "GATE_NOT_AVAILABLE"))),
          Map.of("blockId", String.valueOf(blockId)));
    }
    Integer id = numero(result.get("idHabitacion"));
    Habitacion h =
        habitacionRepository
            .findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
    outboxService.emitirHabitacionHabilitada(
        h,
        h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
        h.getPiso() == null ? null : h.getPiso().getDescripcion(),
        blockId);
    return h;
  }

  private OperacionHabitacionRepository.Operacion requerirOperacionAplicada(
      UUID blockId, Integer idHabitacion, String tipo) {
    return operacionRepository
        .find(blockId)
        .filter(op -> op.idHabitacion().equals(idHabitacion))
        .filter(op -> tipo.equals(op.tipo()))
        .filter(op -> "APLICADA".equals(op.estado()))
        .orElse(null);
  }

  private Habitacion requerirHabitacion(Integer id) {
    Habitacion h =
        habitacionRepository
            .findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id))));
    if (currentUser != null && !currentUser.puedeAccederASede(h.getIdHotel())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id)));
    }
    return h;
  }

  private void validarVersion(String ifMatch, Long version) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    String value = ifMatch.trim();
    if (value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    long enviada;
    try {
      enviada = Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "versión inválida"));
    }
    if (version == null || enviada != version) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", "versión distinta"));
    }
  }

  private void validarMotivo(String motivo) {
    if (motivo == null || motivo.isBlank() || motivo.length() > 300) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("motivo", "obligatorio y máximo 300 caracteres"));
    }
  }

  private String requestJson(Habitacion h, String tipo, String motivo) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("idHabitacion", h.getId());
    request.put("idHotel", h.getIdHotel());
    request.put("tipo", tipo);
    request.put("motivo", motivo);
    request.put("requestedAt", Instant.now().toString());
    try {
      return mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new IllegalStateException("no se pudo serializar el request de la operación", e);
    }
  }

  private String codigoDe(FeignException rechazo) {
    try {
      JsonNode body = mapper.readTree(rechazo.contentUTF8());
      String code = body.path("error").path("code").asText("");
      return code.isBlank() ? "GATE_NOT_AVAILABLE" : code;
    } catch (Exception e) {
      return "GATE_NOT_AVAILABLE";
    }
  }

  /**
   * El circuit breaker de Feign envuelve también los 4xx en {@code NoFallbackAvailableException};
   * se busca el {@link FeignException} causa para conservar la semántica durable del 409.
   */
  private FeignException conflictoDe(Throwable error) {
    Throwable actual = error;
    while (actual != null) {
      if (actual instanceof FeignException feign && feign.status() == 409) {
        return feign;
      }
      actual = actual.getCause();
    }
    return null;
  }

  private ErrorCode codigoError(String code) {
    return switch (code) {
      case "ROOM_OCCUPIED" -> ErrorCode.ROOM_OCCUPIED;
      case "ROOM_NOT_READY" -> ErrorCode.ROOM_NOT_READY;
      case "ROOM_OPERATION_IN_PROGRESS" -> ErrorCode.ROOM_OPERATION_IN_PROGRESS;
      case "IDEMPOTENCY_CONFLICT" -> ErrorCode.IDEMPOTENCY_CONFLICT;
      case "PRECONDITION_FAILED" -> ErrorCode.PRECONDITION_FAILED;
      case "PARENT_INACTIVE", "OPERATION_NOT_APPLICABLE", "TASK_NOT_PENDING",
          "GATE_NOT_AVAILABLE" -> ErrorCode.GATE_NOT_AVAILABLE;
      case "RESOURCE_NOT_FOUND" -> ErrorCode.RESOURCE_NOT_FOUND;
      default -> ErrorCode.GATE_NOT_AVAILABLE;
    };
  }

  private Integer numero(Object value) {
    return value instanceof Number n ? n.intValue() : null;
  }
}
