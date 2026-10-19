package pe.hotel.inventory.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.inventory.domain.dto.AjusteRequest;
import pe.hotel.inventory.domain.dto.AjusteResultado;
import pe.hotel.inventory.domain.dto.ProductoAlta;
import pe.hotel.inventory.domain.dto.ProductoUpdate;
import pe.hotel.inventory.domain.dto.ProductoView;
import pe.hotel.inventory.domain.dto.SedeAsignacion;
import pe.hotel.inventory.domain.security.CurrentUser;
import pe.hotel.inventory.domain.service.ProductoService;
import pe.hotel.inventory.platform.ApiResponse;
import pe.hotel.inventory.platform.CorrelationId;
import pe.hotel.inventory.platform.Meta;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;
import static org.springframework.http.HttpStatus.*;

/** CRUD de productos y existencias por sede (A muta; A/E lee su sede). */
@RestController
@RequestMapping("/api/producto")
public class ProductoController {

  @Autowired(required = false) private ProductoService service;
  @Autowired(required = false) private CurrentUser currentUser;

  @GetMapping("/listar")
  public ResponseEntity<ApiResponse<List<ProductoView>>> listar(
      @RequestParam Integer idHotel, @RequestParam(required = false) Boolean estado) {
    soloLecturaPrivada();
    verificarSede(idHotel);
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.listar(idHotel, estado), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<ProductoView>> buscar(
      @PathVariable Integer id, @RequestParam Integer idHotel) {
    soloLecturaPrivada();
    verificarSede(idHotel);
    ProductoView v = service.buscar(id, idHotel);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/registrar")
  public ResponseEntity<ApiResponse<ProductoView>> registrar(@RequestBody ProductoAlta alta) {
    soloAdmin();
    ProductoView v = service.registrar(alta, currentUser.actor() != null ? parseActor(currentUser.actor()) : null);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok("Producto registrado", v, Meta.of(CorrelationId.current())));
  }

  @PutMapping("/actualizar/{id}")
  public ResponseEntity<ApiResponse<ProductoView>> actualizar(
      @PathVariable Integer id,
      @RequestParam Integer idHotel,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody ProductoUpdate update) {
    soloAdmin();
    ProductoView v =
        service.actualizar(
            id, idHotel, update, ifMatch, parseProductoVersion(update.productoVersion()));
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @DeleteMapping("/eliminar/{id}")
  public ResponseEntity<ApiResponse<ProductoView>> eliminar(
      @PathVariable Integer id,
      @RequestParam Integer idHotel,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
    soloAdmin();
    ProductoView v = service.eliminar(id, idHotel, ifMatch);
    return ResponseEntity.ok()
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/{id}/sedes")
  public ResponseEntity<ApiResponse<ProductoView>> asignarSede(
      @PathVariable Integer id, @RequestBody SedeAsignacion asignacion) {
    soloAdmin();
    ProductoView v = service.asignarSede(id, asignacion, null);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok("Producto asignado a sede", v, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/{id}/ajustes")
  public ResponseEntity<ApiResponse<AjusteResultado>> ajustar(
      @PathVariable Integer id,
      @RequestParam Integer idHotel,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody AjusteRequest request) {
    soloAdmin();
    AjusteResultado r = service.ajustar(id, idHotel, ifMatch, request, null);
    return ResponseEntity.status(CREATED)
        .body(ApiResponse.ok("Ajuste registrado", r, Meta.of(CorrelationId.current())));
  }

  @GetMapping("/{id}/movimientos")
  public ResponseEntity<ApiResponse<List<pe.hotel.inventory.domain.dto.MovimientoView>>> movimientos(
      @PathVariable Integer id,
      @RequestParam Integer idHotel,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size,
      @RequestParam(required = false) String inicio,
      @RequestParam(required = false) String fin) {
    soloLecturaPrivada();
    verificarSede(idHotel);
    List<pe.hotel.inventory.domain.dto.MovimientoView> data =
        service.movimientos(id, idHotel, page, size, inicio, fin);
    long total = data.size();
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                data,
                new Meta(CorrelationId.current(), page, size, total)));
  }

  private void soloAdmin() {
    if (currentUser == null || !currentUser.isAdmin()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private void soloLecturaPrivada() {
    if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isEmpleado())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private void verificarSede(Integer idHotel) {
    if (currentUser != null && !currentUser.puedeAccederASede(idHotel)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private Integer parseActor(String actor) {
    try {
      return Integer.valueOf(actor);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer parseProductoVersion(Integer v) {
    return v;
  }
}
