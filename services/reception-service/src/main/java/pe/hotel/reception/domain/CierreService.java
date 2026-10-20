package pe.hotel.reception.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.reception.client.SalesClient;
import pe.hotel.reception.domain.dto.CalculoCierre;
import pe.hotel.reception.domain.dto.ConfirmacionCierre;
import pe.hotel.reception.domain.dto.Cotizacion;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.domain.dto.ReceiptSales;
import pe.hotel.reception.domain.dto.ResumenCierreSales;
import pe.hotel.reception.domain.dto.SalidaRequest;
import pe.hotel.reception.domain.security.CurrentUser;
import pe.hotel.reception.platform.ApiDataException;
import pe.hotel.reception.platform.ApiException;
import pe.hotel.reception.platform.CorrelationId;
import pe.hotel.reception.platform.ErrorCode;

/**
 * Cierre de estadía (docs/especificacion/04-flujos.md § 4): cotización informativa, inicio durable,
 * comparación exacta del monto, congelamiento del cálculo, confirmación idempotente en sales,
 * completado con outbox triple, recuperación cada 30 s, anulación de check-in y archivado.
 */
@Service
public class CierreService {

  private static final Logger log = LoggerFactory.getLogger(CierreService.class);
  private static final List<String> MEDIOS = List.of("EFECTIVO", "TARJETA", "OTRO");
  private static final BigDecimal CERO = new BigDecimal("0.00");

  private final ReceptionRepository repository;
  private final ReceptionOutbox outbox;
  private final SalesClient sales;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;
  private final CurrentUser currentUser;

  public CierreService(
      ReceptionRepository repository,
      ReceptionOutbox outbox,
      SalesClient sales,
      TransactionTemplate transactions,
      ObjectMapper mapper,
      CurrentUser currentUser) {
    this.repository = repository;
    this.outbox = outbox;
    this.sales = sales;
    this.transactions = transactions;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  /** Resultado del POST: 200 con la estadía cerrada o 202 con Location/Retry-After. */
  public record ResultadoCierre(RecepcionView view, int status, String location, Integer retryAfter) {}

  // ------------------------------------------------------------------ cotización

  public Cotizacion cotizar(int id, BigDecimal costoPenalidad) {
    RecepcionView view = cargar(id);
    requirePersonalDeLaSede(view);
    BigDecimal penalidad = costoPenalidad == null ? CERO : money(costoPenalidad);
    if (penalidad.signum() < 0) {
      throw validation("costoPenalidad", "no puede ser negativo");
    }
    if (penalidad.compareTo(view.precioInicial()) > 0) {
      throw validation("costoPenalidad", "no puede superar el alojamiento");
    }
    ResumenCierreSales resumen = resumen(id, view.idHotel());
    CalculoCierre calculo = calcular(view, resumen, penalidad);
    return new Cotizacion(
        id,
        calculo.alojamientoTotal(),
        money(view.adelanto()),
        penalidad,
        calculo.saldoAlojamiento(),
        calculo.consumosPagadosAntes(),
        calculo.consumosPendientes(),
        calculo.cobroAhora(),
        "PEN",
        Instant.now());
  }

  // ------------------------------------------------------------------ cierre

  public ResultadoCierre registrarSalida(SalidaRequest request, UUID closureId) {
    validarSalida(request);
    RecepcionView view = cargar(request.idRecepcion());
    requirePersonalDeLaSede(view);
    if (!view.idHabitacion().equals(request.idHabitacion())) {
      throw validation("idHabitacion", "no corresponde a la estadía");
    }
    BigDecimal penalidad = money(request.costoPenalidad());
    if (penalidad.signum() < 0) {
      throw validation("costoPenalidad", "no puede ser negativo");
    }
    if (penalidad.compareTo(view.precioInicial()) > 0) {
      throw validation("costoPenalidad", "no puede superar el alojamiento");
    }
    if (penalidad.signum() > 0
        && (request.motivoPenalidad() == null || request.motivoPenalidad().isBlank())) {
      throw validation("motivoPenalidad", "obligatorio cuando hay penalidad");
    }
    if (penalidad.signum() == 0) {
      request = new SalidaRequest(request.idRecepcion(), request.idHabitacion(), penalidad,
          null, request.montoRecibido(), request.medioPago());
    }
    final SalidaRequest salida = request;

    boolean existente = repository.cierre(closureId).isPresent();
    ResumenCierreSales preResumen = null;
    if (!existente) {
      // 503 antes de crear una operación durable: sales caído no deja filas en cierre.
      preResumen = resumen(view.idRecepcion(), view.idHotel());
    }

    Map<String, Object> fn =
        transactions.execute(
            status -> repository.iniciarCierre(closureId, view.idRecepcion(), write(requestJson(view, salida))));
    if (fn == null || !Boolean.TRUE.equals(fn.get("ok"))) {
      throw error(fn == null ? Map.of("code", "STAY_NOT_ACTIVE") : fn);
    }

    ReceptionRepository.CierreRow cierre =
        repository.cierre(closureId)
            .orElseThrow(() -> new IllegalStateException("cierre no persistido"));

    switch (cierre.estado()) {
      case "COMPLETADO" -> {
        return new ResultadoCierre(cargar(view.idRecepcion()), 200, null, null);
      }
      case "RECHAZADO" -> {
        Cotizacion cotizacion = cotizar(view.idRecepcion(), penalidad);
        throw rechazo(cotizacion, closureId);
      }
      case "CONFIRMANDO" -> {
        return continuarConfirmacion(cierre);
      }
      default -> {
        // VALIDANDO: comparar el monto declarado contra el cálculo vigente.
      }
    }

    ResumenCierreSales resumen =
        preResumen != null ? preResumen : resumen(view.idRecepcion(), view.idHotel());
    CalculoCierre calculo = calcular(view, resumen, penalidad);
    if (salida.montoRecibido().compareTo(calculo.cobroAhora()) != 0) {
      transactions.execute(
          status -> {
            Map<String, Object> rechazo = repository.rechazarCierre(closureId);
            if (!Boolean.TRUE.equals(rechazo.get("ok"))) {
              throw error(rechazo);
            }
            return rechazo;
          });
      throw rechazo(cotizacion(view, calculo), closureId);
    }

    transactions.execute(
        status -> {
          Map<String, Object> preparado =
              repository.prepararConfirmacion(closureId, write(calculo));
          if (!Boolean.TRUE.equals(preparado.get("ok"))) {
            throw error(preparado);
          }
          return preparado;
        });

    return continuarConfirmacion(
        repository.cierre(closureId)
            .orElseThrow(() -> new IllegalStateException("cierre no persistido")));
  }

  /** Confirmación idempotente con el payload congelado; timeout deja 202 y el recuperador sigue. */
  private ResultadoCierre continuarConfirmacion(ReceptionRepository.CierreRow cierre) {
    RecepcionView view = cargar(cierre.idRecepcion());
    CalculoCierre calculo = parseCalculo(cierre);
    String medioPago = requestField(cierre.request(), "medioPago");

    ReceiptSales receipt;
    try {
      receipt =
          sales
              .confirmar(
                  cierre.closureId().toString(),
                  new ConfirmacionCierre(
                      cierre.closureId(),
                      cierre.idRecepcion(),
                      view.idHotel(),
                      calculo.expectedPendingTotal(),
                      medioPago))
              .data();
    } catch (FeignException e) {
      log.warn("cierre_confirmacion_fallida closureId={} status={}", cierre.closureId(), e.status());
      return pendiente(cierre.closureId(), codigoFeign(e), view);
    }
    if (receipt == null) {
      return pendiente(cierre.closureId(), "DEPENDENCY_UNAVAILABLE", view);
    }
    if (!cierre.closureId().equals(receipt.closureId())
        || !Integer.valueOf(cierre.idRecepcion()).equals(receipt.idRecepcion())
        || !view.idHotel().equals(receipt.idHotel())
        || receipt.totalPagadoAhora() == null
        || receipt.totalPagadoAhora().compareTo(calculo.expectedPendingTotal()) != 0) {
      return pendiente(cierre.closureId(), "RECEIPT_MISMATCH", view);
    }

    RecepcionView cerrada =
        transactions.execute(
            status -> {
              Map<String, Object> completado = repository.completarCierre(cierre.closureId(), write(receipt));
              if (!Boolean.TRUE.equals(completado.get("ok"))) {
                throw error(completado);
              }
              RecepcionView actual = cargar(cierre.idRecepcion());
              long roomVersion = ((Number) completado.get("roomVersion")).longValue();
              UUID cleaningCycleId = uuid(completado.get("cleaningCycleId"));
              outbox.emitirSalida(
                  actual, roomVersion, calculo, medioPago, receipt.confirmedAt());
              outbox.emitirLimpieza(actual, roomVersion, cleaningCycleId);
              outbox.emitirCheckout(actual, calculo, repository.hotelNombre(cierre.idRecepcion()));
              return actual;
            });
    return new ResultadoCierre(cerrada, 200, null, null);
  }

  private ResultadoCierre pendiente(UUID closureId, String code, RecepcionView view) {
    transactions.execute(
        status -> {
          repository.marcarErrorCierre(closureId, code, 30);
          return null;
        });
    return new ResultadoCierre(
        cargar(view.idRecepcion()), 202, "/api/recepcion/" + view.idRecepcion(), 2);
  }

  // ------------------------------------------------------------------ recuperador

  /** Recorre los cierres pendientes por fase; lo llama el planificador cada 30 s. */
  public void recuperarPendientes() {
    for (ReceptionRepository.CierreRow cierre : repository.recuperables(20)) {
      try {
        recuperar(cierre);
      } catch (RuntimeException e) {
        log.warn("cierre_recuperacion_error closureId={} motivo={}", cierre.closureId(), e.getMessage());
        transactions.execute(
            status -> {
              repository.marcarErrorCierre(cierre.closureId(), "DEPENDENCY_UNAVAILABLE", 30);
              return null;
            });
      }
    }
  }

  public void recuperar(ReceptionRepository.CierreRow cierre) {
    if ("CONFIRMANDO".equals(cierre.estado())) {
      continuarConfirmacion(cierre);
      return;
    }
    if (!"VALIDANDO".equals(cierre.estado())) {
      return;
    }
    RecepcionView view = cargar(cierre.idRecepcion());
    ResumenCierreSales resumen = resumen(view.idRecepcion(), view.idHotel());
    BigDecimal penalidad = new BigDecimal(requestField(cierre.request(), "costoPenalidad"));
    CalculoCierre calculo = calcular(view, resumen, money(penalidad));
    BigDecimal declarado = new BigDecimal(requestField(cierre.request(), "montoRecibido"));
    if (declarado.compareTo(calculo.cobroAhora()) != 0) {
      transactions.execute(
          status -> {
            repository.rechazarCierre(cierre.closureId());
            return null;
          });
      log.info("cierre_recuperado_rechazado closureId={}", cierre.closureId());
      return;
    }
    transactions.execute(
        status -> {
          repository.prepararConfirmacion(cierre.closureId(), write(calculo));
          return null;
        });
    continuarConfirmacion(
        repository.cierre(cierre.closureId())
            .orElseThrow(() -> new IllegalStateException("cierre no persistido")));
  }

  // ------------------------------------------------------------------ anulación y archivado

  public RecepcionView anular(int id, String motivo, String ifMatch, UUID closureId) {
    long version = parseIfMatch(ifMatch);
    if (motivo == null || motivo.isBlank() || motivo.length() > 300) {
      throw validation("motivo", "obligatorio entre 1 y 300 caracteres");
    }
    cargar(id);
    return transactions.execute(
        status -> {
          Map<String, Object> fn =
              repository.anularCheckin(
                  id, motivo.trim(), version, currentUser.actor(), correlationUuid());
          if (fn == null || !Boolean.TRUE.equals(fn.get("ok"))) {
            throw error(fn == null ? Map.of("code", "STAY_NOT_ACTIVE") : fn);
          }
          RecepcionView vista = cargar(id);
          long roomVersion = ((Number) fn.get("roomVersion")).longValue();
          outbox.emitirAnulacion(vista, roomVersion);
          return vista;
        });
  }

  public RecepcionView archivar(int id, String ifMatch) {
    long version = parseIfMatch(ifMatch);
    cargar(id);
    return transactions.execute(
        status -> {
          Map<String, Object> fn = repository.archivar(id, version);
          if (fn == null || !Boolean.TRUE.equals(fn.get("ok"))) {
            throw error(fn == null ? Map.of("code", "STAY_NOT_ACTIVE") : fn);
          }
          return cargar(id);
        });
  }

  // ------------------------------------------------------------------ cálculo

  CalculoCierre calcular(RecepcionView view, ResumenCierreSales resumen, BigDecimal penalidad) {
    BigDecimal alojamiento = money(view.precioInicial().add(penalidad));
    BigDecimal saldo = money(alojamiento.subtract(view.adelanto()));
    if (saldo.signum() < 0) {
      saldo = CERO;
    }
    BigDecimal pagados = money(resumen.totalPagado() == null ? CERO : resumen.totalPagado());
    BigDecimal pendientes = money(resumen.totalPendiente() == null ? CERO : resumen.totalPendiente());
    BigDecimal consumosTotal = money(pagados.add(pendientes));
    BigDecimal cobroAhora = money(saldo.add(pendientes));
    BigDecimal totalGeneral = money(alojamiento.add(consumosTotal));
    return new CalculoCierre(
        alojamiento, saldo, pagados, pendientes, consumosTotal, cobroAhora, totalGeneral, pendientes);
  }

  private Cotizacion cotizacion(RecepcionView view, CalculoCierre calculo) {
    return new Cotizacion(
        view.idRecepcion(),
        calculo.alojamientoTotal(),
        money(view.adelanto()),
        money(calculo.alojamientoTotal().subtract(view.precioInicial())),
        calculo.saldoAlojamiento(),
        calculo.consumosPagadosAntes(),
        calculo.consumosPendientes(),
        calculo.cobroAhora(),
        "PEN",
        Instant.now());
  }

  private ApiDataException rechazo(Cotizacion cotizacion, UUID closureId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("cotizacion", cotizacion);
    data.put("closureId", closureId);
    return new ApiDataException(
        ErrorCode.AMOUNT_MISMATCH,
        data,
        Map.of("montoRecibido", cotizacion.cobroAhora().toPlainString()));
  }

  // ------------------------------------------------------------------ sales

  private ResumenCierreSales resumen(int idRecepcion, int idHotel) {
    try {
      var response = sales.resumen(idRecepcion, idHotel);
      if (response == null || response.data() == null) {
        throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
      }
      if (response.data().hayOperacionesAbiertas()) {
        throw new ApiException(
            ErrorCode.OPERATIONS_IN_PROGRESS, Map.of("idRecepcion", String.valueOf(idRecepcion)));
      }
      return response.data();
    } catch (ApiException e) {
      throw e;
    } catch (FeignException e) {
      if (e.status() == 409) {
        throw new ApiException(
            ErrorCode.OPERATIONS_IN_PROGRESS, Map.of("idRecepcion", String.valueOf(idRecepcion)));
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private String codigoFeign(FeignException e) {
    String body = e.contentUTF8() == null ? "" : e.contentUTF8();
    for (String code : List.of("AMOUNT_MISMATCH", "CLOSURE_IN_PROGRESS", "OPERATIONS_IN_PROGRESS",
        "STAY_NOT_ACTIVE", "VALIDATION_ERROR")) {
      if (body.contains(code)) {
        return code;
      }
    }
    return "DEPENDENCY_UNAVAILABLE";
  }

  // ------------------------------------------------------------------ auxiliares

  private void validarSalida(SalidaRequest request) {
    if (request == null) {
      throw validation("body", "obligatorio");
    }
    if (request.idRecepcion() == null || request.idRecepcion() < 1) {
      throw validation("idRecepcion", "obligatorio");
    }
    if (request.idHabitacion() == null || request.idHabitacion() < 1) {
      throw validation("idHabitacion", "obligatorio");
    }
    if (request.costoPenalidad() == null || request.costoPenalidad().signum() < 0) {
      throw validation("costoPenalidad", "obligatorio y no negativo");
    }
    if (request.motivoPenalidad() != null && request.motivoPenalidad().length() > 300) {
      throw validation("motivoPenalidad", "máximo 300 caracteres");
    }
    if (request.montoRecibido() == null || request.montoRecibido().signum() < 0) {
      throw validation("montoRecibido", "obligatorio y no negativo");
    }
    if (request.medioPago() == null || !MEDIOS.contains(request.medioPago())) {
      throw validation("medioPago", "EFECTIVO, TARJETA u OTRO");
    }
  }

  private RecepcionView cargar(int id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(id))));
  }

  /** A/E con sede válida; un E sobre otra sede recibe 404 (anti-enumeración). */
  private void requirePersonalDeLaSede(RecepcionView view) {
    if (currentUser.isAdmin()) {
      return;
    }
    if (currentUser.isEmpleado()) {
      if (!currentUser.puedeAccederASede(view.idHotel())) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
      }
      return;
    }
    throw new ApiException(ErrorCode.FORBIDDEN);
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

  private Map<String, Object> requestJson(RecepcionView view, SalidaRequest request) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("idRecepcion", view.idRecepcion());
    json.put("idHabitacion", view.idHabitacion());
    json.put("costoPenalidad", money(request.costoPenalidad()));
    json.put("motivoPenalidad", request.motivoPenalidad());
    json.put("montoRecibido", money(request.montoRecibido()));
    json.put("medioPago", request.medioPago());
    json.put("requestedAt", Instant.now().toString());
    return json;
  }

  private String requestField(String requestJson, String field) {
    try {
      com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(requestJson);
      return node.path(field).isNull() ? null : node.path(field).asText();
    } catch (Exception e) {
      throw new IllegalStateException("request de cierre ilegible", e);
    }
  }

  private CalculoCierre parseCalculo(ReceptionRepository.CierreRow cierre) {
    try {
      return mapper.readValue(cierre.calculo(), CalculoCierre.class);
    } catch (Exception e) {
      throw new IllegalStateException("cálculo de cierre ilegible", e);
    }
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private UUID correlationUuid() {
    String current = CorrelationId.current();
    if (current != null) {
      try {
        return UUID.fromString(current);
      } catch (IllegalArgumentException ignored) {
        // cabecera no UUID: se genera una correlación local
      }
    }
    return UUID.randomUUID();
  }

  private static UUID uuid(Object value) {
    if (value == null) {
      return null;
    }
    return value instanceof UUID id ? id : UUID.fromString(value.toString());
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  private ApiException error(Map<String, Object> result) {
    String code = String.valueOf(result.get("code"));
    ErrorCode mapped =
        switch (code) {
          case "STAY_NOT_ACTIVE" -> ErrorCode.STAY_NOT_ACTIVE;
          case "OPERATIONS_IN_PROGRESS" -> ErrorCode.OPERATIONS_IN_PROGRESS;
          case "CLOSURE_IN_PROGRESS" -> ErrorCode.CLOSURE_IN_PROGRESS;
          case "AMOUNT_MISMATCH" -> ErrorCode.AMOUNT_MISMATCH;
          case "IDEMPOTENCY_CONFLICT" -> ErrorCode.IDEMPOTENCY_CONFLICT;
          case "PRECONDITION_FAILED" -> ErrorCode.PRECONDITION_FAILED;
          case "RESOURCE_NOT_FOUND" -> ErrorCode.RESOURCE_NOT_FOUND;
          case "INVALID_STATE", "RECEIPT_MISMATCH" -> ErrorCode.STAY_NOT_ACTIVE;
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
}
