package pe.hotel.inventory;

import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {

  public static void main(String[] args) {
    // Los logs estructurados emiten `service` en cada línea (ver back-plataforma-comunes).
    MDC.put("service", "inventory-service");
    SpringApplication.run(InventoryServiceApplication.class, args);
  }
}
