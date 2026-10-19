package pe.hotel.hotel.domain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.Hotel;

/** Repositorio de sedes. */
public interface HotelRepository extends JpaRepository<Hotel, Integer> {

  Optional<Hotel> findBySlug(String slug);

  boolean existsBySlug(String slug);

  @Query("SELECT h FROM Hotel h WHERE h.estado = :estado")
  List<Hotel> findByEstado(@Param("estado") Boolean estado);

  @Query(
      "SELECT h FROM Hotel h WHERE h.id IN :ids AND h.estado = :estado ORDER BY h.id ASC")
  List<Hotel> findByIdInAndEstado(
      @Param("ids") List<Integer> ids, @Param("estado") Boolean estado);

  @Query("SELECT h FROM Hotel h WHERE h.id = :id AND h.estado = true")
  Optional<Hotel> findActiveById(@Param("id") Integer id);
}
