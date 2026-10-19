package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Proyeccion informativa de ocupacion (id_habitacion es PK, no hay version propia). */
@Entity
@Table(name = "ocupacion_projection")
public class OcupacionProjection {

  @Id
  @Column(name = "id_habitacion")
  private Integer idHabitacion;

  @Column(name = "id_recepcion")
  private Integer idRecepcion;

  @Column(name = "estado_estadia", nullable = false, length = 16)
  private String estadoEstadia = "DESCONOCIDA";

  @Column(name = "room_version", nullable = false)
  private Long roomVersion = 0L;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public OcupacionProjection() {}

  public OcupacionProjection(
      Integer idHabitacion, String estadoEstadia, Integer idRecepcion, Long roomVersion) {
    this.idHabitacion = idHabitacion;
    this.estadoEstadia = estadoEstadia;
    this.idRecepcion = idRecepcion;
    this.roomVersion = roomVersion;
  }

  public Integer getIdHabitacion() {
    return idHabitacion;
  }

  public void setIdHabitacion(Integer idHabitacion) {
    this.idHabitacion = idHabitacion;
  }

  public Integer getIdRecepcion() {
    return idRecepcion;
  }

  public void setIdRecepcion(Integer idRecepcion) {
    this.idRecepcion = idRecepcion;
  }

  public String getEstadoEstadia() {
    return estadoEstadia;
  }

  public void setEstadoEstadia(String estadoEstadia) {
    this.estadoEstadia = estadoEstadia;
  }

  public Long getRoomVersion() {
    return roomVersion;
  }

  public void setRoomVersion(Long roomVersion) {
    this.roomVersion = roomVersion;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
