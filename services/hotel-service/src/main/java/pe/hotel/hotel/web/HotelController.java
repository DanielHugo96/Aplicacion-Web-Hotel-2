package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.hotel.domain.dto.HotelInput;
import pe.hotel.hotel.domain.dto.HotelView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.HotelService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import static org.springframework.http.HttpStatus.*;

/** CRUD de sedes (A muta; A/E lee su alcance). */
@RestController
@RequestMapping("/api/hotel")
public class HotelController {

  @Autowired(required = false)
  private HotelService service;
  @Autowired(required = false)
  private CurrentUser currentUser;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<HotelView>>> listar(
      @RequestParam(required = false) Boolean estado) {
    soloLecturaPrivada();
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.listar(estado), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<HotelView>> buscar(@PathVariable Integer id) {
    soloLecturaPrivada();
    HotelView v = service.buscar(id);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<ApiResponse<HotelView>> registrar(@RequestBody HotelInput input) {
    soloAdmin();
    HotelView v = service.registrar(input);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<HotelView>> actualizar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody HotelInput input) {
    soloAdmin();
    HotelView v = service.actualizar(id, input, ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @DeleteMapping("/eliminar/{id}")
  public ResponseEntity<ApiResponse<HotelView>> eliminar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {
    soloAdmin();
    HotelView v = service.eliminar(id, ifMatch);
    return ResponseEntity.ok()
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  private void soloLecturaPrivada() {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "rol no autorizado");
    }
  }

  private void soloAdmin() {
    if (!currentUser.isAdmin()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "solo administrador");
    }
  }
}
