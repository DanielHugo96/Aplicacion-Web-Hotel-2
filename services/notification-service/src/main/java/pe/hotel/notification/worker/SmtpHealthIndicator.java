package pe.hotel.notification.worker;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * SMTP como degradación visible sin bloquear el negocio
 * (docs/especificacion/microservicios/notification/api.md § REST y
 * infraestructura/configuracion.md § Health y readiness por servicio): aparece en
 * {@code /actuator/health} como componente {@code smtp} y queda **fuera** del grupo
 * {@code readiness}, que solo exige PostgreSQL y RabbitMQ.
 */
public class SmtpHealthIndicator implements HealthIndicator {

  private final String host;
  private final int port;
  private final int timeoutMs;

  public SmtpHealthIndicator(String host, int port, int timeoutMs) {
    this.host = host;
    this.port = port;
    this.timeoutMs = timeoutMs;
  }

  @Override
  public Health health() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      return Health.up().withDetail("host", host).withDetail("port", port).build();
    } catch (Exception ex) {
      return Health.down()
          .withDetail("host", host)
          .withDetail("port", port)
          .withDetail("error", "SMTP no disponible; los jobs quedan en reintento")
          .build();
    }
  }
}