package pe.hotel.reception.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.hotel.reception.domain.dto.CalculoCierre;
import pe.hotel.reception.domain.dto.RecepcionView;
import pe.hotel.reception.messaging.Destination;
import pe.hotel.reception.messaging.MessageValidator;
import pe.hotel.reception.messaging.OutboxMessage;
import pe.hotel.reception.messaging.OutboxStore;

/** Las salidas del cierre validan contra su JSON Schema v1 antes de publicarse. */
class CierreOutboxSchemaTests {

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<OutboxMessage> emitted = new ArrayList<>();
  private final MessageValidator validator = new MessageValidator("schemas");
  private final ReceptionOutbox outbox =
      new ReceptionOutbox(
          new OutboxStore(null) {
            @Override
            public void enqueue(
                Destination destination,
                String channel,
                String partitionKey,
                String aggregateId,
                long aggregateVersion,
                String type,
                String payload) {
              emitted.add(
                  new OutboxMessage(
                      UUID.randomUUID(), destination, channel, partitionKey, aggregateId,
                      aggregateVersion, type, payload, Instant.now(), 0, Instant.now()));
            }
          },
          mapper);

  @Test
  void salidaLimpiezaYCheckoutValidanContraSusEsquemas() {
    RecepcionView cerrada = view("CERRADA", true, UUID.fromString(UUID_CLEANING), Instant.now());
    CalculoCierre calculo = calculo();

    outbox.emitirSalida(cerrada, 3, calculo, "EFECTIVO", Instant.now());
    outbox.emitirLimpieza(cerrada, 3, UUID.fromString(UUID_CLEANING));
    outbox.emitirCheckout(cerrada, calculo, "Hotel Demo Lima Centro");

    assertThat(emitted).hasSize(3);
    assertThat(emitted.get(0).destination()).isEqualTo(Destination.KAFKA);
    assertThat(emitted.get(0).type()).isEqualTo("SalidaRegistrada");
    assertThat(emitted.get(1).destination()).isEqualTo(Destination.RABBIT);
    assertThat(emitted.get(2).type()).isEqualTo("NotificacionCheckout");
    for (OutboxMessage message : emitted) {
      validator.validate(message);
    }
  }

  @Test
  void anulacionPublicaRecepcionActualizadaConOcupacionLibre() throws Exception {
    RecepcionView anulada = view("CERRADA", true, null, null);

    outbox.emitirAnulacion(anulada, 4);

    assertThat(emitted).hasSize(1);
    validator.validate(emitted.get(0));
    var node = mapper.readTree(emitted.get(0).payload());
    assertThat(node.path("type").asText()).isEqualTo("RecepcionActualizada");
    assertThat(node.path("payload").path("ocupacionActual").path("estadoGate").asText())
        .isEqualTo("LIBRE");
    assertThat(node.path("payload").path("archivada").asBoolean()).isTrue();
  }

  private static final String UUID_CLOSURE = "77777777-7777-4777-8777-777777777777";
  private static final String UUID_CLEANING = "44444444-4444-4444-8444-444444444444";

  private static CalculoCierre calculo() {
    return new CalculoCierre(
        new BigDecimal("70.00"), new BigDecimal("50.00"), new BigDecimal("0.00"),
        new BigDecimal("7.00"), new BigDecimal("7.00"), new BigDecimal("57.00"),
        new BigDecimal("77.00"), new BigDecimal("7.00"));
  }

  private RecepcionView view(
      String estadoEstadia, boolean archivada, UUID cleaningCycleId, Instant confirmacion) {
    Map<String, Object> cierre = new LinkedHashMap<>();
    cierre.put("closureId", UUID.fromString(UUID_CLOSURE));
    cierre.put("estado", "COMPLETADO");
    cierre.put("lastErrorCode", null);
    return new RecepcionView(
        1001, 1, 100, 101, "101", "Individual", "Primer piso", "Habitación de demostración",
        new BigDecimal("70.00"), "Cliente", "Uno", "DNI", "90000100", "cliente.uno@hotel.test",
        LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), confirmacion,
        new BigDecimal("70.00"), new BigDecimal("20.00"), "EFECTIVO", new BigDecimal("0.00"),
        new BigDecimal("70.00"), BigDecimal.ZERO, null, null, false, estadoEstadia, archivada,
        false, 3L, UUID.fromString(UUID_CLOSURE), cleaningCycleId, cierre);
  }
}
