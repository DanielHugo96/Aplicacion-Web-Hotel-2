package pe.hotel.sales.messaging;

/**
 * Publicador de un destino del outbox. La plataforma aporta la implementación por broker; los
 * planes de dominio solo encolan mensajes.
 */
public interface OutboxPublisher {

  Destination destination();

  void publish(OutboxMessage message);

  /** Deriva un mensaje permanente inválido a su DLT (Kafka) o DLQ (Rabbit) sin reintentos. */
  void publishToDeadLetter(OutboxMessage message, String reason);
}
