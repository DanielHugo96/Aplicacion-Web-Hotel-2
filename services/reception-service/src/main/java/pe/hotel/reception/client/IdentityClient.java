package pe.hotel.reception.client;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pe.hotel.reception.domain.dto.ClienteVigente;
import pe.hotel.reception.platform.ApiResponse;

/** Feign a identity: GET /internal/clientes/{id} con scope clientes:read (audiencia identity). */
@FeignClient(
    name = "identity-service",
    url = "${hotel.feign.url.identity:http://localhost:8081}",
    configuration = IdentityClient.FeignConfig.class)
public interface IdentityClient {

  @GetMapping("/internal/clientes/{id}")
  ApiResponse<ClienteVigente> clienteVigente(@PathVariable("id") int id);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @Bean
    RequestInterceptor identityTokenInterceptor() {
      return template ->
          template.header(
              HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenPara("identity"));
    }
  }
}
