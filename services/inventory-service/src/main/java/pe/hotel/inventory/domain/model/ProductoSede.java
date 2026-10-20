package pe.hotel.inventory.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
/**
 * Existencia, precio y estado de un producto en una sede (clave compuesta idProducto + idHotel).
 * La baja lógica conserva cantidad y movimientos; solo cambia {@code estado}.
 */
@Entity
@Table(name = "producto_sede")
@IdClass(ProductoSedeId.class)
public class ProductoSede {

  @Id
  @Column(name = "id_producto")
  private Integer idProducto;

  @Id
  @Column(name = "id_hotel")
  private Integer idHotel;

  @Column(name = "precio", precision = 12, scale = 2, nullable = false)
  private BigDecimal precio;

  @Column(nullable = false)
  private Integer cantidad;

  @Column(name = "umbral_bajo", nullable = false)
  private Integer umbralBajo;

  @Column(nullable = false)
  private boolean estado = true;

  @Version
  private Long version;

  protected ProductoSede() {}

  public ProductoSede(
      Integer idProducto,
      Integer idHotel,
      BigDecimal precio,
      Integer cantidad,
      Integer umbralBajo,
      boolean estado,
      Long version) {
    this.idProducto = idProducto;
    this.idHotel = idHotel;
    this.precio = precio;
    this.cantidad = cantidad;
    this.umbralBajo = umbralBajo;
    this.estado = estado;
    this.version = version;
  }

  public Integer getIdProducto() {
    return idProducto;
  }

  public Integer getIdHotel() {
    return idHotel;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public void setPrecio(BigDecimal precio) {
    this.precio = precio;
  }

  public Integer getCantidad() {
    return cantidad;
  }

  public void setCantidad(Integer cantidad) {
    this.cantidad = cantidad;
  }

  public Integer getUmbralBajo() {
    return umbralBajo;
  }

  public void setUmbralBajo(Integer umbralBajo) {
    this.umbralBajo = umbralBajo;
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
}
