package pe.hotel.reporting;

import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReportingServiceApplication {

  public static void main(String[] args) {
    // Los logs estructurados emiten `service` en cada línea (ver back-plataforma-comunes).
    MDC.put("service", "reporting-service");
    SpringApplication.run(ReportingServiceApplication.class, args);
  }
}
