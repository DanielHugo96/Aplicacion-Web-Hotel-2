package pe.hotel.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias del Gateway (nombres de
 * docs/especificacion/infraestructura/configuracion.md §Por servicio).
 */
@ConfigurationProperties(prefix = "hotel.gateway")
public class GatewayProperties {

  private final RateLimit rateLimit = new RateLimit();
  private final Route route = new Route();
  private long maxBodyBytes = 1048576;
  private final Jwt jwt = new Jwt();

  public RateLimit getRateLimit() {
    return rateLimit;
  }

  public Route getRoute() {
    return route;
  }

  public long getMaxBodyBytes() {
    return maxBodyBytes;
  }

  public void setMaxBodyBytes(long maxBodyBytes) {
    this.maxBodyBytes = maxBodyBytes;
  }

  public Jwt getJwt() {
    return jwt;
  }

  public static class RateLimit {
    /** Límite público por IP en rutas públicas (req/min). */
    private int publicRpm = 60;

    public int getPublicRpm() {
      return publicRpm;
    }

    public void setPublicRpm(int publicRpm) {
      this.publicRpm = publicRpm;
    }
  }

  public static class Route {
    private long defaultConnectTimeoutMs = 2000;
    private long defaultReadTimeoutMs = 5000;

    public long getDefaultConnectTimeoutMs() {
      return defaultConnectTimeoutMs;
    }

    public void setDefaultConnectTimeoutMs(long defaultConnectTimeoutMs) {
      this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
    }

    public long getDefaultReadTimeoutMs() {
      return defaultReadTimeoutMs;
    }

    public void setDefaultReadTimeoutMs(long defaultReadTimeoutMs) {
      this.defaultReadTimeoutMs = defaultReadTimeoutMs;
    }
  }

  public static class Jwt {
    private String issuer = "hotel-identity";
    private String jwkSetUri = "http://identity-service/.well-known/jwks.json";
    private String audience = "hotel-api";

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    public String getJwkSetUri() {
      return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
    }

    public String getAudience() {
      return audience;
    }

    public void setAudience(String audience) {
      this.audience = audience;
    }
  }
}
