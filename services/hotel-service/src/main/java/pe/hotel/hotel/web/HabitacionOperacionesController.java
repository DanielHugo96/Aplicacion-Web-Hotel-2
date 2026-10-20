package pe.hotel.hotel.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.hotel.domain.dto.BlockInput;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.dto.LimpiezaCompletadaInput;
import pe.hotel.hotel.domain.dto.LimpiezaResultadoView;
import pe.hotel.hotel.domain.dto.LimpiezaView;
import pe.hotel.hotel.domain.dto.MantenimientoInput;
import pe.hotel.hotel.domain.dto.OperacionView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.LimpiezaService;
import pe.hotel.hotel.domain.service.OperacionHabitacionService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import pe.hotel.hotel.platform.Pagination;
import static org.springframework.http.HttpStatus.*;

/**
 * Rutas de operación y limpieza del contrato hotel: mantenimiento/baja/reactivación con blockId,
 * consulta de operaciones y ciclo de limpieza. A muta; A/E leen limpiezas.
 */
@RestController
@RequestMapping("/api/habitacion")
public class HabitacionOperacionesController {

  @Autowired(required = false)
  private OperacionHabitacionService operaciones;
  @Autowired(required = false)
  private LimpiezaService limpiezas;
  @Autowired(required = false)
  private CurrentUser currentUser;

  @PostMapping("/{id}/mantenimiento")
  public ResponseEntity<ApiResponse<?>> mantenimiento(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody MantenimientoInput input) {
    soloAdmin();
    OperacionHabitacionService.Resultado resultado =
        operaciones.mantenimiento(id, input.motivo(), ifMatch);
    return operacionResponse(resultado);
  }

  @DeleteMapping("/eliminar/{id}")
  public ResponseEntity<ApiResponse<?>> eliminar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {
    soloAdmin();
    return operacionResponse(operaciones.baja(id, ifMatch));
  }

  @PutMapping("/{id}/mantenimiento-completado")
  public ResponseEntity<ApiResponse<HabitacionView>> completarMantenimiento(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody BlockInput input) {
    soloAdmin();
    HabitacionView vista = operaciones.completarMantenimiento(id, input.blockId(), ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(vista.version()))
        .body(ApiResponse.ok(vista, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/{id}/reactivar")
  public ResponseEntity<ApiResponse<HabitacionView>> reactivar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody BlockInput input) {
    soloAdmin();
    HabitacionView vista = operaciones.reactivar(id, input.blockId(), ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(vista.version()))
        .body(ApiResponse.ok(vista, Meta.of(CorrelationId.current())));
  }

  @GetMapping("/operaciones/{blockId}")
  public ResponseEntity<ApiResponse<OperacionView>> consultarOperacion(
      @PathVariable UUID blockId) {
    soloAdmin();
    return ResponseEntity.ok()
        .body(ApiResponse.ok(operaciones.consultar(blockId), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/limpiezas")
  public ResponseEntity<ApiResponse<List<LimpiezaView>>> listarLimpiezas(
      @RequestParam Integer idHotel,
      @RequestParam(required = false) String estado,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    soloLecturaPrivada();
    LimpiezaService.Listado listado = limpiezas.listar(idHotel, estado, page, size);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                listado.data(),
                Meta.of(CorrelationId.current(), page, size, listado.totalElements())));
  }

  @PutMapping("/{id}/limpieza-completada")
  public ResponseEntity<ApiResponse<LimpiezaResultadoView>> completarLimpieza(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody LimpiezaCompletadaInput input) {
    soloLecturaPrivada();
    LimpiezaResultadoView resultado =
        limpiezas.completar(id, input.cleaningCycleId(), ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(resultado.limpieza().version()))
        .body(ApiResponse.ok(resultado, Meta.of(CorrelationId.current())));
  }

  private ResponseEntity<ApiResponse<?>> operacionResponse(
      OperacionHabitacionService.Resultado resultado) {
    if (resultado.preparando()) {
      return ResponseEntity.status(ACCEPTED)
          .header("Location", "/api/habitacion/operaciones/" + resultado.operacion().blockId())
          .header("Retry-After", "2")
          .body(
              ApiResponse.ok(
                  "Operación en curso",
                  resultado.operacion(),
                  Meta.of(CorrelationId.current())));
    }
    return ResponseEntity.ok()
        .eTag(String.valueOf(resultado.habitacion().version()))
        .body(ApiResponse.ok(resultado.habitacion(), Meta.of(CorrelationId.current())));
  }

  private void soloAdmin() {
    if (currentUser == null || !currentUser.isAdmin()) {
      throw new org.springframework.web.server.ResponseStatusException(FORBIDDEN, "solo administrador");
    }
  }

  private void soloLecturaPrivada() {
    if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isEmpleado())) {
      throw new org.springframework.web.server.ResponseStatusException(FORBIDDEN, "rol no autorizado");
    }
  }
}
