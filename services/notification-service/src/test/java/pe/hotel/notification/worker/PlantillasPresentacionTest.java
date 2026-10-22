package pe.hotel.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Plantillas v1 de la presentación real (docs/especificacion/microservicios/notification/
 * base-de-datos.md § Seeds): asunto «Bienvenido a Casa Sillar», pie «Notificación automática de
 * la cadena; no es un comprobante fiscal» y sin «Hotel Demo» ni «demostración».
 *
 * <p>La corrección vive en el seed {@code V901__notification_plantillas_presentacion.sql} y no en
 * {@code V900}: una migración aplicada no se reescribe
 * (docs/implementacion/decisiones/IA-back-notification-presentacion-real-01.md). Esta prueba lee
 * el fichero del classpath, así que falla si alguien cambia el texto o vuelve a tocar V900.
 */
class PlantillasPresentacionTest {

  private static final String SEED = "db/seed/dev/V901__notification_plantillas_presentacion.sql";
  private static final String PIE =
      "Notificación automática de la cadena; no es un comprobante fiscal.";
  private static final String ASUNTO_BIENVENIDA = "Bienvenido a Casa Sillar";

  /** Un {@code UPDATE template} del seed: tipo, asunto y cuerpo literal. */
  private record Sentencia(String tipo, String asunto, String cuerpo) {}

  private static final Pattern ASUNTO = Pattern.compile("subject\\s*=\\s*'([^']*)'");
  private static final Pattern CUERPO = Pattern.compile("body_text\\s*=\\s*E'((?:[^']|'')*)'");
  /** Un {@code UPDATE} completo. El cuerpo lleva {@code ;} dentro del literal, así que no se
   *  puede trocear el fichero por punto y coma. */
  private static final Pattern ACTUALIZACION =
      Pattern.compile(
          "UPDATE template.*?WHERE tipo = '([A-Z]+)'\\s*AND version = 1;", Pattern.DOTALL);

  private static List<Sentencia> sentencias;

  @BeforeAll
  static void leerSeed() throws IOException {
    String sql = recurso(SEED);
    sentencias = new ArrayList<>();
    Matcher actualizacion = ACTUALIZACION.matcher(sql);
    while (actualizacion.find()) {
      String bloque = actualizacion.group();
      Matcher asunto = ASUNTO.matcher(bloque);
      Matcher cuerpo = CUERPO.matcher(bloque);
      assertThat(asunto.find()).as("el seed fija el asunto de %s", actualizacion.group(1)).isTrue();
      assertThat(cuerpo.find()).as("el seed fija el cuerpo de %s", actualizacion.group(1)).isTrue();
      sentencias.add(
          new Sentencia(actualizacion.group(1), asunto.group(1), literal(cuerpo.group(1))));
    }
  }

  @Test
  void actualizaUnaSolaVezCadaPlantillaDeLaVersionUno() throws IOException {
    assertThat(sentencias).extracting(Sentencia::tipo)
        .containsExactlyInAnyOrder("BIENVENIDA", "CHECKIN", "CHECKOUT");
    assertThat(recurso(SEED)).doesNotContain("version = 2");
  }

  @Test
  void losAsuntosSonLosDeLaSpec() {
    assertThat(sentencias)
        .extracting(Sentencia::tipo, Sentencia::asunto)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("BIENVENIDA", ASUNTO_BIENVENIDA),
            org.assertj.core.groups.Tuple.tuple("CHECKIN", "Estadía registrada"),
            org.assertj.core.groups.Tuple.tuple("CHECKOUT", "Salida registrada"));
  }

  @Test
  void cadaCuerpoLlevaElPieYConservaLasVariablesDeSuPlantilla() {
    Map<String, List<String>> esperadas =
        Map.of(
            "BIENVENIDA", List.of("nombre"),
            "CHECKIN", List.of("nombre", "hotel", "idRecepcion", "fechaSalida"),
            "CHECKOUT",
                List.of("nombre", "hotel", "idRecepcion", "totalAlojamiento", "totalConsumos"));

    for (Sentencia s : sentencias) {
      assertThat(esperadas.get(s.tipo()))
          .as("allowlist del render para %s", s.tipo())
          .containsExactlyInAnyOrderElementsOf(TemplateRenderer.variablesDe(s.tipo()));
      assertThat(s.cuerpo()).as("pie de %s", s.tipo()).endsWith(PIE + "\n");
      for (String variable : esperadas.get(s.tipo())) {
        assertThat(s.cuerpo()).as("%s usa {{%s}}", s.tipo(), variable)
            .contains("{{" + variable + "}}");
      }
      // Solo las variables de su plantilla: ninguna ajena se cuela en el cuerpo.
      assertThat(marcadoresDe(s.cuerpo())).as("variables de %s", s.tipo())
          .containsExactlyInAnyOrderElementsOf(esperadas.get(s.tipo()));
    }
  }

  private static List<String> marcadoresDe(String cuerpo) {
    Matcher m = Pattern.compile("\\{\\{([a-zA-Z]+)}}").matcher(cuerpo);
    List<String> vistos = new ArrayList<>();
    while (m.find()) {
      vistos.add(m.group(1));
    }
    return vistos;
  }

  @Test
  void ningunCuerpoNiAsuntoConservaLaMarcaDeDemostracion() {
    for (Sentencia s : sentencias) {
      assertThat((s.asunto() + "\n" + s.cuerpo()).toLowerCase())
          .as("texto de %s", s.tipo())
          .doesNotContain("demo")
          .doesNotContain("demostración");
    }
  }

  /** El cuerpo real, con las variables escapadas, deja un correo listo y sin marcadores. */
  @Test
  void elCuerpoNuevoSeRenderizaSinMarcadoresNiMarcaDeDemostracion() {
    Map<String, Map<String, Object>> variables = new LinkedHashMap<>();
    variables.put("BIENVENIDA", Map.of("nombre", "Cliente Uno"));
    variables.put(
        "CHECKIN",
        Map.of(
            "nombre", "Cliente Uno",
            "hotel", "Casa Sillar Lima Centro",
            "idRecepcion", 1001,
            "fechaSalida", "2026-10-05"));
    variables.put(
        "CHECKOUT",
        Map.of(
            "nombre", "Cliente Uno",
            "hotel", "Casa Sillar Lima Centro",
            "idRecepcion", 1001,
            "totalAlojamiento", "70.00",
            "totalConsumos", "7.00"));

    for (Sentencia s : sentencias) {
      String texto = TemplateRenderer.render(s.cuerpo(), s.tipo(), variables.get(s.tipo()));
      assertThat(texto).as("render de %s", s.tipo())
          .doesNotContain("{{")
          .doesNotContain("}}")
          .contains(PIE)
          .contains("Cliente Uno");
      if (!"BIENVENIDA".equals(s.tipo())) {
        assertThat(texto).contains("Casa Sillar Lima Centro").doesNotContain("Hotel Demo");
      }
    }
  }

  private static String literal(String escapado) {
    return escapado.replace("''", "'").replace("\\n", "\n");
  }

  private static String recurso(String ruta) throws IOException {
    try (InputStream in = PlantillasPresentacionTest.class.getClassLoader().getResourceAsStream(ruta)) {
      assertThat(in).as("recurso %s en el classpath", ruta).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
