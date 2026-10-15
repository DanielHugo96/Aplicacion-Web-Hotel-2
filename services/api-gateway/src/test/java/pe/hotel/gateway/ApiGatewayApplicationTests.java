package pe.hotel.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eureka.client.enabled=false", "management.server.port=0"})
class ApiGatewayApplicationTests {

  @Test
  void contextLoads() {
  }
}
