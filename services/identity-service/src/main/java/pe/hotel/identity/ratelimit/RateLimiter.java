package pe.hotel.identity.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Contadores de rate limit en memoria, por ventana de un minuto.
 *
 * <p>Un contador por proceso es lo que pide el alcance cerrado del MVP (sin Redis): con una sola
 * instancia de identity el límite es exacto; con varias habría que compartirlo, y ese es un problema
 * de otro plan, no de este. El límite público de 60/min del Gateway es otro contador, en otro sitio.
 *
 * <p>Ventana fija de 60 s alineada al minuto: el {@code Retry-After} que se devuelve son los
 * segundos que quedan hasta que empieza la ventana siguiente.
 */
@Component
public class RateLimiter {

  private static final long WINDOW_SECONDS = 60;

  /** Se purga lo viejo cuando el mapa crece; los contadores son de un minuto. */
  private static final int MAX_ENTRADAS = 10_000;

  private final ConcurrentMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

  /**
   * Registra un intento y dice si pasa.
   *
   * @param clave clave del contador (p. ej. IP+cuenta en login)
   * @param limite intentos admitidos por minuto; menos de 1 desactiva el límite
   */
  public Decision registrar(String clave, int limite) {
    if (limite < 1) {
      return new Decision(true, 0);
    }
    long ahora = Instant.now().getEpochSecond();
    long minuto = ahora / WINDOW_SECONDS;
    Ventana ventana = ventanas.compute(clave, (k, actual) ->
        actual == null || actual.minuto() != minuto ? new Ventana(minuto, 1) : actual.contado());
    if (ventanas.size() > MAX_ENTRADAS) {
      purgar(minuto);
    }
    long restantes = WINDOW_SECONDS - (ahora % WINDOW_SECONDS);
    return new Decision(ventana.conteo() <= limite, restantes);
  }

  private void purgar(long minutoActual) {
    ventanas.entrySet().removeIf(entrada -> entrada.getValue().minuto() < minutoActual);
  }

  /** Forget de un contador; lo usan las pruebas, no el flujo de negocio. */
  public void olvidar() {
    ventanas.clear();
  }

  /** Resultado de un intento. */
  public record Decision(boolean permitido, long retryAfterSegundos) {}

  private record Ventana(long minuto, int conteo) {
    Ventana contado() {
      return new Ventana(minuto, conteo + 1);
    }
  }
}