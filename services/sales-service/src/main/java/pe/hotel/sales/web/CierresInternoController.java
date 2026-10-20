package pe.hotel.sales.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.sales.domain.dto.ConfirmacionCierre;
import pe.hotel.sales.domain.dto.Receipt;
import pe.hotel.sales.domain.dto.ResumenCierre;
import pe.hotel.sales.domain.service.SalesService;
import pe.hotel.sales.platform.ApiResponse;
import pe.hotel.sales.platform.CorrelationId;
import pe.hotel.sales.platform.Meta;

/** Feign entrante de reception: resumen y confirmación de cierre de consumos. */
@RestController
@RequestMapping("/internal/cierres")
public class CierresInternoController {

  private final SalesService service;

  public CierresInternoController(SalesService service) {
    this.service = service;
  }

  @GetMapping("/{idRecepcion}/resumen")
  public ResponseEntity<ApiResponse<ResumenCierre>> resumen(
      @PathVariable("idRecepcion") Integer idRecepcion,
      @RequestParam("idHotel") Integer idHotel) {
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                service.resumenCierre(idRecepcion, idHotel), Meta.of(CorrelationId.current())));
  }

  @PostMapping("/confirmar")
  public ResponseEntity<ApiResponse<Receipt>> confirmar(
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ConfirmacionCierre body) {
    return ResponseEntity.ok()
        .body(
            ApiResponse.ok(
                service.confirmarCierre(body, idempotencyKey), Meta.of(CorrelationId.current())));
  }
}
