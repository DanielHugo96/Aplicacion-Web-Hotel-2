package pe.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * Paso 4: las rutas explícitas por prefijo apuntan al service id de Eureka
 * correcto; sin discovery-locator y sin rutas a notification, /internal,
 * Eureka ni /actuator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "management.server.port=0"})
class GatewayRoutesTest {

  @Autowired
  private RouteLocator routeLocator;

  @Autowired
  private org.springframework.cloud.gateway.config.GatewayProperties gatewayProps;

  @Test
  void destinosPorPrefijo() {
    Map<String, String> destinoPorPrefijo = new HashMap<>();
    for (var def : gatewayProps.getRoutes()) {
      String uri = def.getUri().toString();
      for (var pred : def.getPredicates()) {
        if ("Path".equals(pred.getName())) {
          for (String patterns : pred.getArgs().values()) {
            for (String pattern : patterns.split(",")) {
              destinoPorPrefijo.put(pattern.trim(), uri);
            }
          }
        }
      }
    }

    assertThat(destinoPorPrefijo.get("/api/auth/**")).isEqualTo("lb://identity-service");
    assertThat(destinoPorPrefijo.get("/api/persona/**")).isEqualTo("lb://identity-service");
    assertThat(destinoPorPrefijo.get("/api/tipopersona/**")).isEqualTo("lb://identity-service");
    assertThat(destinoPorPrefijo.get("/.well-known/jwks.json")).isEqualTo("lb://identity-service");
    assertThat(destinoPorPrefijo.get("/api/public/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/hotel/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/piso/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/categoria/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/habitacion/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/estadohabitacion/**")).isEqualTo("lb://hotel-service");
    assertThat(destinoPorPrefijo.get("/api/recepcion/**")).isEqualTo("lb://reception-service");
    assertThat(destinoPorPrefijo.get("/api/producto/**")).isEqualTo("lb://inventory-service");
    assertThat(destinoPorPrefijo.get("/api/carrito/**")).isEqualTo("lb://sales-service");
    assertThat(destinoPorPrefijo.get("/api/venta/**")).isEqualTo("lb://sales-service");
    assertThat(destinoPorPrefijo.get("/api/reportes/**")).isEqualTo("lb://reporting-service");

    // Ninguna ruta hacia notification, /internal, Eureka ni /actuator.
    String definiciones = gatewayProps.getRoutes().toString();
    assertThat(definiciones).doesNotContain("notification");
    assertThat(definiciones).doesNotContain("/internal");
    assertThat(definiciones).doesNotContain("/actuator");
    assertThat(definiciones).doesNotContain("/eureka");
  }

  @Autowired
  private org.springframework.core.env.Environment env;

  @Test
  void sinDiscoveryLocatorNiFiltrosDeReintentoOCache() {
    assertThat(env.getProperty("spring.cloud.gateway.server.webflux.discovery.locator.enabled"))
        .isEqualTo("false");
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    assertThat(routes).isNotNull();
    for (Route route : routes) {
      for (Object filter : route.getFilters()) {
        String nombre = filter.getClass().getName();
        assertThat(nombre).doesNotContain("Retry");
        assertThat(nombre).doesNotContain("Cache");
      }
    }
  }
}
