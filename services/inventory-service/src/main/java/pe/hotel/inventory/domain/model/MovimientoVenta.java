package pe.hotel.inventory.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro terminal de stock de una venta (id_venta PK externo de sales_db).
 * Estados: DESCONTADO / RECHAZADO / RESTITUIDO. Nunca se reescribe el resultado una vez terminal.
 */
@Entity
@Table(name = "movimiento_venta")
public class MovimientoVenta {

  @Id
  @Column(name = "id_venta")
  private Integer idVenta;

  @Column(name = "id_hotel")
  private Integer idHotel;

  @Column(name = "request_hash", length = 64)
  @JdbcTypeCode(SqlTypes.CHAR)
  private String requestHash;

  @Column(length = 16)
  private String estado; // DESCONTADO | RECHAZADO | RESTITUIDO

  @Version
  private Long version;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
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
