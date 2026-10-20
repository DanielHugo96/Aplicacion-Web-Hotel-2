package pe.hotel.reception.client;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import pe.hotel.reception.domain.dto.ConfirmacionCierre;
import pe.hotel.reception.domain.dto.ReceiptSales;
import pe.hotel.reception.domain.dto.ResumenCierreSales;
import pe.hotel.reception.platform.ApiResponse;

/**
 * Feign a sales para el cierre de consumos: resumen y confirmación idempotente, con audiencia
 * {@code sales} y scope {@code cierres:write} (docs/especificacion/03-contrato-comun.md
 * § Servicio a servicio).
 */
@FeignClient(
    name = "sales-service",
    url = "${hotel.feign.url.sales:http://localhost:8084}",
    configuration = SalesClient.FeignConfig.class)
public interface SalesClient {

  @GetMapping("/internal/cierres/{idRecepcion}/resumen")
  ApiResponse<ResumenCierreSales> resumen(
      @PathVariable("idRecepcion") int idRecepcion, @RequestParam("idHotel") int idHotel);

  @PostMapping("/internal/cierres/confirmar")
  ApiResponse<ReceiptSales> confirmar(
      @RequestHeader("Idempotency-Key") String closureId,
      @RequestBody ConfirmacionCierre body);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @Bean
    RequestInterceptor salesTokenInterceptor() {
      return template ->
          template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenPara("sales"));
    }
  }
}
