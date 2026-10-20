package pe.hotel.hotel.domain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.Habitacion;

/** Repositorio de habitaciones. */
public interface HabitacionRepository extends JpaRepository<Habitacion, Integer> {

  @Query(
      "SELECT h FROM Habitacion h WHERE h.idHotel = :idHotel AND h.estado = true ORDER BY h.id ASC")
  List<Habitacion> findByIdHotelAndEstadoTrueOrderByIdAsc(@Param("idHotel") Integer idHotel);

  @Query(
      "SELECT h FROM Habitacion h WHERE h.idHotel = :idHotel AND h.categoria.id = :idCategoria "
          + "AND h.estado = true ORDER BY h.id ASC")
  List<Habitacion> findByIdHotelAndIdCategoriaAndEstadoTrueOrderByIdAsc(
      @Param("idHotel") Integer idHotel, @Param("idCategoria") Integer idCategoria);

  @Query("SELECT h FROM Habitacion h WHERE h.id = :id AND h.idHotel = :idHotel AND h.estado = true")
  Optional<Habitacion> findActiveByIdAndHotel(
      @Param("id") Integer id, @Param("idHotel") Integer idHotel);

  @Query(
      "SELECT COUNT(h) > 0 FROM Habitacion h WHERE h.idHotel = :idHotel AND h.numero = :numero AND h.estado = true")
  boolean existsActiveByIdHotelAndNumero(
      @Param("idHotel") Integer idHotel, @Param("numero") String numero);

  @Query(
      "SELECT COUNT(h) > 0 FROM Habitacion h WHERE h.categoria.id = :idCategoria AND h.estado = true")
  boolean countByIdCategoriaEstadoTrue(@Param("idCategoria") Integer idCategoria);

  @Query(
      "SELECT h FROM Habitacion h WHERE h.id = :id AND h.idHotel = :idHotel")
  Optional<Habitacion> findByIdAndHotel(
      @Param("id") Integer id, @Param("idHotel") Integer idHotel);

  long countByIdHotelAndEstadoTrue(@Param("idHotel") Integer idHotel);

  @Query(
      "SELECT COUNT(h) > 0 FROM Habitacion h WHERE h.idPiso = :idPiso AND h.idHotel = :idHotel AND h.estado = true")
  boolean existsActiveByIdPisoAndIdHotel(
      @Param("idPiso") Integer idPiso, @Param("idHotel") Integer idHotel);

  @Query(
      "SELECT h FROM Habitacion h WHERE h.categoria.id = :idCategoria AND h.estado = true ORDER BY h.id ASC")
  List<Habitacion> findByIdCategoriaAndEstadoTrueOrderByIdAsc(@Param("idCategoria") Integer idCategoria);

  @Query(
      "SELECT h FROM Habitacion h WHERE h.idPiso = :idPiso AND h.idHotel = :idHotel AND h.estado = true ORDER BY h.id ASC")
  List<Habitacion> findByIdPisoAndIdHotelAndEstadoTrueOrderByIdAsc(
      @Param("idPiso") Integer idPiso, @Param("idHotel") Integer idHotel);

  @Query("SELECT h FROM Habitacion h WHERE h.idHotel = :idHotel AND h.estado = true ORDER BY h.id ASC")
  Page<Habitacion> findPagedByHotelAndEstado(
      @Param("idHotel") Integer idHotel, Pageable pageable);

  @Query(
      "SELECT h FROM Habitacion h WHERE h.idHotel = :idHotel AND h.categoria.id = :idCategoria "
          + "AND h.estado = true ORDER BY h.id ASC")
  Page<Habitacion> findPagedByHotelAndCategoriaAndEstado(
      @Param("idHotel") Integer idHotel,
      @Param("idCategoria") Integer idCategoria,
      Pageable pageable);
}
