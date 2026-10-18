package pe.hotel.notification.worker;

import java.util.List;

/**
 * Reintentos TTL de RabbitMQ para los errores anteriores a la persistencia del job
 * (docs/especificacion/05-mensajeria.md § Reintentos y DLQ Rabbit). Tras agotarlos, el mensaje va
 * a la DLQ de su cola por el exchange {@code hotel.dlx.v1}.
 */
public final class RabbitRetryPolicy {

  /** TTL por intento fallido, en el orden de la spec. */
  public static final List<String> SUFIJOS = List.of("5s", "30s", "120s");

  private RabbitRetryPolicy() {}

  public static int reintentosDisponibles() {
    return SUFIJOS.size();
  }

  /**
   * Cola de reintento para el número de intentos ya fallidos ({@code intentosFallidos} = 0 en el
   * primer fallo). Devuelve {@code null} cuando ya no quedan reintentos: el mensaje va a la DLQ.
   */
  public static String colaDeReintento(String cola, int intentosFallidos) {
    if (intentosFallidos < 0 || intentosFallidos >= SUFIJOS.size()) {
      return null;
    }
    return cola + ".retry." + SUFIJOS.get(intentosFallidos);
  }

  public static String dlq(String cola) {
    return cola + ".dlq";
  }

  /** Contador propio de intentos del mensaje (cabecera), independiente de {@code x-death}. */
  public static final String CABECERA_INTENTOS = "x-notification-attempts";
}