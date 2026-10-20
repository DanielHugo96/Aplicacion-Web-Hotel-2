package pe.hotel.hotel.domain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.Piso;

/** Repositorio de pisos por sede. */
public interface PisoRepository extends JpaRepository<Piso, Integer> {

  @Query(
      "SELECT p FROM Piso p WHERE p.hotel.id = :idHotel AND p.estado = true ORDER BY p.id ASC")
  List<Piso> findByHotelIdAndEstadoTrueOrderByIdAsc(@Param("idHotel") Integer idHotel);

  @Query(
      "SELECT p FROM Piso p WHERE p.id = :id AND p.hotel.id = :idHotel AND p.estado = true")
  Optional<Piso> findActiveByIdAndHotel(
      @Param("id") Integer id, @Param("idHotel") Integer idHotel);

  @Query(
      "SELECT p FROM Piso p WHERE p.hotel.id = :idHotel AND p.descripcion = :descripcion")
  Optional<Piso> findByHotelIdAndDescripcion(
      @Param("idHotel") Integer idHotel, @Param("descripcion") String descripcion);

  @Query(
      "SELECT p FROM Piso p WHERE p.hotel.id = :idHotel AND p.estado = :estado ORDER BY p.id ASC")
  Page<Piso> findPagedByHotelIdAndEstado(
      @Param("idHotel") Integer idHotel, @Param("estado") Boolean estado, Pageable pageable);
}
