package pe.hotel.reporting.report;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportConfig {

  /** Siempre existe: en pruebas sin Kafka arranca conectado y no hay monitor. */
  @Bean
  ConnectivityState connectivityState() {
    return new ConnectivityState();
  }
}
