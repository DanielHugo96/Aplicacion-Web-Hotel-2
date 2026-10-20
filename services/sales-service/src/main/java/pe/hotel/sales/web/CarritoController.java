package pe.hotel.sales.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.sales.domain.dto.CarritoItem;
import pe.hotel.sales.domain.service.SalesService;
import pe.hotel.sales.platform.ApiResponse;
import pe.hotel.sales.platform.CorrelationId;
import pe.hotel.sales.platform.Meta;

/** Rutas /api/carrito: upsert por producto, listado por estadía y borrado de línea. */
@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

  private final SalesService service;

  public CarritoController(SalesService service) {
    this.service = service;
  }

  @PostMapping("/agregar")
  public ResponseEntity<ApiResponse<CarritoItem>> agregar(
      @RequestParam("idRecepcion") Integer idRecepcion,
      @RequestParam("idProducto") Integer idProducto,
      @RequestParam("cantidad") Integer cantidad) {
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                service.agregarCarrito(idRecepcion, idProducto, cantidad),
                Meta.of(CorrelationId.current())));
  }

  @GetMapping("/listar/{idRecepcion}")
  public ResponseEntity<ApiResponse<List<CarritoItem>>> listar(
      @PathVariable("idRecepcion") Integer idRecepcion) {
    List<CarritoItem> items = service.listarCarrito(idRecepcion);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                items,
                new Meta(CorrelationId.current(), 0, items.size(), (long) items.size())));
  }

  @DeleteMapping("/eliminar")
  public ResponseEntity<Void> eliminar(
      @RequestParam("idRecepcion") Integer idRecepcion,
      @RequestParam("idProducto") Integer idProducto) {
    service.eliminarCarrito(idRecepcion, idProducto);
    return ResponseEntity.noContent().build();
  }
}
