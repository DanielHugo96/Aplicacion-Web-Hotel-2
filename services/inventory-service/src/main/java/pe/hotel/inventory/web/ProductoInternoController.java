package pe.hotel.inventory.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.inventory.domain.dto.ProductoView;
import pe.hotel.inventory.domain.service.ProductoService;
import pe.hotel.inventory.platform.ApiResponse;
import pe.hotel.inventory.platform.CorrelationId;
import pe.hotel.inventory.platform.Meta;

/**
 * Catálogo interno de productos para sales (rutas /internal/productos), solo con token máquina y
 * alcance {@code stock:read}.
 */
@RestController
@RequestMapping("/internal/productos")
public class ProductoInternoController {

  @Autowired(required = false) private ProductoService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ProductoView>>> listar(
      @RequestParam Integer idHotel,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    List<ProductoView> data = service.listar(idHotel, true);
    long total = data.size();
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                data, new Meta(CorrelationId.current(), page, size, total)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ProductoView>> obtener(
      @PathVariable Integer id, @RequestParam Integer idHotel) {
    ProductoView v = service.buscar(id, idHotel);
    return ResponseEntity.ok()
        .eTag(String.valueOf(v.version()))
        .body(ApiResponse.ok(v, Meta.of(CorrelationId.current())));
  }
}
