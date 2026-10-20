package pe.hotel.hotel.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.CategoriaPublica;
import pe.hotel.hotel.domain.dto.HotelPublico;
import pe.hotel.hotel.domain.service.CatalogoPublicoService;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.Meta;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Catálogo público de solo lectura: cuatro GET sin token, Cache-Control public,max-age=60. */
@RestController
@RequestMapping("/api/public/hoteles")
public class HotelPublicoController {

  private static final String CACHE = "public,max-age=60";

  @Autowired(required = false)
  private CatalogoPublicoService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<HotelPublico>>> listar(
      @RequestParam(required = false) String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    validarPagina(page, size);
    List<HotelPublico> todos = service.listarHoteles(slug);
    List<HotelPublico> data = paginar(todos, page, size);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(CorrelationId.current(), page, size, todos.size())));
  }

  @GetMapping("/{slug}")
  public ResponseEntity<ApiResponse<HotelPublico>> detalle(@PathVariable String slug) {
    HotelPublico data = service.detalleHotel(slug);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(CorrelationId.current())));
  }

  @GetMapping("/{slug}/categorias")
  public ResponseEntity<ApiResponse<List<CategoriaPublica>>> categorias(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer idCategoria,
      @RequestParam(required = false) Integer capacidad) {
    validarPagina(page, size);
    List<CategoriaPublica> todas = service.listarCategorias(slug, idCategoria, capacidad);
    List<CategoriaPublica> data = paginar(todas, page, size);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(CorrelationId.current(), page, size, todas.size())));
  }

  @GetMapping("/{slug}/categorias/{idCategoria}")
  public ResponseEntity<ApiResponse<CategoriaPublica>> categoria(
      @PathVariable String slug, @PathVariable Integer idCategoria) {
    CategoriaPublica data = service.detalleCategoria(slug, idCategoria);
    return ResponseEntity.ok()
        .header("Cache-Control", CACHE)
        .body(ApiResponse.ok(data, Meta.of(CorrelationId.current())));
  }

  private void validarPagina(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(BAD_REQUEST, "page debe ser >= 0 y size estar entre 1 y 100");
    }
  }

  private <T> List<T> paginar(List<T> todos, int page, int size) {
    long desdeLargo = (long) page * size;
    int desde = (int) Math.min(desdeLargo, todos.size());
    int hasta = Math.min(desde + size, todos.size());
    return todos.subList(desde, hasta);
  }
}
