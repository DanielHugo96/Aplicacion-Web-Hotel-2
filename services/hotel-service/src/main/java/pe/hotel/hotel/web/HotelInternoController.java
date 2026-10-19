package pe.hotel.hotel.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.hotel.domain.dto.HabitacionInterna;
import pe.hotel.hotel.domain.dto.HotelInterno;
import pe.hotel.hotel.domain.service.HotelInternoService;
import pe.hotel.hotel.platform.ApiError;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.CorrelationId;
import pe.hotel.hotel.platform.ErrorCode;
import pe.hotel.hotel.platform.Meta;

/** Rutas internas /internal/** para servicios (token maquina por scope, sin Gateway). */
@RestController
@RequestMapping("/internal")
public class HotelInternoController {

  @Autowired(required = false)
  private HotelInternoService service;

  @GetMapping("/habitaciones/{id}")
  public ResponseEntity<?> habitacion(@PathVariable Integer id) {
    if (service == null) {
      return ResponseEntity.notFound().build();
    }
    try {
      HabitacionInterna data = service.habitacionInterna(id);
      return ResponseEntity.ok()
          .body(ApiResponse.ok(data, Meta.of(CorrelationId.current())));
    } catch (ApiException ex) {
      return error(ex.code());
    }
  }

  @GetMapping("/hoteles/{id}")
  public ResponseEntity<?> hotel(@PathVariable Integer id) {
    if (service == null) {
      return ResponseEntity.notFound().build();
    }
    try {
      HotelInterno data = service.hotelInterno(id);
      return ResponseEntity.ok()
          .body(ApiResponse.ok(data, Meta.of(CorrelationId.current())));
    } catch (ApiException ex) {
      return error(ex.code());
    }
  }

  private ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
    Meta meta = Meta.of(CorrelationId.current());
    ApiResponse<Void> body =
        ApiResponse.fail(code, ApiError.of(code, Map.of()), meta);
    return ResponseEntity.status(code.status()).body(body);
  }
}
