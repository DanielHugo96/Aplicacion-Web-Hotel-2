package pe.hotel.hotel.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.EstadoFisicoView;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.domain.service.EstadoHabitacionService;
import pe.hotel.hotel.platform.GlobalExceptionHandler;

/**
 * {@code PUT /api/estadohabitacion/actualizar/{id}} lee {@code descripcion} del cuerpo JSON del
 * contrato, no de un query param, y conserva 428/412. Contrato:
 * docs/contracts/openapi/hotel.yaml § /api/estadohabitacion/actualizar/{id}.
 */
class EstadoHabitacionControllerTests {

  private final EstadoHabitacionService service = mock(EstadoHabitacionService.class);
  private final CurrentUser admin = mock(CurrentUser.class);
  private MockMvc mockMvc;

  @BeforeEach
  void preparar() {
    when(admin.isAdmin()).thenReturn(true);
    EstadoHabitacionController controller = new EstadoHabitacionController();
    ReflectionTestUtils.setField(controller, "service", service);
    ReflectionTestUtils.setField(controller, "currentUser", admin);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void putConCuerpoYSinQueryParamActualizaLaEtiqueta() throws Exception {
    when(service.actualizar(eq(1), eq("Lista"), eq("\"1\"")))
        .thenReturn(new EstadoFisicoView(1, "LISTA", "Lista", true, 2L));

    mockMvc
        .perform(
            put("/api/estadohabitacion/actualizar/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .content("{\"descripcion\":\"Lista\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.descripcion").value("Lista"))
        .andExpect(jsonPath("$.data.version").value(2));

    verify(service).actualizar(1, "Lista", "\"1\"");
  }

  @Test
  void putSinIfMatchSigueEn428() throws Exception {
    when(service.actualizar(eq(1), eq("Lista"), eq(null)))
        .thenThrow(
            new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match obligatorio"));

    mockMvc
        .perform(
            put("/api/estadohabitacion/actualizar/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Lista\"}"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
  }

  @Test
  void putConVersionViejaSigueEn412() throws Exception {
    when(service.actualizar(eq(1), eq("Lista"), eq("\"9\"")))
        .thenThrow(
            new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "version distinta a la vigente"));

    mockMvc
        .perform(
            put("/api/estadohabitacion/actualizar/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"9\"")
                .content("{\"descripcion\":\"Lista\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("PRECONDITION_FAILED"));
  }

  @Test
  void putSinDescripcionEsValidationError() throws Exception {
    mockMvc
        .perform(
            put("/api/estadohabitacion/actualizar/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"1\"")
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.descripcion").isNotEmpty());
  }
}
