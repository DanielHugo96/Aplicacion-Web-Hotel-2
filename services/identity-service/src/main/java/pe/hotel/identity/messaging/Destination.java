package pe.hotel.identity.messaging;

/**
 * Broker destino de una fila de {@code outbox}; una fila por destino
 * (docs/especificacion/03-contrato-comun.md § Persistencia transversal).
 */
public enum Destination {
  KAFKA,
  RABBIT
}
