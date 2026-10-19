package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.hotel.domain.dto.EstadoFisicoView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.EstadoHabitacionService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import static org.springframework.http.HttpStatus.*;

/** Estados fisicos fijos del catalogo (A/E lee; A actualiza etiqueta; alta/baja retiradas). */
@RestController
@RequestMapping("/api/estadohabitacion")
public class EstadoHabitacionController {

  @Autowired(required = false)
  private EstadoHabitacionService service;
  @Autowired(required = false)
  private CurrentUser currentUser;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<EstadoFisicoView>>> listar() {
    soloLecturaPrivada();
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.listar(), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<EstadoFisicoView>> buscar(@PathVariable Integer id) {
    soloLecturaPrivada();
    EstadoFisicoView v = service.buscar(id);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<EstadoFisicoView>> actualizar(
      @PathVariable Integer id,
      @RequestParam String descripcion,
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {
    soloAdmin();
    EstadoFisicoView v = service.actualizar(id, descripcion, ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<Void> registrar() {
    soloAdmin();
    service.registrar();
    return ResponseEntity.status(CONFLICT).build();
  }

  @DeleteMapping("/eliminar/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
    soloAdmin();
    service.eliminar(id);
    return ResponseEntity.status(CONFLICT).build();
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
