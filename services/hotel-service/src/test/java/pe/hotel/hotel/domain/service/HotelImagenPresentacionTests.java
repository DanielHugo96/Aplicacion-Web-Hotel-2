package pe.hotel.hotel.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.hotel.hotel.domain.dto.HotelInput;
import pe.hotel.hotel.domain.dto.HotelView;
import pe.hotel.hotel.domain.model.Hotel;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.HotelRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.platform.ApiException;

/**
 * Foto principal de la sede: el par {@code imagenUrl}/{@code imagenAlt} de
 * {@code HotelInput} se valida con la regla del contrato común, viaja junto o no viaja, y se
 * devuelve tal cual en {@code Hotel} (docs/contracts/openapi/hotel.yaml).
 */
class HotelImagenPresentacionTests {

  private static final String URL = "/assets/sedes/lima-centro-fachada.jpg";
  private static final String ALT = "Fachada de piedra de la sede de Lima Centro al atardecer";

  private final HotelRepository repositorio = mock(HotelRepository.class);
  private final HabitacionRepository habitaciones = mock(HabitacionRepository.class);
  private final CurrentUser admin = mock(CurrentUser.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final HotelService service = new HotelService();

  @BeforeEach
  void preparar() {
    ReflectionTestUtils.setField(service, "repository", repositorio);
    ReflectionTestUtils.setField(service, "habitacionRepository", habitaciones);
    ReflectionTestUtils.setField(service, "currentUser", admin);
    ReflectionTestUtils.setField(service, "outboxService", outbox);
    when(repositorio.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void altaGuardaYDevuelveElParDeImagen() {
    HotelView vista = service.registrar(entrada(URL, ALT));

    assertThat(vista.imagenUrl()).isEqualTo(URL);
    assertThat(vista.imagenAlt()).isEqualTo(ALT);
    verify(outbox).emitirSedeSnapshot(any(Hotel.class));
  }

  @Test
  void altaSinImagenDevuelveAmbosCamposNulos() {
    HotelView vista = service.registrar(entrada(null, null));

    assertThat(vista.imagenUrl()).isNull();
    assertThat(vista.imagenAlt()).isNull();
  }

  @Test
  void unaUrlConEsquemaNoPermitidoEsValidationErrorSobreImagenUrl() {
    assertThatThrownBy(() -> service.registrar(entrada("http://cdn.ejemplo.test/foto.jpg", ALT)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("imagenUrl");
            });
    verify(repositorio, never()).save(any(Hotel.class));
  }

  @Test
  void unAltSinUrlEsValidationErrorSobreImagenAlt() {
    assertThatThrownBy(() -> service.registrar(entrada(null, ALT)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("imagenAlt");
            });
    verify(repositorio, never()).save(any(Hotel.class));
  }

  @Test
  void unaUrlSinAltEsValidationErrorSobreImagenAlt() {
    assertThatThrownBy(() -> service.registrar(entrada(URL, "  ")))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("imagenAlt");
            });
  }

  @Test
  void unaUrlHttpsDeLaListaPermitidaSeAcepta() {
    HotelView vista =
        service.registrar(entrada("https://images.casasillar.pe/sedes/lima-centro.jpg", ALT));

    assertThat(vista.imagenUrl()).isEqualTo("https://images.casasillar.pe/sedes/lima-centro.jpg");
  }

  @Test
  void unaUrlDeMasDe500CaracteresEsValidationErrorSobreImagenUrl() {
    String larga = "/assets/sedes/" + "a".repeat(500) + ".jpg";

    assertThatThrownBy(() -> service.registrar(entrada(larga, ALT)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("imagenUrl");
            });
  }

  @Test
  void unAltDeMasDe150CaracteresEsValidationErrorSobreImagenAlt() {
    assertThatThrownBy(() -> service.registrar(entrada(URL, "a".repeat(151))))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code().name()).isEqualTo("VALIDATION_ERROR");
              assertThat(ex.fields()).containsKey("imagenAlt");
            });
  }

  @Test
  void laEntidadHotelmappeaElParYLoDevuelveEnLaVista() {
    Hotel hotel = hotel();
    when(repositorio.findById(1)).thenReturn(java.util.Optional.of(hotel));
    when(admin.isAdmin()).thenReturn(true);

    HotelView vista = service.buscar(1);

    assertThat(vista.imagenUrl()).isEqualTo(URL);
    assertThat(vista.imagenAlt()).isEqualTo(ALT);
  }

  @Test
  void laEdicionReemplazaElParYConservaLaVersionDelRepositorio() {
    Hotel hotel = hotel();
    when(repositorio.findById(1)).thenReturn(java.util.Optional.of(hotel));
    when(admin.isAdmin()).thenReturn(true);
    when(repositorio.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

    HotelView vista =
        service.actualizar(1, entrada("/assets/sedes/lima-centro-fachada-v2.jpg", ALT), "1");

    assertThat(vista.imagenUrl()).isEqualTo("/assets/sedes/lima-centro-fachada-v2.jpg");
    assertThat(hotel.getImagenUrl()).isEqualTo("/assets/sedes/lima-centro-fachada-v2.jpg");
    assertThat(vista.version()).isEqualTo(1L);
  }

  private static Hotel hotel() {
    Hotel hotel =
        new Hotel(
            "lima-centro",
            "Casa Sillar Lima Centro",
            "Casona restaurada a dos cuadras de la Plaza Mayor.",
            "Jr. de la Union 540, Cercado de Lima",
            "Lima",
            "+51 1 428 6400",
            "contacto@casasillar.pe",
            URL,
            ALT,
            true);
    hotel.setId(1);
    hotel.setEstado(true);
    hotel.setVersion(1L);
    return hotel;
  }

  private static HotelInput entrada(String imagenUrl, String imagenAlt) {
    return new HotelInput(
        "lima-centro",
        "Casa Sillar Lima Centro",
        "Casona restaurada a dos cuadras de la Plaza Mayor.",
        "Jr. de la Union 540, Cercado de Lima",
        "Lima",
        "+51 1 428 6400",
        "contacto@casasillar.pe",
        imagenUrl,
        imagenAlt,
        true,
        true);
  }
}
