package pe.hotel.notification.worker;

import java.time.Instant;
import pe.hotel.notification.messaging.Destination;
import pe.hotel.notification.messaging.MessageInvalidException;
import pe.hotel.notification.messaging.MessageValidator;
import pe.hotel.notification.messaging.OutboxMessage;

/**
 * Adapta el validador de esquemas de la plataforma de mensajería al consumo de notification: el
 * schema se resuelve por destino, cola y tipo
 * ({@code notificacion.<cola>.<tipo>.v1.schema.json}). Un mensaje que no valida es error
 * permanente y va directo a la DLQ.
 */
public class RabbitSchemaValidator implements SchemaValidator {

  private final MessageValidator validator;

  public RabbitSchemaValidator(MessageValidator validator) {
    this.validator = validator;
  }

  @Override
  public void validar(String cola, NotificationCommand comando) {
    try {
      validator.validate(
          new OutboxMessage(
              comando.commandId(),
              Destination.RABBIT,
              cola,
              null,
              comando.commandId().toString(),
              1L,
              comando.type(),
              comando.body(),
              Instant.now(),
              0,
              Instant.now()));
    } catch (MessageInvalidException invalid) {
      throw new NotificationPermanentException(invalid.getMessage());
    }
  }
}