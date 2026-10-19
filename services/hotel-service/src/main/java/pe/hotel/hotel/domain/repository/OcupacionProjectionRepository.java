package pe.hotel.hotel.domain.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.OcupacionProjection;

/** Proyeccion informativa de ocupacion (actualizada por el consumidor Kafka). */
public interface OcupacionProjectionRepository extends JpaRepository<OcupacionProjection, Integer> {

  Optional<OcupacionProjection> findById(Integer idHabitacion);

  @Modifying
  @Query(
      "UPDATE OcupacionProjection o SET o.estadoEstadia = :estado, o.idRecepcion = :recepcion, "
          + "o.roomVersion = :version, o.updatedAt = CURRENT_TIMESTAMP "
          + "WHERE o.id = :idHabitacion AND :version > o.roomVersion")
  int upsertIfNewer(
      @Param("idHabitacion") Integer idHabitacion,
      @Param("estado") String estado,
      @Param("recepcion") Integer idRecepcion,
      @Param("version") Long roomVersion);

  @Modifying
  @Query(
      "INSERT INTO OcupacionProjection (id, estadoEstadia, idRecepcion, roomVersion, updatedAt) "
          + "VALUES (:idHabitacion, :estado, :recepcion, :version, CURRENT_TIMESTAMP)")
  int insertIfAbsent(
      @Param("idHabitacion") Integer idHabitacion,
      @Param("estado") String estado,
      @Param("recepcion") Integer idRecepcion,
      @Param("version") Long roomVersion);
}
