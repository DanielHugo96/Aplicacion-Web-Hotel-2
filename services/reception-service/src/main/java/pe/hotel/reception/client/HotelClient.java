package pe.hotel.reception.client;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pe.hotel.reception.domain.dto.HabitacionInterna;
import pe.hotel.reception.platform.ApiResponse;

/** Feign a hotel: GET /internal/habitaciones/{id} con scope habitaciones:read (audiencia hotel). */
@FeignClient(
    name = "hotel-service",
    url = "${hotel.feign.url.hotel:http://localhost:8082}",
    configuration = HotelClient.FeignConfig.class)
public interface HotelClient {

  @GetMapping("/internal/habitaciones/{id}")
  ApiResponse<HabitacionInterna> habitacion(@PathVariable("id") int id);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @Bean
    RequestInterceptor hotelTokenInterceptor() {
      return template ->
          template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenPara("hotel"));
    }
  }
}
