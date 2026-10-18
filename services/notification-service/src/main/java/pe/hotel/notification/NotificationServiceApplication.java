package pe.hotel.notification;

import java.util.Arrays;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import pe.hotel.notification.worker.AdminCli;

@SpringBootApplication
public class NotificationServiceApplication {

  public static void main(String[] args) {
    // Los logs estructurados emiten `service` en cada línea (ver back-plataforma-comunes).
    MDC.put("service", "notification-service");

    // `--admin` es la CLI local del operador: sin web, sin listeners y sin despacho.
    if (Arrays.asList(args).contains("--admin")) {
      SpringApplication cli = new SpringApplication(NotificationServiceApplication.class);
      cli.setWebApplicationType(WebApplicationType.NONE);
      cli.setAdditionalProfiles("admin");
      try (ConfigurableApplicationContext contexto = cli.run(args)) {
        int codigo = contexto.getBean(AdminCli.class).run(args, System.out);
        System.exit(SpringApplication.exit(contexto, () -> codigo));
      }
      return;
    }

    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}