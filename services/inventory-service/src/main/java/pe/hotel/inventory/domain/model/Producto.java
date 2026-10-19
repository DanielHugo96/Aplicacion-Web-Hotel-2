package pe.hotel.inventory.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Producto global de catálogo (idProducto). Su existencia, precio y estado son por sede en
 * {@link ProductoSede}. {@code estado} global permanece activo en el MVP; no hay baja global.
 */
@Entity
@Table(name = "producto")
public class Producto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String sku;
  private String nombre;
  private String detalle;
  private String imagenUrl;
  private boolean estado = true;

  @Version
  private Long version;

  private Instant createdAt;

  protected Producto() {}

  public Producto(
      String sku,
      String nombre,
      String detalle,
      String imagenUrl,
      boolean estado,
      Instant createdAt) {
    this.sku = sku;
    this.nombre = nombre;
    this.detalle = detalle;
    this.imagenUrl = imagenUrl;
    this.estado = estado;
    this.createdAt = createdAt;
  }

  public Integer getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getDetalle() {
    return detalle;
  }

  public void setDetalle(String detalle) {
    this.detalle = detalle;
  }

  public String getImagenUrl() {
    return imagenUrl;
  }

  public void setImagenUrl(String imagenUrl) {
    this.imagenUrl = imagenUrl;
  }

  public boolean isEstado() {
    return estado;
  }

  public void setEstado(boolean estado) {
    this.estado = estado;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
