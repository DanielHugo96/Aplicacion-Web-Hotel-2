package pe.hotel.inventory.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Registro terminal de stock de una venta (id_venta PK externo de sales_db).
 * Estados: DESCONTADO / RECHAZADO / RESTITUIDO. Nunca se reescribe el resultado una vez terminal.
 */
@Entity
@Table(name = "movimiento_venta")
public class MovimientoVenta {

  @Id
  private Integer idVenta;

  private Integer idHotel;

  private String requestHash;

  private String estado; // DESCONTADO | RECHAZADO | RESTITUIDO

  @Version
  private Long version;

  private Instant createdAt;

  private Instant updatedAt;

  protected MovimientoVenta() {}

  public MovimientoVenta(
      Integer idVenta,
      Integer idHotel,
      String requestHash,
      String estado,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.idVenta = idVenta;
    this.idHotel = idHotel;
    this.requestHash = requestHash;
    this.estado = estado;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Integer getIdVenta() {
    return idVenta;
  }

  public Integer getIdHotel() {
    return idHotel;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getEstado() {
    return estado;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
