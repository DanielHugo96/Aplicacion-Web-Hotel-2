package pe.hotel.hotel.domain.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.hotel.domain.model.Categoria;

/** Repositorio de categorías globales. */
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

  Optional<Categoria> findByDescripcion(String descripcion);

  boolean existsByDescripcion(String descripcion);

  @Query("SELECT c FROM Categoria c WHERE c.estado = :estado")
  List<Categoria> findByEstado(@Param("estado") Boolean estado);

  @Query("SELECT c FROM Categoria c WHERE c.id = :id AND c.estado = true")
  Optional<Categoria> findActiveById(@Param("id") Integer id);

  @Query("SELECT c FROM Categoria c ORDER BY c.id ASC")
  Page<Categoria> findPagedAll(Pageable pageable);

  @Query("SELECT c FROM Categoria c WHERE c.estado = :estado ORDER BY c.id ASC")
  Page<Categoria> findPagedByEstado(@Param("estado") Boolean estado, Pageable pageable);
}
