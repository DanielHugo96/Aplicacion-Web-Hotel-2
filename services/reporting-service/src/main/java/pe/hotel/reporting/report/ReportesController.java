package pe.hotel.reporting.report;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.reporting.platform.ApiResponse;
import pe.hotel.reporting.platform.Meta;
import pe.hotel.reporting.report.security.SedeAccess;

/**
 * Los cinco reportes de solo lectura. El aislamiento por sede se resuelve antes de consultar:
 * ADMIN global, EMPLEADO solo sus sedes, CLIENTE 403 y sede ajena 404.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

  private final ReportService service;
  private final SedeAccess access;
  private final ReportingMetaService metaService;

  public ReportesController(
      ReportService service, SedeAccess access, ReportingMetaService metaService) {
    this.service = service;
    this.access = access;
    this.metaService = metaService;
  }

  @GetMapping("/productos-bajo-stock")
  public ApiResponse<List<ReportModels.BajoStock>> productosBajoStock(
      @RequestParam(name = "idHotel", required = false) Integer idHotel,
      @RequestParam(name = "limite", defaultValue = "5") Integer limite,
      @RequestParam(name = "page", defaultValue = "0") Integer page,
      @RequestParam(name = "size", defaultValue = "20") Integer size) {
    Integer hotel = access.require(idHotel, true);
    Meta meta = metaService.meta(page, size, null);
    ReportService.Listing<ReportModels.BajoStock> listing =
        service.productosBajoStock(hotel, limite, page, size);
    return ApiResponse.ok(
        listing.data(), withTotal(meta, page, size, listing.total()));
  }

  @GetMapping("/ventas")
  public ApiResponse<List<ReportModels.Venta>> ventas(
      @RequestParam(name = "idHotel", required = false) Integer idHotel,
      @RequestParam(name = "inicio", required = false) String inicio,
      @RequestParam(name = "fin", required = false) String fin,
      @RequestParam(name = "page", defaultValue = "0") Integer page,
      @RequestParam(name = "size", defaultValue = "20") Integer size) {
    Integer hotel = access.require(idHotel, true);
    Meta meta = metaService.meta(page, size, null);
    ReportService.Listing<ReportModels.Venta> listing =
        service.ventas(hotel, inicio, fin, page, size);
    return ApiResponse.ok(listing.data(), withTotal(meta, page, size, listing.total()));
  }

  @GetMapping("/ocupacion")
  public ApiResponse<List<ReportModels.Ocupacion>> ocupacion(
      @RequestParam(name = "idHotel", required = false) Integer idHotel,
      @RequestParam(name = "inicio", required = false) String inicio,
      @RequestParam(name = "fin", required = false) String fin,
      @RequestParam(name = "page", defaultValue = "0") Integer page,
      @RequestParam(name = "size", defaultValue = "20") Integer size) {
    Integer hotel = access.require(idHotel, true);
    Meta meta = metaService.meta(page, size, null);
    ReportService.Listing<ReportModels.Ocupacion> listing =
        service.ocupacion(hotel, inicio, fin, page, size);
    return ApiResponse.ok(listing.data(), withTotal(meta, page, size, listing.total()));
  }

  @GetMapping("/cobros")
  public ApiResponse<List<ReportModels.Cobro>> cobros(
      @RequestParam(name = "idHotel", required = false) Integer idHotel,
      @RequestParam(name = "inicio", required = false) String inicio,
      @RequestParam(name = "fin", required = false) String fin,
      @RequestParam(name = "page", defaultValue = "0") Integer page,
      @RequestParam(name = "size", defaultValue = "20") Integer size) {
    Integer hotel = access.require(idHotel, true);
    Meta meta = metaService.meta(page, size, null);
    ReportService.Listing<ReportModels.Cobro> listing =
        service.cobros(hotel, inicio, fin, page, size);
    return ApiResponse.ok(listing.data(), withTotal(meta, page, size, listing.total()));
  }

  @GetMapping("/dashboard")
  public ApiResponse<ReportModels.Dashboard> dashboard(
      @RequestParam(name = "idHotel", required = false) Integer idHotel) {
    Integer hotel = access.require(idHotel, false);
    Meta meta = metaService.meta(null, null, null);
    return ApiResponse.ok(service.dashboard(hotel), meta);
  }

  private Meta withTotal(Meta meta, int page, int size, long total) {
    return new Meta(
        meta.correlationId(),
        meta.updatedAt(),
        meta.consistency(),
        meta.degraded(),
        meta.bootstrapComplete(),
        page,
        size,
        total);
  }
}
