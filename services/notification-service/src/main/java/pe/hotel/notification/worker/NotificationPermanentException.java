package pe.hotel.notification.worker;

/** Error permanente del worker: no reintentable, va directo a la DLQ de su cola. */
public class NotificationPermanentException extends RuntimeException {

  public NotificationPermanentException(String message) {
    super(message);
  }
}