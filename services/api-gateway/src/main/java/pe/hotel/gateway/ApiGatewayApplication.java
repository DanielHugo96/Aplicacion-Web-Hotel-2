package pe.hotel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pe.hotel.gateway.config.GatewayProperties;

@EnableConfigurationProperties(GatewayProperties.class)
@SpringBootApplication
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
