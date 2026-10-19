package pe.hotel.hotel.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** Habitación (catálogo): precio, galería, estado físico y proyección de ocupación. */
@Entity
@Table(name = "habitacion")
public class Habitacion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Integer id;

  @Column(name = "id_hotel", nullable = false)
  private Integer idHotel;

  @Column(name = "id_piso", nullable = false)
  private Integer idPiso;

  @ManyToOne(optional = false)
  @JoinColumn(name = "id_categoria", nullable = false)
  private Categoria categoria;

  @ManyToOne(optional = false)
  @JoinColumns({
    @JoinColumn(name = "id_piso", referencedColumnName = "id", insertable = false, updatable = false),
    @JoinColumn(name = "id_hotel", referencedColumnName = "id_hotel", insertable = false, updatable = false)
  })
  private Piso piso;

  @Column(name = "numero", nullable = false, length = 10)
  private String numero;

  @Column(name = "detalle", nullable = false, length = 500)
  private String detalle;

  @Column(name = "precio", nullable = false, precision = 12, scale = 2)
  private BigDecimal precio;

  @ManyToOne(optional = false)
  @JoinColumn(name = "id_estado_habitacion", nullable = false)
  private EstadoHabitacion estadoHabitacion;

  @Column(name = "estado", nullable = false)
  private Boolean estado = true;

  @Column(name = "publicada", nullable = false)
  private Boolean publicada = false;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  @Column(name = "last_cleaning_room_version", nullable = false)
  private Long lastCleaningRoomVersion = 0L;

  public Habitacion() {}

  public Habitacion(
      Integer idHotel,
      Integer idPiso,
      Categoria categoria,
      String numero,
      String detalle,
      BigDecimal precio,
      EstadoHabitacion estadoHabitacion) {
    this.idHotel = idHotel;
    this.idPiso = idPiso;
    this.categoria = categoria;
    this.numero = numero;
    this.detalle = detalle;
    this.precio = precio;
    this.estadoHabitacion = estadoHabitacion;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getIdHotel() {
    return idHotel;
  }

  public void setIdHotel(Integer idHotel) {
    this.idHotel = idHotel;
  }

  public Integer getIdPiso() {
    return idPiso;
  }

  public void setIdPiso(Integer idPiso) {
    this.idPiso = idPiso;
  }

  public Integer getIdCategoria() {
    return categoria == null ? null : categoria.getId();
  }

  public void setIdCategoria(Integer idCategoria) {
    if (categoria == null) {
      categoria = new Categoria();
    }
    categoria.setId(idCategoria);
  }

  public Categoria getCategoria() {
    return categoria;
  }

  public void setCategoria(Categoria categoria) {
    this.categoria = categoria;
  }

  public Piso getPiso() {
    return piso;
  }

  public void setPiso(Piso piso) {
    this.piso = piso;
  }

  public String getNumero() {
    return numero;
  }

  public void setNumero(String numero) {
    this.numero = numero;
  }

  public String getDetalle() {
    return detalle;
  }

  public void setDetalle(String detalle) {
    this.detalle = detalle;
  }

  public BigDecimal getPrecio() {
    return precio;
  }

  public void setPrecio(BigDecimal precio) {
    this.precio = precio;
  }

  public EstadoHabitacion getEstadoHabitacion() {
    return estadoHabitacion;
  }

  public void setEstadoHabitacion(EstadoHabitacion estadoHabitacion) {
    this.estadoHabitacion = estadoHabitacion;
  }

  public Integer getIdEstadoHabitacion() {
    return estadoHabitacion == null ? null : estadoHabitacion.getId();
  }

  public Boolean getEstado() {
    return estado;
  }

  public void setEstado(Boolean estado) {
    this.estado = estado;
  }

  public Boolean getPublicada() {
    return publicada;
  }

  public void setPublicada(Boolean publicada) {
    this.publicada = publicada;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public Long getLastCleaningRoomVersion() {
    return lastCleaningRoomVersion;
  }

  public void setLastCleaningRoomVersion(Long lastCleaningRoomVersion) {
    this.lastCleaningRoomVersion = lastCleaningRoomVersion;
  }
}
