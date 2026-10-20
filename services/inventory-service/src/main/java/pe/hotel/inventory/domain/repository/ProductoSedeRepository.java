package pe.hotel.inventory.domain.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.hotel.inventory.domain.model.ProductoSede;
import pe.hotel.inventory.domain.model.ProductoSedeId;

/** Repositorio de existencias de producto por sede. */
public interface ProductoSedeRepository extends JpaRepository<ProductoSede, ProductoSedeId> {

  Optional<ProductoSede> findByIdProductoAndIdHotel(Integer idProducto, Integer idHotel);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT ps FROM ProductoSede ps WHERE ps.idProducto = :idProducto AND ps.idHotel = :idHotel")
  Optional<ProductoSede> findByIdProductoAndIdHotelForUpdate(
      @Param("idProducto") Integer idProducto, @Param("idHotel") Integer idHotel);

  List<ProductoSede> findByIdHotelAndEstado(Integer idHotel, boolean estado);

  boolean existsByIdProductoAndIdHotelAndEstadoTrue(Integer idProducto, Integer idHotel);
}
