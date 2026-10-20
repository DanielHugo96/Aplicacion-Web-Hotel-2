package pe.hotel.hotel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import pe.hotel.hotel.domain.dto.CategoriaView;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.dto.HotelView;
import pe.hotel.hotel.domain.dto.PisoView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.CategoriaService;
import pe.hotel.hotel.domain.service.HabitacionService;
import pe.hotel.hotel.domain.service.HotelService;
import pe.hotel.hotel.domain.service.PisoService;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ApiResponse;
import pe.hotel.hotel.platform.Pagination;

/**
 * Los cuatro listados del catálogo devuelven {@code meta.page}, {@code meta.size} y
 * {@code meta.totalElements} del contrato, y un {@code page}/{@code size} fuera de rango es
 * {@code VALIDATION_ERROR}. Contrato: {@code ApiMetaPage} de docs/contracts/openapi/hotel.yaml.
 */
class CatalogoPaginacionTests {

  private final CurrentUser admin = mock(CurrentUser.class);

  @BeforeEach
  void adminAutenticado() {
    when(admin.isAdmin()).thenReturn(true);
    when(admin.isEmpleado()).thenReturn(false);
  }

  @Test
  void hotelListarDevuelveMetaDePagina() {
    HotelService service = mock(HotelService.class);
    when(service.listar(isNull(), any(Pageable.class))).thenReturn(pagina());
    HotelController controller = new HotelController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);

    ApiResponse<List<HotelView>> body = controller.listar(0, 2, null).getBody();

    assertThat(body.meta().page()).isZero();
    assertThat(body.meta().size()).isEqualTo(2);
    assertThat(body.meta().totalElements()).isEqualTo(5L);
  }

  @Test
  void pisoListarDevuelveMetaDePagina() {
    PisoService service = mock(PisoService.class);
    when(service.listar(any(), isNull(), any(Pageable.class))).thenReturn(pagina());
    PisoController controller = new PisoController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);

    ApiResponse<List<PisoView>> body = controller.listar(1, 0, 2, null).getBody();

    assertThat(body.meta().page()).isZero();
    assertThat(body.meta().size()).isEqualTo(2);
    assertThat(body.meta().totalElements()).isEqualTo(5L);
  }

  @Test
  void categoriaListarDevuelveMetaDePagina() {
    CategoriaService service = mock(CategoriaService.class);
    when(service.listar(isNull(), any(Pageable.class))).thenReturn(pagina());
    CategoriaController controller = new CategoriaController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);

    ApiResponse<List<CategoriaView>> body = controller.listar(0, 2, null).getBody();

    assertThat(body.meta().page()).isZero();
    assertThat(body.meta().size()).isEqualTo(2);
    assertThat(body.meta().totalElements()).isEqualTo(5L);
  }

  @Test
  void habitacionListarDevuelveMetaDePagina() {
    HabitacionService service = mock(HabitacionService.class);
    when(service.listar(any(), any(), any(Pageable.class))).thenReturn(pagina());
    HabitacionController controller = new HabitacionController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);

    ApiResponse<List<HabitacionView>> body = controller.listar(1, null, 0, 2).getBody();

    assertThat(body.meta().page()).isZero();
    assertThat(body.meta().size()).isEqualTo(2);
    assertThat(body.meta().totalElements()).isEqualTo(5L);
  }

  @Test
  void pageNegativoEsValidationError() {
    assertThatThrownBy(() -> Pagination.validar(-1, 20))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("page");
            });
  }

  @Test
  void sizeCeroEsValidationError() {
    assertThatThrownBy(() -> Pagination.validar(0, 0))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("size");
            });
  }

  @Test
  void sizeMayorQueCienEsValidationError() {
    assertThatThrownBy(() -> Pagination.validar(0, 101))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("size");
            });
  }

  private static <T> Page<T> pagina() {
    return new PageImpl<>(List.of(), PageRequest.of(0, 2), 5);
  }
}
