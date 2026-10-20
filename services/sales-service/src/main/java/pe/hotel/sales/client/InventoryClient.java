package pe.hotel.sales.client;

import feign.RequestInterceptor;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import pe.hotel.sales.domain.dto.DebitoRequest;
import pe.hotel.sales.domain.dto.DebitoResultado;
import pe.hotel.sales.domain.dto.MovimientoResumen;
import pe.hotel.sales.domain.dto.ProductoStock;
import pe.hotel.sales.platform.ApiResponse;

/**
 * Cliente Feign hacia inventory-service: catálogo autoritativo de la sede, débito atómico y
 * consulta del movimiento de una venta. Token máquina con audiencia {@code inventory} y scopes
 * {@code stock:read} / {@code stock:debit}.
 */
@FeignClient(
    name = "inventory-service",
    url = "${hotel.feign.url.inventory}",
    configuration = InventoryClient.FeignConfig.class)
public interface InventoryClient {

  @GetMapping("/internal/productos")
  ApiResponse<List<ProductoStock>> productos(
      @RequestParam("idHotel") Integer idHotel,
      @RequestParam("page") Integer page,
      @RequestParam("size") Integer size);

  @GetMapping("/internal/productos/{id}")
  ApiResponse<ProductoStock> producto(
      @PathVariable("id") Integer idProducto, @RequestParam("idHotel") Integer idHotel);

  @PostMapping("/internal/stock/descontar")
  ApiResponse<DebitoResultado> descontar(@RequestBody DebitoRequest debito);

  @GetMapping("/internal/stock/movimientos/{idVenta}")
  ApiResponse<MovimientoResumen> movimiento(@PathVariable("idVenta") Integer idVenta);

  class FeignConfig {

    private final MachineTokenProvider tokens;

    public FeignConfig(MachineTokenProvider tokens) {
      this.tokens = tokens;
    }

    @org.springframework.context.annotation.Bean
    RequestInterceptor tokenInterceptor() {
      return template -> {
        String token = tokens.tokenPara("sales", "inventory");
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      };
    }
  }
}
