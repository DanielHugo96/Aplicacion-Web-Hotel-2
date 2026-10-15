package pe.hotel.inventory.messaging;

/** Error de validación de esquema permanente: va directo a DLT/DLQ sin reintento infinito. */
public class MessageInvalidException extends RuntimeException {

  public MessageInvalidException(String message) {
    super(message);
  }
}
