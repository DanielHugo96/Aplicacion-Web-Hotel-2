package pe.hotel.reception.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.reception.domain.ReceptionService;
import pe.hotel.reception.domain.dto.ActualizarRecepcionRequest;
import pe.hotel.reception.domain.dto.CheckInRequest;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.domain.security.CurrentUser;
import pe.hotel.reception.platform.ApiException;
import pe.hotel.reception.platform.ApiResponse;
import pe.hotel.reception.platform.CorrelationId;
import pe.hotel.reception.platform.ErrorCode;
import pe.hotel.reception.platform.Meta;

/** Rutas humanas /api/recepcion/** (A/E; C solo lectura de su propia estadía). */
@RestController
@RequestMapping("/api/recepcion")
public class ReceptionController {

  private final ReceptionService service;
  private final CurrentUser currentUser;

  public ReceptionController(ReceptionService service, CurrentUser currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<RecepcionView>>> listar(
      @RequestParam("idHotel") Integer idHotel,
      @RequestParam(value = "estadoEstadia", required = false) String estadoEstadia,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    List<RecepcionView> data = service.listar(idHotel, estadoEstadia, page, size);
    long total = service.contar(idHotel, estadoEstadia);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                data, Meta.of(CorrelationId.current(), page, size, total)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RecepcionView>> buscar(@PathVariable("id") int id) {
    RecepcionView view = service.buscar(id);
    return ResponseEntity.ok()
        .eTag(String.valueOf(view.version()))
        .body(ApiResponse.ok(view, Meta.of(CorrelationId.current())));
  }

  @GetMapping("/cliente/{idCliente}")
  public ResponseEntity<ApiResponse<List<RecepcionView>>> porCliente(
      @PathVariable("idCliente") int idCliente,
      @RequestParam(value = "idHotel", required = false) Integer idHotel,
      @RequestParam(value = "estadoEstadia", required = false) String estadoEstadia,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    List<RecepcionView> data = service.porCliente(idCliente, idHotel, estadoEstadia, page, size);
    long total = service.contarPorCliente(idCliente, idHotel, estadoEstadia);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                data, Meta.of(CorrelationId.current(), page, size, total)));
  }

  @GetMapping("/habitacion-activa/{idHabitacion}")
  public ResponseEntity<ApiResponse<RecepcionView>> habitacionActiva(
      @PathVariable("idHabitacion") int idHabitacion) {
    RecepcionView view = service.habitacionActiva(idHabitacion);
    return ResponseEntity.ok()
        .body(ApiResponse.ok(view, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<ApiResponse<RecepcionView>> registrar(
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CheckInRequest request) {
    soloPersonal();
    RecepcionView view = service.checkin(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(String.valueOf(view.version()))
        .body(
            ApiResponse.ok(
                "Check-in registrado", view, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<RecepcionView>> actualizar(
      @PathVariable("id") int id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody ActualizarRecepcionRequest body) {
    soloPersonal();
    RecepcionView view =
        service.actualizar(id, body.fechaSalida(), body.observacion(), ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(view.version()))
        .body(ApiResponse.ok(view, Meta.of(CorrelationId.current())));
  }

  private void soloPersonal() {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }
}
