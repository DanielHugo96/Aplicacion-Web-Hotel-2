package pe.hotel.reporting.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.hotel.reporting.config.ReportingProperties;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;

/** Validaciones de rango/paginación y consultas de los cinco reportes. */
@Service
public class ReportService {

  public record Listing<T>(List<T> data, long total) {}

  private final ReportRepository repository;
  private final ReportingProperties properties;

  public ReportService(ReportRepository repository, ReportingProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public Listing<ReportModels.BajoStock> productosBajoStock(
      int idHotel, int limite, int page, int size) {
    if (limite < 0) {
      throw validation("limite", "debe ser mayor o igual a 0");
    }
    validarPagina(page, size);
    List<ReportModels.BajoStock> all = repository.productosBajoStock(idHotel, limite);
    return new Listing<>(paginar(all, page, size), all.size());
  }

  public Listing<ReportModels.Venta> ventas(
      int idHotel, String inicio, String fin, int page, int size) {
    validarPagina(page, size);
    ReportRange.Range range =
        ReportRange.parse(inicio, fin, properties.getMaxRangeDays(), false);
    List<ReportModels.Venta> all =
        repository.ventas(idHotel, range.inicio(), range.fin());
    return new Listing<>(paginar(all, page, size), all.size());
  }

  public Listing<ReportModels.Ocupacion> ocupacion(
      int idHotel, String inicio, String fin, int page, int size) {
    validarPagina(page, size);
    ReportRange.Range range =
        ReportRange.parse(inicio, fin, properties.getMaxRangeDays(), true);
    List<ReportModels.Ocupacion> all =
        repository.ocupacion(idHotel, range.inicio(), range.fin());
    return new Listing<>(paginar(all, page, size), all.size());
  }

  public Listing<ReportModels.Cobro> cobros(
      int idHotel, String inicio, String fin, int page, int size) {
    validarPagina(page, size);
    ReportRange.Range range =
        ReportRange.parse(inicio, fin, properties.getMaxRangeDays(), false);
    List<ReportModels.Cobro> all = repository.cobros(idHotel, range.inicio(), range.fin());
    return new Listing<>(paginar(all, page, size), all.size());
  }

  public ReportModels.Dashboard dashboard(Integer idHotel) {
    List<Integer> hoteles = idHotel == null ? repository.hoteles() : List.of(idHotel);
    return repository.dashboard(hoteles, LocalDate.now(ReportRange.LIMA));
  }

  public static void validarPagina(int page, int size) {
    if (page < 0) {
      throw validation("page", "debe ser mayor o igual a 0");
    }
    if (size < 1 || size > 100) {
      throw validation("size", "debe estar entre 1 y 100");
    }
  }

  private static <T> List<T> paginar(List<T> all, int page, int size) {
    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());
    return all.subList(from, to);
  }

  private static ApiException validation(String field, String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, Map.of(field, message));
  }
}
