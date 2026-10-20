package pe.hotel.hotel.domain.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.ImagenHabitacion;

/** Repositorio de imagenes de la galeria de habitaciones. */
public interface ImagenHabitacionRepository extends JpaRepository<ImagenHabitacion, Integer> {

  @Query(
      "SELECT i FROM ImagenHabitacion i WHERE i.habitacion.id = :idHabitacion ORDER BY i.orden ASC")
  List<ImagenHabitacion> findByHabitacionIdOrderByOrdenAsc(@Param("idHabitacion") Integer idHabitacion);

  @Query(
      "SELECT i FROM ImagenHabitacion i WHERE i.habitacion.id IN :ids "
          + "ORDER BY i.habitacion.id ASC, i.orden ASC")
  List<ImagenHabitacion> findByHabitacionIdInOrderByOrdenAsc(@Param("ids") List<Integer> ids);

  @Modifying
  @Query("DELETE FROM ImagenHabitacion i WHERE i.habitacion.id = :idHabitacion")
  void deleteByHabitacionId(@Param("idHabitacion") Integer idHabitacion);
}
