package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.hotel.domain.dto.CategoriaInput;
import pe.hotel.hotel.domain.dto.CategoriaView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.CategoriaService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import pe.hotel.hotel.platform.Pagination;
import static org.springframework.http.HttpStatus.*;

/** CRUD de categorías globales (A muta; A/E lee). */
@RestController
@RequestMapping("/api/categoria")
public class CategoriaController {

  @Autowired(required = false)
  private CategoriaService service;
  @Autowired(required = false)
  private CurrentUser currentUser;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<CategoriaView>>> listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Boolean estado) {
    soloLecturaPrivada();
    Pageable pageable = Pagination.validar(page, size);
    Page<CategoriaView> resultado = service.listar(estado, pageable);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                resultado.getContent(),
                Meta.of(
                    CorrelationId.current(), page, size, resultado.getTotalElements())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<CategoriaView>> buscar(@PathVariable Integer id) {
    soloLecturaPrivada();
    CategoriaView v = service.buscar(id);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<ApiResponse<CategoriaView>> registrar(@RequestBody CategoriaInput input) {
    soloAdmin();
    CategoriaView v = service.registrar(input);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<CategoriaView>> actualizar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody CategoriaInput input) {
    soloAdmin();
    CategoriaView v = service.actualizar(id, input, ifMatch);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @DeleteMapping("/eliminar/{id}")
  public ResponseEntity<ApiResponse<CategoriaView>> eliminar(
      @PathVariable Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {
    soloAdmin();
    CategoriaView v = service.eliminar(id, ifMatch);
    return ResponseEntity.ok()
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
