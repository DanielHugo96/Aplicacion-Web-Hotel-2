package pe.hotel.inventory.domain.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.hotel.inventory.domain.model.Producto;

/** Repositorio de productos globales de catálogo. */
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

  Optional<Producto> findBySku(String sku);

  boolean existsBySku(String sku);
}
