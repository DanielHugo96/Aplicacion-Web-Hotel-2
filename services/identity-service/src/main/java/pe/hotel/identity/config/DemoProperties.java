package pe.hotel.identity.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Datos de demo: la clave de las personas y los secretos de los clientes de servicio. Solo las lee
 * el seeder de los perfiles {@code dev}/{@code demo}; en cualquier otro perfil no se siembran.
 *
 * <p>La contraseña común es {@code hotel.demo.password} (docs/especificacion/infraestructura/
 * configuracion.md § Comunes a todos los servicios). Los secretos de servicio no tienen nombre fijado
 * por la especificación: se declaran aquí como {@code hotel.demo.service-client-secrets.<servicio>}.
 */
@Component
@ConfigurationProperties(prefix = "hotel.demo")
public class DemoProperties {

  /** Clave de las personas demo; si falta, no se siembran credenciales de usuarios. */
  private String password = "";

  /** Secreto de cada cliente de servicio sembrado, indexado por nombre de servicio. */
  private Map<String, String> serviceClientSecrets = new LinkedHashMap<>();

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Map<String, String> getServiceClientSecrets() {
    return serviceClientSecrets;
  }

  public void setServiceClientSecrets(Map<String, String> serviceClientSecrets) {
    this.serviceClientSecrets =
        serviceClientSecrets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(serviceClientSecrets);
  }

  /** Secreto declarado para un servicio, o cadena vacía si no está definido en el entorno. */
  public String secretOf(String servicio) {
    return serviceClientSecrets.getOrDefault(servicio, "");
  }
}