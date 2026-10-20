package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.reception.client.HotelClient;
import pe.hotel.reception.client.IdentityClient;
import pe.hotel.reception.domain.dto.BloqueoRequest;
import pe.hotel.reception.domain.dto.CheckInRequest;
import pe.hotel.reception.domain.dto.ClienteVigente;
import pe.hotel.reception.domain.dto.HabitacionInterna;
import pe.hotel.reception.domain.dto.OperacionRequest;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.domain.dto.ResumenRecepcion;
import pe.hotel.reception.domain.security.CurrentUser;
import pe.hotel.reception.platform.ApiException;
import pe.hotel.reception.platform.ApiResponse;
import pe.hotel.reception.platform.ErrorCode;

/** Reglas de operación de reception: check-in, edición, lecturas, bloqueos y admisión. */
@Service
public class ReceptionService {

  private static final List<String> MEDIOS = List.of("EFECTIVO", "TARJETA", "OTRO");
  private static final List<String> TIPOS = List.of("VENTA", "PAGO", "ANULACION");

  private final ReceptionRepository repository;
  private final ReceptionOutbox outbox;
  private final IdentityClient identity;
  private final HotelClient hotel;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;
  private final CurrentUser currentUser;

  public ReceptionService(
      ReceptionRepository repository,
      ReceptionOutbox outbox,
      IdentityClient identity,
      HotelClient hotel,
      TransactionTemplate transactions,
      ObjectMapper mapper,
      CurrentUser currentUser) {
    this.repository = repository;
    this.outbox = outbox;
    this.identity = identity;
    this.hotel = hotel;
    this.transactions = transactions;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  public RecepcionView checkin(CheckInRequest request, String idempotencyKey) {
    validateCheckin(request);
    LocalDate hoy = LocalDate.now(ReceptionRepository.LIMA);
    long noches = ChronoUnit.DAYS.between(hoy, request.fechaSalida());
    if (noches < 1 || noches > 365) {
      throw validation("fechaSalida", "La fecha de salida debe estar entre 1 y 365 noches");
    }

    ClienteVigente cliente = cliente(request.idCliente());
    HabitacionInterna habitacion = habitacion(request.idHabitacion());
    if (habitacion.idHotel() == null || !habitacion.idHotel().equals(request.idHotel())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHabitacion", String.valueOf(request.idHabitacion())));
    }
    if (!Boolean.TRUE.equals(habitacion.estado()) || !Boolean.TRUE.equals(habitacion.hotelActivo())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHabitacion", String.valueOf(request.idHabitacion())));
    }
    if (!"LISTA".equals(habitacion.estadoFisico())) {
      throw new ApiException(ErrorCode.ROOM_NOT_READY, Map.of("idHabitacion", String.valueOf(request.idHabitacion())));
    }

    BigDecimal tarifa = habitacion.precio();
    BigDecimal precio = tarifa.multiply(BigDecimal.valueOf(noches)).setScale(2, RoundingMode.HALF_UP);
    if (request.adelanto().compareTo(precio) > 0) {
      throw validation("adelanto", "El adelanto no puede superar el alojamiento");
    }

    Map<String, Object> clienteSnapshot = new LinkedHashMap<>();
    clienteSnapshot.put("idCliente", cliente.idPersona());
    clienteSnapshot.put("tipoDocumento", cliente.tipoDocumento());
    clienteSnapshot.put("documento", cliente.documento());
    clienteSnapshot.put("nombre", cliente.nombre());
    clienteSnapshot.put("apellido", cliente.apellido());
    clienteSnapshot.put("correo", cliente.correo());
    clienteSnapshot.put("estado", cliente.estado() == null ? true : cliente.estado());
    clienteSnapshot.put("version", cliente.version());

    Map<String, Object> habitacionSnapshot = new LinkedHashMap<>();
    habitacionSnapshot.put("idHabitacion", habitacion.idHabitacion());
    habitacionSnapshot.put("idHotel", habitacion.idHotel());
    habitacionSnapshot.put("numero", habitacion.numero());
    habitacionSnapshot.put("idCategoria", habitacion.idCategoria());
    habitacionSnapshot.put("categoriaNombre", habitacion.categoriaNombre());
    habitacionSnapshot.put("pisoNombre", habitacion.pisoNombre());
    habitacionSnapshot.put("detalle", habitacion.detalle());
    habitacionSnapshot.put("precio", tarifa);
    habitacionSnapshot.put("hotelNombre", habitacion.hotelNombre());
    habitacionSnapshot.put("estadoFisico", habitacion.estadoFisico());
    habitacionSnapshot.put("estado", habitacion.estado());
    habitacionSnapshot.put("version", habitacion.version());

    Map<String, Object> fn = new LinkedHashMap<>();
    fn.put("idHotel", request.idHotel());
    fn.put("idCliente", request.idCliente());
    fn.put("idHabitacion", request.idHabitacion());
    fn.put("fechaEntrada", hoy.toString());
    fn.put("fechaSalida", request.fechaSalida().toString());
    fn.put("adelanto", request.adelanto());
    fn.put("medioPago", blankToNull(request.medioPago()));
    fn.put("observacion", blankToNull(request.observacion()));

    return transactions.execute(
        status -> {
          Map<String, Object> result =
              repository.checkin(write(fn), write(clienteSnapshot), write(habitacionSnapshot));
          if (!Boolean.TRUE.equals(result.get("ok"))) {
            throw error(result);
          }
          int id = ((Number) result.get("idRecepcion")).intValue();
          long roomVersion = ((Number) result.get("roomVersion")).longValue();
          RecepcionView view = repository.findById(id).orElseThrow();
          outbox.emitirCambio(view, roomVersion, "CheckInRegistrado");
          outbox.emitirNotificacionCheckin(view, habitacion.hotelNombre());
          return view;
        });
  }

  public RecepcionView actualizar(int id, LocalDate salida, String observacion, String ifMatch) {
    long version = parseIfMatch(ifMatch);
    if (salida == null) {
      throw validation("fechaSalida", "obligatoria");
    }
    if (observacion != null && observacion.length() > 500) {
      throw validation("observacion", "máximo 500 caracteres");
    }
    RecepcionView actual = repository.findById(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id))));
    LocalDate hoy = LocalDate.now(ReceptionRepository.LIMA);
    if (salida.isBefore(hoy)) {
      throw validation("fechaSalida", "La fecha de salida debe ser hoy o posterior");
    }
    long noches = ChronoUnit.DAYS.between(actual.fechaEntrada(), salida);
    if (noches < 1 || noches > 365) {
      throw validation("fechaSalida", "La estadía debe durar entre 1 y 365 noches");
    }
    return transactions.execute(
        status -> {
          Map<String, Object> result =
              repository.actualizar(id, salida, blankToNull(observacion), version);
          if (!Boolean.TRUE.equals(result.get("ok"))) {
            throw error(result);
          }
          long roomVersion = ((Number) result.get("roomVersion")).longValue();
          RecepcionView view = repository.findById(id).orElseThrow();
          outbox.emitirCambio(view, roomVersion, "RecepcionActualizada");
          return view;
        });
  }

  public RecepcionView buscar(int id) {
    RecepcionView view = repository.findById(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id))));
    requireLectura(view);
    return view;
  }

  public List<RecepcionView> listar(
      Integer idHotel, String estadoEstadia, int page, int size) {
    if (page < 0) {
      throw validation("page", "debe ser 0 o mayor");
    }
    if (size < 1 || size > 100) {
      throw validation("size", "entre 1 y 100");
    }
    if (estadoEstadia != null && !List.of("ACTIVA", "CERRANDO", "CERRADA").contains(estadoEstadia)) {
      throw validation("estadoEstadia", "valor no permitido");
    }
    if (currentUser.isCliente()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (idHotel == null) {
      throw validation("idHotel", "obligatorio");
    }
    if (!currentUser.puedeAccederASede(idHotel)) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("idHotel", String.valueOf(idHotel)));
    }
    return repository.listar(idHotel, estadoEstadia, null, page, size);
  }

  public long contar(Integer idHotel, String estadoEstadia) {
    return repository.count(idHotel, estadoEstadia, null);
  }

  public List<RecepcionView> porCliente(
      Integer idCliente, Integer idHotel, String estadoEstadia, int page, int size) {
    if (page < 0) {
      throw validation("page", "debe ser 0 o mayor");
    }
    if (size < 1 || size > 100) {
      throw validation("size", "entre 1 y 100");
    }
    if (currentUser.isCliente()) {
      Integer sub = currentUser.subjectId();
      if (sub == null || !sub.equals(idCliente)) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
      }
      return repository.listar(null, estadoEstadia, idCliente, page, size);
    }
    if (currentUser.isEmpleado() && idHotel == null) {
      throw validation("idHotel", "obligatorio para EMPLEADO");
    }
    if (currentUser.isEmpleado() && !currentUser.puedeAccederASede(idHotel)) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("idHotel", String.valueOf(idHotel)));
    }
    return repository.listar(idHotel, estadoEstadia, idCliente, page, size);
  }

  public long contarPorCliente(Integer idCliente, Integer idHotel, String estadoEstadia) {
    return repository.count(idHotel, estadoEstadia, idCliente);
  }

  public RecepcionView habitacionActiva(int idHabitacion) {
    if (currentUser.isCliente()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    ReceptionRepository.Gate gate = repository.gate(idHabitacion).orElse(null);
    if (gate != null && !currentUser.puedeAccederASede(gate.idHotel())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    if (gate == null && !currentUser.isAdmin()) {
      // Sin gate no se puede validar la sede de un empleado: anti-enumeración.
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return repository.findByHabitacionActiva(idHabitacion).orElse(null);
  }

  public Map<String, Object> bloquear(int idHabitacion, BloqueoRequest request) {
    if (request.blockId() == null || request.idHotel() == null || request.motivo() == null
        || request.motivo().isBlank() || request.motivo().length() > 300) {
      throw validation("bloqueo", "blockId, idHotel y motivo (1..300) son obligatorios");
    }
    String hash;
    try {
      Map<String, Object> canonical = new LinkedHashMap<>();
      canonical.put("idHabitacion", idHabitacion);
      canonical.put("idHotel", request.idHotel());
      canonical.put("motivo", request.motivo());
      hash = ReceptionRepository.hash(mapper.writeValueAsString(canonical));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    Map<String, Object> result =
        transactions.execute(
            status ->
                repository.bloquear(
                    request.blockId(), idHabitacion, request.idHotel(), request.motivo(), hash));
    if (result == null || !Boolean.TRUE.equals(result.get("ok"))) {
      // La decisión RECHAZADO ya se confirmó en su propia transacción antes del 409.
      throw error(result == null ? Map.of("code", "GATE_NOT_AVAILABLE") : result);
    }
    return result;
  }

  public Map<String, Object> admitir(int idRecepcion, OperacionRequest request) {
    if (request.operationId() == null || request.idVenta() == null || request.idHotel() == null
        || request.tipo() == null || request.actorId() == null || request.actorRol() == null) {
      throw validation("operacion", "operationId, idVenta, idHotel, tipo, actorId y actorRol son obligatorios");
    }
    if (!TIPOS.contains(request.tipo())) {
      throw validation("tipo", "VENTA, PAGO o ANULACION");
    }
    RecepcionView recepcion = repository.findById(idRecepcion)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(idRecepcion))));
    if (!recepcion.idHotel().equals(request.idHotel())) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("idHotel", String.valueOf(request.idHotel())));
    }
    if ("CLIENTE".equals(request.actorRol())
        && (!request.actorId().equals(recepcion.idCliente()) || !"VENTA".equals(request.tipo()))) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    Map<String, Object> result =
        transactions.execute(
            status ->
                repository.admitir(
                    request.operationId(), idRecepcion, request.idVenta(), request.tipo()));
    if (result == null || !Boolean.TRUE.equals(result.get("ok"))) {
      // El rechazo tardío de admisión también es durable: se confirmó antes de responder 409.
      throw error(result == null ? Map.of("code", "STAY_NOT_ACTIVE") : result);
    }
    return result;
  }

  public ResumenRecepcion resumen(int id) {
    return repository.resumen(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id))));
  }

  private void validateCheckin(CheckInRequest request) {
    if (request.fechaEntrada() != null) {
      throw validation("fechaEntrada", "la fija el servidor; campo no permitido");
    }
    if (request.precioInicial() != null
        || request.precioRestante() != null
        || request.precioHabitacion() != null
        || request.tarifa() != null
        || request.totalPagado() != null
        || request.estadoEstadia() != null
        || request.version() != null) {
      throw validation("body", "los precios, el estado y la versión los calcula el servidor");
    }
    if (request.nombre() != null
        || request.apellido() != null
        || request.tipoDocumento() != null
        || request.documento() != null
        || request.correo() != null) {
      throw validation("body", "los datos del cliente provienen de identity; no se aceptan en el body");
    }
    if (request.idHotel() == null) {
      throw validation("idHotel", "obligatorio");
    }
    if (request.idCliente() == null) {
      throw validation("idCliente", "obligatorio");
    }
    if (request.idHabitacion() == null) {
      throw validation("idHabitacion", "obligatorio");
    }
    if (request.fechaSalida() == null) {
      throw validation("fechaSalida", "obligatoria");
    }
    if (request.adelanto() == null || request.adelanto().signum() < 0) {
      throw validation("adelanto", "no puede ser negativo");
    }
    if (request.observacion() != null && request.observacion().length() > 500) {
      throw validation("observacion", "máximo 500 caracteres");
    }
    if (request.adelanto().signum() > 0) {
      if (request.medioPago() == null || request.medioPago().isBlank()) {
        throw validation("medioPago", "obligatorio cuando hay adelanto");
      }
      if (!MEDIOS.contains(request.medioPago())) {
        throw validation("medioPago", "EFECTIVO, TARJETA u OTRO");
      }
    } else if (request.medioPago() != null
        && !request.medioPago().isBlank()
        && !MEDIOS.contains(request.medioPago())) {
      throw validation("medioPago", "EFECTIVO, TARJETA u OTRO");
    }
  }

  private ClienteVigente cliente(int id) {
    try {
      ApiResponse<ClienteVigente> response = identity.clienteVigente(id);
      if (response == null || response.data() == null) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idCliente", String.valueOf(id)));
      }
      return response.data();
    } catch (FeignException.NotFound notFound) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idCliente", String.valueOf(id)));
    } catch (FeignException | ApiException ex) {
      if (ex instanceof ApiException api) {
        throw api;
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private HabitacionInterna habitacion(int id) {
    try {
      ApiResponse<HabitacionInterna> response = hotel.habitacion(id);
      if (response == null || response.data() == null) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHabitacion", String.valueOf(id)));
      }
      return response.data();
    } catch (FeignException.NotFound notFound) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idHabitacion", String.valueOf(id)));
    } catch (FeignException | ApiException ex) {
      if (ex instanceof ApiException api) {
        throw api;
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private void requireLectura(RecepcionView view) {
    if (currentUser.isAdmin()) {
      return;
    }
    if (currentUser.isEmpleado()) {
      if (!currentUser.puedeAccederASede(view.idHotel())) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
      }
      return;
    }
    Integer sub = currentUser.subjectId();
    if (sub == null || !sub.equals(view.idCliente())) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
  }

  private long parseIfMatch(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
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

  private ApiException error(Map<String, Object> result) {
    String code = String.valueOf(result.get("code"));
    ErrorCode mapped =
        switch (code) {
          case "ROOM_OCCUPIED" -> ErrorCode.ROOM_OCCUPIED;
          case "ROOM_NOT_READY" -> ErrorCode.ROOM_NOT_READY;
          case "ROOM_NOT_SYNCED" -> ErrorCode.ROOM_NOT_SYNCED;
          case "STAY_NOT_ACTIVE" -> ErrorCode.STAY_NOT_ACTIVE;
          case "OPERATIONS_IN_PROGRESS" -> ErrorCode.OPERATIONS_IN_PROGRESS;
          case "ADVANCE_EXCEEDS_NEW_TOTAL" -> ErrorCode.ADVANCE_EXCEEDS_NEW_TOTAL;
          case "GATE_NOT_AVAILABLE" -> ErrorCode.GATE_NOT_AVAILABLE;
          case "IDEMPOTENCY_CONFLICT" -> ErrorCode.IDEMPOTENCY_CONFLICT;
          case "PRECONDITION_FAILED" -> ErrorCode.PRECONDITION_FAILED;
          case "RESOURCE_NOT_FOUND" -> ErrorCode.RESOURCE_NOT_FOUND;
          case "FORBIDDEN" -> ErrorCode.FORBIDDEN;
          default -> ErrorCode.VALIDATION_ERROR;
        };
    Object field = result.get("field");
    Map<String, String> fields =
        field == null ? Map.of() : Map.of(String.valueOf(field), mapped.uiMessage());
    return new ApiException(mapped, fields);
  }

  private ApiException validation(String field, String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, Map.of(field, message));
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID uuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }
}
