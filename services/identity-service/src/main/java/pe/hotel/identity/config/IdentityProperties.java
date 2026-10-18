package pe.hotel.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades propias de identity
 * (docs/especificacion/infraestructura/configuracion.md § Por servicio). Los nombres de variable y
 * los valores por defecto son los que fija esa tabla; aquí no se inventa ninguno.
 */
@Component
@ConfigurationProperties(prefix = "hotel.identity")
public class IdentityProperties {

  /** Ruta del archivo PEM con la clave privada RS256. Vacía: clave efímera (solo dev/demo). */
  private String signingKeyFile = "";

  /** Identificador de la clave en el JWKS; su rotación se cambia aquí. */
  private String jwkKid = "v1";

  /** TTL del token de persona en minutos. */
  private long tokenTtlMinutes = 15;

  /** TTL del token máquina en minutos. */
  private long machineTokenTtlMinutes = 5;

  /** Límite de login por minuto y IP+cuenta. */
  private int loginRateLimitPerMinute = 5;

  /** Límite de registro por minuto y IP. */
  private int registroRateLimitPerMinute = 3;

  /** Antigüedad de la anonimización manual de personas inactivas (procedimiento auditado). */
  private int anonimizarInactivosAnios = 5;

  /**
   * Clave del HMAC con el que se calcula el {@code request_hash} de los requests con credenciales
   * (registro y cambio de clave). Nunca se guarda la clave en el hash ni en el payload de
   * idempotencia.
   */
  private String idempotencyHmacKey = "";

  public String getSigningKeyFile() {
    return signingKeyFile;
  }

  public void setSigningKeyFile(String signingKeyFile) {
    this.signingKeyFile = signingKeyFile;
  }

  public String getJwkKid() {
    return jwkKid;
  }

  public void setJwkKid(String jwkKid) {
    this.jwkKid = jwkKid;
  }

  public long getTokenTtlMinutes() {
    return tokenTtlMinutes;
  }

  public void setTokenTtlMinutes(long tokenTtlMinutes) {
    this.tokenTtlMinutes = tokenTtlMinutes;
  }

  public long getMachineTokenTtlMinutes() {
    return machineTokenTtlMinutes;
  }

  public void setMachineTokenTtlMinutes(long machineTokenTtlMinutes) {
    this.machineTokenTtlMinutes = machineTokenTtlMinutes;
  }

  public int getLoginRateLimitPerMinute() {
    return loginRateLimitPerMinute;
  }

  public void setLoginRateLimitPerMinute(int loginRateLimitPerMinute) {
    this.loginRateLimitPerMinute = loginRateLimitPerMinute;
  }

  public int getRegistroRateLimitPerMinute() {
    return registroRateLimitPerMinute;
  }

  public void setRegistroRateLimitPerMinute(int registroRateLimitPerMinute) {
    this.registroRateLimitPerMinute = registroRateLimitPerMinute;
  }

  public int getAnonimizarInactivosAnios() {
    return anonimizarInactivosAnios;
  }

  public void setAnonimizarInactivosAnios(int anonimizarInactivosAnios) {
    this.anonimizarInactivosAnios = anonimizarInactivosAnios;
  }

  public String getIdempotencyHmacKey() {
    return idempotencyHmacKey;
  }

  public void setIdempotencyHmacKey(String idempotencyHmacKey) {
    this.idempotencyHmacKey = idempotencyHmacKey;
  }
}