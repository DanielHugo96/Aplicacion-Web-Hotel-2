package pe.hotel.identity.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente Feign hacia hotel-service para validar sedes antes de asignarlas a una persona.
 * Resuelve el nombre en Eureka y no entra por Gateway.
 *
 * <p>El interceptor de request inyecta el token máquina de identity con alcance
 * {@code hoteles:read} para la audiencia {@code hotel} (docs/especificacion/03-contrato-comun.md
 * § Servicio a servicio).
 */
@FeignClient(
    name = "hotel-service",
    configuration = HotelClient.FeignConfig.class)
public interface HotelClient {

  /**
   * Datos de una sede para validar que existe y está activa antes de asignarla.
   *
   * @param idHotel identificador externo en hotel_db
   * @return sede con su estado; 404 si no existe, 503 si hotel no responde
   */
  @GetMapping("/internal/hoteles/{id}")
  HotelSede datos(@PathVariable("id") int idHotel);

  record HotelSede(int idHotel, String nombre, boolean estado, int version) {}

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @org.springframework.context.annotation.Bean
    RequestInterceptor tokenInterceptor() {
      return template -> {
        String token = tokens.tokenPara("identity", "hotel");
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      };
    }
  }
}
