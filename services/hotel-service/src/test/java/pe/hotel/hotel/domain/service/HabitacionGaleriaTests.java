package pe.hotel.hotel.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.model.Categoria;
import pe.hotel.hotel.domain.model.EstadoHabitacion;
import pe.hotel.hotel.domain.model.Habitacion;
import pe.hotel.hotel.domain.model.ImagenHabitacion;
import pe.hotel.hotel.domain.model.Piso;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.ImagenHabitacionRepository;
import pe.hotel.hotel.domain.security.CurrentUser;

/**
 * {@code GET /api/habitacion/buscar/{id}} devuelve {@code urlsImagenes} ordenadas por {@code orden}
 * y {@code []} cuando no hay imágenes (nunca {@code null}). Contrato: {@code Habitacion} de
 * docs/contracts/openapi/hotel.yaml.
 */
class HabitacionGaleriaTests {

  private final HabitacionRepository repository = mock(HabitacionRepository.class);
  private final ImagenHabitacionRepository imagenes = mock(ImagenHabitacionRepository.class);
  private final CurrentUser admin = mock(CurrentUser.class);
  private final HabitacionService service = new HabitacionService();

  @BeforeEach
  void preparar() {
    when(admin.isAdmin()).thenReturn(true);
    when(admin.puedeAccederASede(1)).thenReturn(true);
    ReflectionTestUtils.setField(service, "repository", repository);
    ReflectionTestUtils.setField(service, "imagenRepository", imagenes);
    ReflectionTestUtils.setField(service, "currentUser", admin);
  }

  @Test
  void buscarDevuelveLaGaleriaOrdenada() {
    Habitacion habitacion = habitacion(101);
    when(repository.findById(101)).thenReturn(Optional.of(habitacion));
    when(imagenes.findByHabitacionIdOrderByOrdenAsc(101))
        .thenReturn(
            List.of(
                new ImagenHabitacion(habitacion, "/assets/demo/hab-101-1.jpg", "Habitacion de demostracion", 1),
                new ImagenHabitacion(habitacion, "/assets/demo/hab-101-2.jpg", "Segunda imagen", 2)));

    HabitacionView view = service.buscar(101);

    assertThat(view.urlsImagenes()).hasSize(2);
    assertThat(view.urlsImagenes().get(0).url()).isEqualTo("/assets/demo/hab-101-1.jpg");
    assertThat(view.urlsImagenes().get(0).alt()).isEqualTo("Habitacion de demostracion");
    assertThat(view.urlsImagenes().get(0).orden()).isEqualTo(1);
    assertThat(view.urlsImagenes().get(1).orden()).isEqualTo(2);
  }

  @Test
  void buscarSinImagenesDevuelveListaVacia() {
    when(repository.findById(102)).thenReturn(Optional.of(habitacion(102)));
    when(imagenes.findByHabitacionIdOrderByOrdenAsc(102)).thenReturn(List.of());

    HabitacionView view = service.buscar(102);

    assertThat(view.urlsImagenes()).isNotNull().isEmpty();
  }

  private static Habitacion habitacion(Integer id) {
    EstadoHabitacion lista = new EstadoHabitacion(1, "LISTA", "Lista");
    Categoria categoria = new Categoria("Individual", "Habitacion individual", 1);
    categoria.setId(1);
    Piso piso = new Piso(null, "Primer piso");
    piso.setId(11);
    Habitacion h =
        new Habitacion(1, 11, categoria, String.valueOf(id), "Detalle", new BigDecimal("70.00"), lista);
    h.setId(id);
    h.setPiso(piso);
    return h;
  }
}
