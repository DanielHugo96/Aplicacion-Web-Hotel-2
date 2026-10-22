package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/** Sede del hotel (maestro con baja lógica). */
@Entity
@Table(name = "hotel")
public class Hotel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @Column(name = "slug", nullable = false, unique = true, length = 80)
  private String slug;

  @Column(name = "nombre", nullable = false, length = 120)
  private String nombre;

  @Column(name = "descripcion", nullable = false, length = 300)
  private String descripcion;

  @Column(name = "direccion", nullable = false, length = 200)
  private String direccion;

  @Column(name = "ciudad", nullable = false, length = 80)
  private String ciudad;

  @Column(name = "telefono", nullable = false, length = 20)
  private String telefono;

  @Column(name = "correo_contacto", nullable = false, length = 254)
  private String correoContacto;

  /** Foto principal de la sede: opcional, y su descripcion viaja siempre con ella. */
  @Column(name = "imagen_url", length = 500)
  private String imagenUrl;

  @Column(name = "imagen_alt", length = 150)
  private String imagenAlt;

  @Column(name = "estado", nullable = false)
  private Boolean estado = true;

  @Column(name = "publicado", nullable = false)
  private Boolean publicado = false;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  public Hotel() {}

  public Hotel(
      String slug,
      String nombre,
      String descripcion,
      String direccion,
      String ciudad,
      String telefono,
      String correoContacto,
      String imagenUrl,
      String imagenAlt,
      Boolean publicado) {
    this.slug = slug;
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.direccion = direccion;
    this.ciudad = ciudad;
    this.telefono = telefono;
    this.correoContacto = correoContacto;
    this.imagenUrl = imagenUrl;
    this.imagenAlt = imagenAlt;
    this.publicado = publicado;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public String getDireccion() {
    return direccion;
  }

  public void setDireccion(String direccion) {
    this.direccion = direccion;
  }

  public String getCiudad() {
    return ciudad;
  }

  public void setCiudad(String ciudad) {
    this.ciudad = ciudad;
  }

  public String getTelefono() {
    return telefono;
  }

  public void setTelefono(String telefono) {
    this.telefono = telefono;
  }

  public String getCorreoContacto() {
    return correoContacto;
  }

  public void setCorreoContacto(String correoContacto) {
    this.correoContacto = correoContacto;
  }

  public String getImagenUrl() {
    return imagenUrl;
  }

  public void setImagenUrl(String imagenUrl) {
    this.imagenUrl = imagenUrl;
  }

  public String getImagenAlt() {
    return imagenAlt;
  }

  public void setImagenAlt(String imagenAlt) {
    this.imagenAlt = imagenAlt;
  }

  public Boolean getEstado() {
    return estado;
  }

  public void setEstado(Boolean estado) {
    this.estado = estado;
  }

  public Boolean getPublicado() {
    return publicado;
  }

  public void setPublicado(Boolean publicado) {
    this.publicado = publicado;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
