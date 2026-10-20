package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.hotel.domain.dto.HabitacionInput;
import pe.hotel.hotel.domain.dto.HabitacionUpdate;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.HabitacionService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import pe.hotel.hotel.platform.Pagination;
import static org.springframework.http.HttpStatus.*;

/** Lecturas y mutaciones de catalogo de habitaciones (A muta; A/E lee su alcance). */
@RestController
@RequestMapping("/api/habitacion")
public class HabitacionController {

  @Autowired(required = false)
  private HabitacionService service;
  @Autowired(required = false)
  private CurrentUser currentUser;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<HabitacionView>>> listar(
      @RequestParam Integer idHotel,
      @RequestParam(required = false) Integer idCategoria,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    soloLecturaPrivada();
    Pageable pageable = Pagination.validar(page, size);
    Page<HabitacionView> resultado = service.listar(idHotel, idCategoria, pageable);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                resultado.getContent(),
                Meta.of(
                    CorrelationId.current(), page, size, resultado.getTotalElements())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<HabitacionView>> buscar(@PathVariable Integer id) {
    soloLecturaPrivada();
    HabitacionView v = service.buscar(id);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<ApiResponse<HabitacionView>> registrar(@RequestBody HabitacionInput input) {
    soloAdmin();
    HabitacionView v = service.registrar(input);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<HabitacionView>> actualizar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody HabitacionUpdate input) {
    soloAdmin();
    HabitacionView v = service.actualizar(id, input, ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  private void soloLecturaPrivada() {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new org.springframework.web.server.ResponseStatusException(FORBIDDEN, "rol no autorizado");
    }
  }

  private void soloAdmin() {
    if (!currentUser.isAdmin()) {
      throw new org.springframework.web.server.ResponseStatusException(FORBIDDEN, "solo administrador");
    }
  }
}
