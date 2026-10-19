package pe.hotel.inventory.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import pe.hotel.inventory.domain.dto.Debito;
import pe.hotel.inventory.domain.dto.DebitoResultado;
import pe.hotel.inventory.domain.dto.MovimientoResumen;
import pe.hotel.inventory.domain.service.StockService;
import pe.hotel.inventory.domain.service.StockService.DescontarResultado;
import pe.hotel.inventory.domain.service.StockService.Estado;
import pe.hotel.inventory.platform.ApiResponse;
import pe.hotel.inventory.platform.CorrelationId;
import pe.hotel.inventory.platform.Meta;
import pe.hotel.inventory.platform.ApiException;
import pe.hotel.inventory.platform.ErrorCode;
import java.util.Map;
import static org.springframework.http.HttpStatus.*;

/**
 * Rutas Feign entrantes de inventory, solo para sales con token máquina y alcance
 * {@code stock:read} / {@code stock:debit} (seguridad en {@link pe.hotel.inventory.platform.SecurityConfig}).
 */
@RestController
@RequestMapping("/internal/stock")
public class StockInternoController {

  @Autowired(required = false) private StockService stockService;

  @PostMapping("/descontar")
  public ResponseEntity<ApiResponse<DebitoResultado>> descontar(
      @Valid @RequestBody Debito debito) {
    DescontarResultado resultado = stockService.descontar(debito);
    switch (resultado.estado()) {
      case DESCONTADO -> {
        return ResponseEntity.ok()
            .body(
                ApiResponse.ok(
                    "Stock descontado", resultado.resultado(), Meta.of(CorrelationId.current())));
      }
      case RECHAZADO -> {
        return ResponseEntity.status(CONFLICT)
            .body(
                ApiResponse.fail(
                    resultado.errorCode(),
                    new pe.hotel.inventory.platform.ApiError(
                        resultado.errorCode().name(), resultado.fields()),
                    Meta.of(CorrelationId.current())));
      }
      case IDEMPOTENCY_CONFLICT ->
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_CONFLICT,
            Map.of("Idempotency-Key", "mismo idVenta con hash distinto al del primer débito"));
    }
    throw new ApiException(ErrorCode.INTERNAL_ERROR);
  }

  @GetMapping("/movimientos/{idVenta}")
  public ResponseEntity<ApiResponse<MovimientoResumen>> movimientoPorVenta(
      @PathVariable Integer idVenta) {
    MovimientoResumen resumen = stockService.movimientoPorVenta(idVenta);
    if (resumen == null) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    return ResponseEntity.ok()
        .body(ApiResponse.ok(resumen, Meta.of(CorrelationId.current())));
  }
}
