package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Piso de una sede (maestro con baja lógica). */
@Entity
@Table(name = "piso")
public class Piso {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "id_hotel", nullable = false)
  private Hotel hotel;

  @Column(name = "descripcion", nullable = false, length = 80)
  private String descripcion;

  @Column(name = "estado", nullable = false)
  private Boolean estado = true;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  public Piso() {}

  public Piso(Hotel hotel, String descripcion) {
    this.hotel = hotel;
    this.descripcion = descripcion;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Hotel getHotel() {
    return hotel;
  }

  public void setHotel(Hotel hotel) {
    this.hotel = hotel;
  }

  public Integer getIdHotel() {
    return hotel == null ? null : hotel.getId();
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public Boolean getEstado() {
    return estado;
  }

  public void setEstado(Boolean estado) {
    this.estado = estado;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
