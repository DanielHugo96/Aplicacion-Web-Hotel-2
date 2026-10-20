package pe.hotel.sales.client;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import pe.hotel.sales.domain.dto.OperacionAdmisionRequest;
import pe.hotel.sales.domain.dto.OperacionEstado;
import pe.hotel.sales.domain.dto.RecepcionResumen;
import pe.hotel.sales.platform.ApiResponse;

/**
 * Cliente Feign hacia reception-service: resumen de la estadía y admisión de operaciones de
 * consumo. Token máquina con audiencia {@code reception} y scopes {@code recepciones:read} y
 * {@code operaciones:write}.
 */
@FeignClient(
    name = "reception-service",
    url = "${hotel.feign.url.reception}",
    configuration = ReceptionClient.FeignConfig.class)
public interface ReceptionClient {

  @GetMapping("/internal/recepciones/{id}")
  ApiResponse<RecepcionResumen> recepcion(@PathVariable("id") Integer idRecepcion);

  @PostMapping("/internal/recepciones/{id}/operaciones")
  ApiResponse<OperacionEstado> admitir(
      @PathVariable("id") Integer idRecepcion, @RequestBody OperacionAdmisionRequest request);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @org.springframework.context.annotation.Bean
    RequestInterceptor tokenInterceptor() {
      return template -> {
        String token = tokens.tokenPara("sales", "reception");
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      };
    }
  }
}
