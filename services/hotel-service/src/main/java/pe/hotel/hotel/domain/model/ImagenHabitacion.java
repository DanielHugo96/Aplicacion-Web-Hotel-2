package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Imagen de la galería de una habitación (máximo 10, orden único). */
@Entity
@Table(name = "imagen_habitacion")
public class ImagenHabitacion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "id_habitacion", nullable = false)
  private Habitacion habitacion;

  @Column(name = "url", nullable = false, length = 500)
  private String url;

  @Column(name = "alt", nullable = false, length = 150)
  private String alt;

  @Column(name = "orden", nullable = false)
  private Integer orden;

  public ImagenHabitacion() {}

  public ImagenHabitacion(Habitacion habitacion, String url, String alt, Integer orden) {
    this.habitacion = habitacion;
    this.url = url;
    this.alt = alt;
    this.orden = orden;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getIdHabitacion() {
    return habitacion == null ? null : habitacion.getId();
  }

  public Habitacion getHabitacion() {
    return habitacion;
  }

  public void setHabitacion(Habitacion habitacion) {
    this.habitacion = habitacion;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getAlt() {
    return alt;
  }

  public void setAlt(String alt) {
    this.alt = alt;
  }

  public Integer getOrden() {
    return orden;
  }

  public void setOrden(Integer orden) {
    this.orden = orden;
  }
}
