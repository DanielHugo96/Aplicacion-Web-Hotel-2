package pe.hotel.notification.worker;

/**
 * Validación del comando contra su JSON Schema v1 antes de encolar
 * (docs/especificacion/microservicios/notification/api.md § RabbitMQ). Se abstrae para poder
 * probar el listener sin el classpath de esquemas.
 */
public interface SchemaValidator {

  void validar(String cola, NotificationCommand comando);
}