package pe.hotel.inventory.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.hotel.inventory.client.HotelClient;
import pe.hotel.inventory.domain.security.CurrentUser;
import pe.hotel.inventory.domain.service.OutboxService;
import pe.hotel.inventory.domain.service.ProductoService;
import pe.hotel.inventory.platform.GlobalExceptionHandler;
import pe.hotel.inventory.support.ProductoJdbcFalso;

/**
 * Regresión del binding de {@code POST /api/producto/{id}/ajustes}: la sede llega en el cuerpo del
 * contrato ({@code idHotel}) y no en la query, y se conservan 201, 400, 403, 404, 412 y 428.
 *
 * <p>Ver docs/contracts/openapi/inventory.yaml § /api/producto/{id}/ajustes.
 */
class ProductoControllerAjustesTests {

  private static final String IDEM = "44444444-4444-4444-8444-444444444444";

  private final ProductoJdbcFalso jdbc =
      new ProductoJdbcFalso(new ProductoJdbcFalso.Fila(1, 1, 3));

  private final ProductoService service =
      new ProductoService(jdbc, mock(OutboxService.class), mock(HotelClient.class));

  private final CurrentUser admin = mock(CurrentUser.class);

  private MockMvc mockMvc;

  @BeforeEach
  void preparar() {
    when(admin.isAdmin()).thenReturn(true);
    when(admin.puedeAccederASede(anyInt())).thenReturn(true);
    ProductoController controller = new ProductoController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void ajusteConElCuerpoDelContratoYSinQueryResponde201() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/1/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":1,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Ajuste registrado"))
        .andExpect(jsonPath("$.data.ajusteId").isNotEmpty())
        .andExpect(jsonPath("$.data.cantidad").value(5))
        .andExpect(jsonPath("$.data.version").value(2));
  }

  @Test
  void ajusteConElCuerpoDelContratoYConQueryDeSedeSigueRespondiendo201() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/1/ajustes?idHotel=1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":1,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.cantidad").value(5));
  }

  @Test
  void ajusteSinIdHotelEnElCuerpoDevuelve400ValidationErrorYNo500() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/1/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.idHotel").isNotEmpty());
  }

  @Test
  void ajusteSinIfMatchSigueEn428() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/1/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":1,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  @Test
  void ajusteConVersionViejaSigueEn412() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/1/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"9\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":1,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_FAILED"));
  }

  @Test
  void ajusteDeUnProductoQueNoEstaEnLaSedeSigueEn404() throws Exception {
    mockMvc
        .perform(
            post("/api/producto/99/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":1,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void ajusteDeUnEmpleadoFueraDeSuSedeSigueEn403() throws Exception {
    CurrentUser empleado = mock(CurrentUser.class);
    when(empleado.isAdmin()).thenReturn(false);
    when(empleado.isEmpleado()).thenReturn(true);
    when(empleado.puedeAccederASede(2)).thenReturn(false);
    ProductoController controller = new ProductoController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", empleado);
    MockMvc conEmpleado =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    conEmpleado
        .perform(
            post("/api/producto/1/ajustes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", IDEM)
                .content("{\"idHotel\":2,\"delta\":2,\"motivo\":\"Verificacion del arreglo\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
