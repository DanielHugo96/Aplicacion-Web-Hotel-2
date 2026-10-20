package pe.hotel.hotel.client;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import pe.hotel.hotel.platform.ApiResponse;

/**
 * Feign a reception: bloqueo durable de habitación con scope {@code bloqueos:write} (audiencia
 * {@code reception}). La ruta es interna y no pasa por el Gateway.
 */
@FeignClient(
    name = "reception-service",
    url = "${hotel.feign.url.reception:http://localhost:8083}",
    configuration = ReceptionClient.FeignConfig.class)
public interface ReceptionClient {

  @PostMapping("/internal/habitaciones/{id}/bloqueos")
  ApiResponse<BloqueoResultado> bloquear(
      @PathVariable("id") int idHabitacion, @RequestBody BloqueoRequest request);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @Bean
    RequestInterceptor receptionTokenInterceptor() {
      return template ->
          template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenPara("reception"));
    }
  }
}
