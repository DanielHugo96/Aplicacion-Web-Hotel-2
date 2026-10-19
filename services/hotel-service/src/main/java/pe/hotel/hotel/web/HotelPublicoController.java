package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.hotel.hotel.domain.dto.CategoriaPublica;
import pe.hotel.hotel.domain.dto.HotelPublico;
import pe.hotel.hotel.domain.service.CatalogoPublicoService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.Meta;

/** Catálogo público de solo lectura: cuatro GET sin token, Cache-Control public,max-age=60. */
@RestController
@RequestMapping("/api/public/hoteles")
public class HotelPublicoController {

  private static final String CACHE = "public,max-age=60";

  @Autowired(required = false)
  private CatalogoPublicoService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<HotelPublico>>> listar(
      @RequestParam(required = false) String slug) {
    List<HotelPublico> data = service.listarHoteles(slug);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(null)));
  }

  @GetMapping("/{slug}")
  public ResponseEntity<ApiResponse<HotelPublico>> detalle(@PathVariable String slug) {
    HotelPublico data = service.detalleHotel(slug);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(null)));
  }

  @GetMapping("/{slug}/categorias")
  public ResponseEntity<ApiResponse<List<CategoriaPublica>>> categorias(
      @PathVariable String slug,
      @RequestParam(required = false) Integer idCategoria,
      @RequestParam(required = false) Integer capacidad) {
    List<CategoriaPublica> data = service.listarCategorias(slug, idCategoria, capacidad);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(null)));
  }

  @GetMapping("/{slug}/categorias/{idCategoria}")
  public ResponseEntity<ApiResponse<CategoriaPublica>> categoria(
      @PathVariable String slug, @PathVariable Integer idCategoria) {
    CategoriaPublica data = service.detalleCategoria(slug, idCategoria);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(null)));
  }
}
