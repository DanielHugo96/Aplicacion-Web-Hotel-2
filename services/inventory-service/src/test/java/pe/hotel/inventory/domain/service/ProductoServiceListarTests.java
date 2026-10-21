package pe.hotel.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.hotel.inventory.client.HotelClient;
import pe.hotel.inventory.domain.dto.ProductoView;
import pe.hotel.inventory.support.ProductoJdbcFalso;

/**
 * Regresión del filtro {@code estado} de {@code GET /api/producto/listar}: el filtro es la baja
 * lógica por sede ({@code producto_sede.estado}), no el estado global del producto, y el
 * {@code estado} devuelto sigue siendo el efectivo (producto AND producto_sede).
 *
 * <p>Ver docs/contracts/openapi/inventory.yaml § /api/producto/listar y
 * docs/especificacion/microservicios/inventory/api.md §REST.
 */
class ProductoServiceListarTests {

  private final ProductoJdbcFalso jdbc =
      new ProductoJdbcFalso(
          new ProductoJdbcFalso.Fila(1, 1, 20),
          new ProductoJdbcFalso.Fila(2, 1, 5).estadoSede(false),
          new ProductoJdbcFalso.Fila(3, 2, 7));

  private final ProductoService service =
      new ProductoService(jdbc, mock(OutboxService.class), mock(HotelClient.class));

  @Test
  void listarConEstadoFalseDevuelveElProductoDadoDeBajaEnLaSede() {
    List<ProductoView> filas = service.listar(1, false);

    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).idProducto()).isEqualTo(2);
    assertThat(filas.get(0).estado()).isFalse();
  }

  @Test
  void listarConEstadoTrueNoDevuelveElProductoDadoDeBaja() {
    List<ProductoView> filas = service.listar(1, true);

    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).idProducto()).isEqualTo(1);
    assertThat(filas.get(0).estado()).isTrue();
  }

  @Test
  void listarSinEstadoDevuelveActivosYLadosConSuEstadoEfectivo() {
    List<ProductoView> filas = service.listar(1, null);

    assertThat(filas).extracting(ProductoView::idProducto).containsExactly(1, 2);
    assertThat(filas).extracting(ProductoView::estado).containsExactly(true, false);
  }

  @Test
  void elFiltroMiraLaBajaDeLaSedeYNoElEstadoGlobalDelProducto() {
    service.listar(1, false);

    assertThat(jdbc.ultimaConsulta())
        .contains("AND ps.estado = ?")
        .doesNotContain("p.estado = ?");
    assertThat(jdbc.ultimosParametros()).containsExactly(1, false);
  }

  @Test
  void elEstadoDevueltoEsElEfectivoProductoYProductoSede() {
    ProductoJdbcFalso conProductoInactivo =
        new ProductoJdbcFalso(
            new ProductoJdbcFalso.Fila(1, 1, 20).estadoProducto(false),
            new ProductoJdbcFalso.Fila(2, 1, 5).estadoSede(false));
    ProductoService otro =
        new ProductoService(conProductoInactivo, mock(OutboxService.class), mock(HotelClient.class));

    List<ProductoView> filas = otro.listar(1, null);

    assertThat(filas).hasSize(2);
    assertThat(filas).extracting(ProductoView::estado).containsOnly(false);
  }
}
