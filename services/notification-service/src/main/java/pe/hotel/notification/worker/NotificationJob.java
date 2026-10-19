package pe.hotel.notification.worker;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila de {@code notification_job}
 * (docs/especificacion/microservicios/notification/base-de-datos.md § Tablas). {@code variables}
 * viaja como texto JSONB: el render de la plantilla lo parsea y valida contra su allowlist.
 */
public record NotificationJob(
    UUID commandId,
    String tipo,
    Integer idHotel,
    int idPersona,
    Integer idRecepcion,
    String destinatario,
    int templateVersion,
    String variables,
    String estado,
    int attempts,
    OffsetDateTime nextAttemptAt,
    OffsetDateTime leaseUntil,
    UUID leaseToken,
    String messageId,
    OffsetDateTime createdAt,
    OffsetDateTime sentAt,
    String lastErrorCode) {}