package pe.hotel.inventory.domain.model;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Embeddable;

/**
 * Clave compuesta de {@link ProductoSede}: (idProducto, idHotel).
 */
@Embeddable
public class ProductoSedeId implements Serializable {

  private Integer idProducto;
  private Integer idHotel;

  protected ProductoSedeId() {}

  public ProductoSedeId(Integer idProducto, Integer idHotel) {
    this.idProducto = idProducto;
    this.idHotel = idHotel;
  }

  public Integer getIdProducto() {
    return idProducto;
  }

  public Integer getIdHotel() {
    return idHotel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductoSedeId that)) return false;
    return Objects.equals(idProducto, that.idProducto) && Objects.equals(idHotel, that.idHotel);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idProducto, idHotel);
  }
}
