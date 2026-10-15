package pe.hotel.hotel;

import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HotelServiceApplication {

  public static void main(String[] args) {
    // Los logs estructurados emiten `service` en cada línea (ver back-plataforma-comunes).
    MDC.put("service", "hotel-service");
    SpringApplication.run(HotelServiceApplication.class, args);
  }
}
