package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Categoria global de habitaciones (maestro con baja lógica). */
@Entity
@Table(name = "categoria")
public class Categoria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @Column(name = "descripcion", nullable = false, unique = true, length = 80)
  private String descripcion;

  @Column(name = "detalle", nullable = false, length = 500)
  private String detalle;

  @Column(name = "capacidad", nullable = false)
  private Integer capacidad;

  @Column(name = "estado", nullable = false)
  private Boolean estado = true;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  public Categoria() {}

  public Categoria(String descripcion, String detalle, Integer capacidad) {
    this.descripcion = descripcion;
    this.detalle = detalle;
    this.capacidad = capacidad;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public String getDetalle() {
    return detalle;
  }

  public void setDetalle(String detalle) {
    this.detalle = detalle;
  }

  public Integer getCapacidad() {
    return capacidad;
  }

  public void setCapacidad(Integer capacidad) {
    this.capacidad = capacidad;
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
