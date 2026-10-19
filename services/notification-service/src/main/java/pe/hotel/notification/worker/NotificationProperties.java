package pe.hotel.notification.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades del worker de notification
 * (docs/especificacion/infraestructura/configuracion.md § notification). Los valores por defecto
 * son los de la especificación y son sobrescribibles por entorno.
 */
@Component
@ConfigurationProperties(prefix = "hotel.notification")
public class NotificationProperties {

  private int leaseSeconds = 60;
  private int maxAttempts = 3;
  private int retentionSentDays = 30;
  private int auditRetentionDays = 90;
  private boolean smtpEnabled = false;
  private long dispatchIntervalMs = 2000;
  private long smtpTimeoutMs = 10000;
  private boolean admin = false;

  public int getLeaseSeconds() {
    return leaseSeconds;
  }

  public void setLeaseSeconds(int leaseSeconds) {
    this.leaseSeconds = leaseSeconds;
  }

  /** Reintentos SMTP del job después del intento inicial (4 intentos contando el inicial). */
  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public int getRetentionSentDays() {
    return retentionSentDays;
  }

  public void setRetentionSentDays(int retentionSentDays) {
    this.retentionSentDays = retentionSentDays;
  }

  public int getAuditRetentionDays() {
    return auditRetentionDays;
  }

  public void setAuditRetentionDays(int auditRetentionDays) {
    this.auditRetentionDays = auditRetentionDays;
  }

  /** Envío SMTP habilitado; por defecto false hasta configurar proveedor (dev lo activa). */
  public boolean isSmtpEnabled() {
    return smtpEnabled;
  }

  public void setSmtpEnabled(boolean smtpEnabled) {
    this.smtpEnabled = smtpEnabled;
  }

  public long getDispatchIntervalMs() {
    return dispatchIntervalMs;
  }

  public void setDispatchIntervalMs(long dispatchIntervalMs) {
    this.dispatchIntervalMs = dispatchIntervalMs;
  }

  public long getSmtpTimeoutMs() {
    return smtpTimeoutMs;
  }

  public void setSmtpTimeoutMs(long smtpTimeoutMs) {
    this.smtpTimeoutMs = smtpTimeoutMs;
  }

  /** Modo CLI del operador: sin web, sin listeners y sin despacho. */
  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }
}