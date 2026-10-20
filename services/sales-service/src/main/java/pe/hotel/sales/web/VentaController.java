package pe.hotel.sales.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import pe.hotel.sales.domain.dto.PagoRequest;
import pe.hotel.sales.domain.dto.VentaAlta;
import pe.hotel.sales.domain.dto.VentaView;
import pe.hotel.sales.domain.service.SalesService;
import pe.hotel.sales.platform.ApiResponse;
import pe.hotel.sales.platform.CorrelationId;
import pe.hotel.sales.platform.Meta;

/** Rutas /api/venta del contrato: catálogo, ventas, pago, anulación y polling. */
@RestController
@RequestMapping("/api/venta")
public class VentaController {

  private final SalesService service;

  public VentaController(SalesService service) {
    this.service = service;
  }

  @GetMapping("/catalogo")
  public ResponseEntity<ApiResponse<List<pe.hotel.sales.domain.dto.CatalogoItem>>> catalogo(
      @RequestParam("idRecepcion") Integer idRecepcion,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    var pagina = service.catalogo(idRecepcion, page, size);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                pagina.data(),
                new Meta(CorrelationId.current(), page, size, pagina.total())));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<VentaView>>> listar(
      @RequestParam("idHotel") Integer idHotel,
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) String estadoOperacion,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    var pagina = service.listarVentas(idHotel, estado, estadoOperacion, page, size);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                pagina.data(), new Meta(CorrelationId.current(), page, size, pagina.total())));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<VentaView>> crear(@Valid @RequestBody VentaAlta body) {
    SalesService.Resultado resultado = service.crear(body);
    if (resultado.status() == 201) {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/venta/buscar/" + resultado.venta().idVenta())
        .header("Retry-After", "2")
        .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/buscar/{id}")
  public ResponseEntity<ApiResponse<VentaView>> buscar(@PathVariable("id") Integer id) {
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.buscar(id), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/recepcion/{idRecepcion}")
  public ResponseEntity<ApiResponse<List<VentaView>>> porRecepcion(
      @PathVariable("idRecepcion") Integer idRecepcion,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    var pagina = service.ventasPorRecepcion(idRecepcion, page, size);
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                pagina.data(), new Meta(CorrelationId.current(), page, size, pagina.total())));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<VentaView>> pagar(
      @PathVariable("id") Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody PagoRequest body) {
    SalesService.Resultado resultado = service.pagar(id, body, ifMatch);
    if (resultado.status() == 200) {
      return ResponseEntity.ok()
          .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/venta/buscar/" + resultado.venta().idVenta())
        .header("Retry-After", "2")
        .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<VentaView>> anular(
      @PathVariable("id") Integer id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestParam("motivo") String motivo) {
    SalesService.Resultado resultado = service.anular(id, motivo, ifMatch);
    if (resultado.status() == 200) {
      return ResponseEntity.ok()
          .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/venta/buscar/" + resultado.venta().idVenta())
        .header("Retry-After", "2")
        .body(ApiResponse.ok(resultado.venta(), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/operaciones/{operationId}")
  public ResponseEntity<ApiResponse<pe.hotel.sales.domain.dto.OperacionVentaView>> operacion(
      @PathVariable("operationId") UUID operationId) {
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.operacion(operationId), Meta.of(CorrelationId.current())));
  }
}
