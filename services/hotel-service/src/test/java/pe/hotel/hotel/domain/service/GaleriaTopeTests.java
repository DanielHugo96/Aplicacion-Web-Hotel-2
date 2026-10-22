package pe.hotel.hotel.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import pe.hotel.hotel.domain.dto.CategoriaPublica;

/**
 * Tope de 10 de la galería pública por categoría ({@code CategoriaPublica.imagenes},
 * {@code maxItems: 10} en docs/contracts/openapi/hotel.yaml). La función SQL ya limita, y el service
 * lo vuelve a aplicar para que el contrato no dependa de una sola capa.
 */
class GaleriaTopeTests {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final CatalogoPublicoService service = new CatalogoPublicoService();

  @Test
  void unaCategoriaConDoceImagenesDevuelveDiez() {
    ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString(), eq("lima-centro"), eq(1)))
        .thenReturn(List.of(fila(12)));

    List<CategoriaPublica> categorias = service.listarCategorias("lima-centro", 1, null);

    assertThat(categorias).hasSize(1);
    assertThat(categorias.get(0).imagenes()).hasSize(10);
    assertThat(categorias.get(0).imagenes().get(0).url()).isEqualTo("/assets/habitaciones/foto-1.jpg");
    assertThat(categorias.get(0).imagenes().get(9).url()).isEqualTo("/assets/habitaciones/foto-10.jpg");
  }

  @Test
  void unaCategoriaConTresImagenesLasDevuelveTodas() {
    ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString(), eq("lima-centro"), eq(1)))
        .thenReturn(List.of(fila(3)));

    List<CategoriaPublica> categorias = service.listarCategorias("lima-centro", 1, null);

    assertThat(categorias.get(0).imagenes()).hasSize(3);
  }

  private static Map<String, Object> fila(int imagenes) {
    List<Map<String, Object>> galeria = new ArrayList<>();
    for (int i = 1; i <= imagenes; i++) {
      Map<String, Object> imagen = new LinkedHashMap<>();
      imagen.put("url", "/assets/habitaciones/foto-" + i + ".jpg");
      imagen.put("alt", "Imagen " + i);
      galeria.add(imagen);
    }
    Map<String, Object> fila = new LinkedHashMap<>();
    fila.put("id_categoria", 1);
    fila.put("descripcion", "Individual");
    fila.put("detalle", "Habitacion individual con cama de una plaza.");
    fila.put("capacidad", 1);
    fila.put("precio_desde", new java.math.BigDecimal("70.00"));
    fila.put("imagenes", galeria);
    return fila;
  }
}
