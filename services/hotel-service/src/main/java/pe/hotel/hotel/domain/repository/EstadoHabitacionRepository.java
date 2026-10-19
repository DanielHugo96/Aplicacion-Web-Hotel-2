package pe.hotel.hotel.domain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.EstadoHabitacion;

/** Repositorio del catalogo fijo de estados fisicos. */
public interface EstadoHabitacionRepository extends JpaRepository<EstadoHabitacion, Integer> {

  @Query("SELECT e FROM EstadoHabitacion e WHERE e.estado = true ORDER BY e.id ASC")
  List<EstadoHabitacion> findByEstadoTrueOrderByIdAsc();

  Optional<EstadoHabitacion> findByCodigo(String codigo);

  @Query("SELECT e FROM EstadoHabitacion e WHERE e.id = :id AND e.estado = true")
  Optional<EstadoHabitacion> findActiveById(@Param("id") Integer id);
}
