package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Allowlist de variables, escape de valores y rechazo de claves desconocidas o valores no escalares. */
class TemplateRendererTest {

  @Test
  void sustituyeLasVariablesDeSuPlantilla() {
    String cuerpo = "Hola {{nombre}}, tu salida de {{hotel}} ({{idRecepcion}}) total {{totalAlojamiento}}";
    Map<String, Object> valores = new HashMap<>();
    valores.put("nombre", "Cliente Uno");
    valores.put("hotel", "Hotel Demo Lima Centro");
    valores.put("idRecepcion", "1001");
    valores.put("totalAlojamiento", "70.00");
    valores.put("totalConsumos", "7.00");

    String texto = TemplateRenderer.render(cuerpo, "CHECKOUT", valores);

    assertThat(texto).isEqualTo("Hola Cliente Uno, tu salida de Hotel Demo Lima Centro (1001) total 70.00");
  }

  @Test
  void escapaElValorParaQueNoInyecteMarcadoresNiCabeceras() {
    Map<String, Object> valores = Map.of("nombre", "Eva\nBcc: otro@hotel.test {{hotel}}");

    String texto = TemplateRenderer.render("Hola {{nombre}}.", "BIENVENIDA", valores);

    // Cada llave se sustituye por un paréntesis: `{{hotel}}` queda `((hotel))` y ya no es un
    // marcador que el render pudiera volver a sustituir.
    assertThat(texto).isEqualTo("Hola Eva Bcc: otro@hotel.test ((hotel)).");
  }

  @Test
  void rechazaClavesDesconocidas() {
    Map<String, Object> valores = new HashMap<>();
    valores.put("nombre", "Cliente Uno");
    valores.put("html", "<b>hola</b>");

    assertThatThrownBy(() -> TemplateRenderer.render("Hola {{nombre}}", "BIENVENIDA", valores))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("variable desconocida");
  }

  @Test
  void rechazaValoresNoEscalares() {
    Map<String, Object> valores = new HashMap<>();
    valores.put("nombre", List.of("uno", "dos"));

    assertThatThrownBy(() -> TemplateRenderer.render("Hola {{nombre}}", "BIENVENIDA", valores))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("no escalar");
  }

  @Test
  void exigeLasVariablesObligatoriasDeLaPlantilla() {
    Map<String, Object> valores = Map.of("nombre", "Cliente Uno");

    assertThatThrownBy(() -> TemplateRenderer.render("Salida {{idRecepcion}}", "CHECKOUT", valores))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("falta la variable");
  }

  @Test
  void plantillaDesconocidaEsErrorPermanente() {
    assertThatThrownBy(() -> TemplateRenderer.variablesDe("PROMOCION"))
        .isInstanceOf(NotificationPermanentException.class)
        .hasMessageContaining("plantilla desconocida");
  }
}