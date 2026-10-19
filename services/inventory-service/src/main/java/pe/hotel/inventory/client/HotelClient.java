package pe.hotel.inventory.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente Feign hacia hotel-service para validar que una sede existe y está activa antes de crear
 * o activar una asignación de producto. Resuelve el nombre en Eureka (o la {@code url} configurada)
 * y no entra por Gateway.
 *
 * <p>El interceptor inyecta el token máquina con alcance {@code hoteles:read} para la audiencia
 * {@code hotel} (docs/especificacion/03-contrato-comun.md § Servicio a servicio).
 */
@FeignClient(
    name = "hotel-service",
    url = "${hotel.feign.url.hotel}",
    configuration = HotelClient.FeignConfig.class)
public interface HotelClient {

  /** Datos de una sede para validar que existe y está activa. 404 si no existe. */
  @GetMapping("/internal/hoteles/{id}")
  HotelSede datos(@PathVariable("id") Integer idHotel);

  record HotelSede(Integer idHotel, String nombre, boolean estado, Integer version) {}

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @org.springframework.context.annotation.Bean
    RequestInterceptor tokenInterceptor() {
      return template -> {
        String token = tokens.tokenPara("inventory", "hotel");
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      };
    }
  }
}
