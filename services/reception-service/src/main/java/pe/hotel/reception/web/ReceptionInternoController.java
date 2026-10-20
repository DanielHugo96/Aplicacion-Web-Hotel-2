package pe.hotel.reception.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.reception.domain.ReceptionService;
import pe.hotel.reception.domain.dto.BloqueoRequest;
import pe.hotel.reception.domain.dto.BloqueoResultado;
import pe.hotel.reception.domain.dto.OperacionEstado;
import pe.hotel.reception.domain.dto.OperacionRequest;
import pe.hotel.reception.domain.dto.ResumenRecepcion;
import pe.hotel.reception.platform.ApiResponse;
import pe.hotel.reception.platform.CorrelationId;
import pe.hotel.reception.platform.Meta;

/** Rutas /internal/** solo máquina (scope por ruta en SecurityConfig); no pasan por Gateway. */
@RestController
@RequestMapping("/internal")
public class ReceptionInternoController {

  private final ReceptionService service;

  public ReceptionInternoController(ReceptionService service) {
    this.service = service;
  }

  @GetMapping("/recepciones/{id}")
  public ResponseEntity<ApiResponse<ResumenRecepcion>> resumen(@PathVariable("id") int id) {
    return ResponseEntity.ok()
        .body(ApiResponse.ok(service.resumen(id), Meta.of(CorrelationId.current())));
  }

  @PostMapping("/recepciones/{id}/operaciones")
  public ResponseEntity<ApiResponse<OperacionEstado>> admitir(
      @PathVariable("id") int id, @RequestBody OperacionRequest request) {
    Map<String, Object> result = service.admitir(id, request);
    OperacionEstado estado =
        new OperacionEstado(
            request.operationId(), String.valueOf(result.get("tipo")), "ABIERTA");
    return ResponseEntity.ok()
        .body(ApiResponse.ok(estado, Meta.of(CorrelationId.current())));
  }

  @PostMapping("/habitaciones/{id}/bloqueos")
  public ResponseEntity<ApiResponse<BloqueoResultado>> bloquear(
      @PathVariable("id") int id, @RequestBody BloqueoRequest request) {
    Map<String, Object> result = service.bloquear(id, request);
    String decision = String.valueOf(result.get("decision"));
    if (!"ACTIVO".equals(decision)) {
      // La decisión RECHAZADO ya se persistió; el controlador devuelve 409 con GATE_NOT_AVAILABLE.
      throw new pe.hotel.reception.platform.ApiException(
          pe.hotel.reception.platform.ErrorCode.GATE_NOT_AVAILABLE,
          Map.of("blockId", String.valueOf(request.blockId())));
    }
    BloqueoResultado bloqueo =
        new BloqueoResultado(request.blockId(), id, "BLOQUEADA");
    return ResponseEntity.ok()
        .body(ApiResponse.ok("Habitación bloqueada", bloqueo, Meta.of(CorrelationId.current())));
  }
}
