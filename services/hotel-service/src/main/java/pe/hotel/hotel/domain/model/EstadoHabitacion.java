package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Estado fisico fijo del catalogo: 1 LISTA, 2 EN_LIMPIEZA, 3 MANTENIMIENTO. */
@Entity
@Table(name = "estado_habitacion")
public class EstadoHabitacion {

  @Id
  @Column(name = "id")
  private Integer id;

  @Column(name = "codigo", nullable = false, unique = true, length = 30)
  private String codigo;

  @Column(name = "descripcion", nullable = false, length = 80)
  private String descripcion;

  @Column(name = "estado", nullable = false)
  private Boolean estado = true;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  public EstadoHabitacion() {}

  public EstadoHabitacion(Integer id, String codigo, String descripcion) {
    this.id = id;
    this.codigo = codigo;
    this.descripcion = descripcion;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getCodigo() {
    return codigo;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
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
