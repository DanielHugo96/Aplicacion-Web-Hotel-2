package pe.hotel.hotel.domain.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.CategoriaInput;
import pe.hotel.hotel.domain.dto.CategoriaView;
import pe.hotel.hotel.domain.model.Categoria;
import pe.hotel.hotel.domain.repository.CategoriaRepository;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.OperacionHabitacionRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;
import static org.springframework.http.HttpStatus.*;

/** Casos de uso de categorías globales. */
@Service
@Transactional(readOnly = true)
public class CategoriaService {

  @Autowired(required = false)
  private CategoriaRepository repository;
  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private CurrentUser currentUser;
  @Autowired(required = false)
  private OutboxService outboxService;
  @Autowired(required = false)
  private OperacionHabitacionRepository operacionRepository;

  public Page<CategoriaView> listar(Boolean estado, Pageable pageable) {
    Page<Categoria> cats =
        estado == null
            ? repository.findPagedAll(pageable)
            : repository.findPagedByEstado(estado, pageable);
    return cats.map(this::toView);
  }

  public CategoriaView buscar(Integer id) {
    Optional<Categoria> categoria = repository.findById(id);
    if (categoria.isEmpty() || !categoria.get().getEstado()) {
      throw new ResponseStatusException(NOT_FOUND, "categoria no encontrada");
    }
    return toView(categoria.get());
  }

  @Transactional
  public CategoriaView registrar(CategoriaInput input) {
    if (repository.existsByDescripcion(input.descripcion())) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
    }
    Categoria categoria = new Categoria(input.descripcion(), input.detalle(), input.capacidad());
    categoria.setEstado(input.estado());
    categoria = repository.save(categoria);
    repository.flush();
    return toView(categoria);
  }

  @Transactional
  public CategoriaView actualizar(Integer id, CategoriaInput input, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Categoria categoria = requerirCategoria(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(categoria.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (operacionRepository != null && operacionRepository.existsPreparandoByCategoria(id)) {
      throw new ApiException(
          ErrorCode.ROOM_OPERATION_IN_PROGRESS, Map.of("idCategoria", String.valueOf(id)));
    }
    if (Boolean.FALSE.equals(input.estado())
        && habitacionRepository.countByIdCategoriaEstadoTrue(categoria.getId())) {
      throw new ApiException(ErrorCode.ACTIVE_DEPENDENCIES);
    }
    if (!input.descripcion().equals(categoria.getDescripcion())
        && repository.existsByDescripcion(input.descripcion())) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
    }
    categoria.setDescripcion(input.descripcion());
    categoria.setDetalle(input.detalle());
    categoria.setCapacidad(input.capacidad());
    categoria.setEstado(input.estado());
    try {
      categoria = repository.save(categoria);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirCategoriaCambio(categoria.getId());
    return toView(categoria);
  }

  @Transactional
  public CategoriaView eliminar(Integer id, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Categoria categoria = requerirCategoria(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(categoria.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (operacionRepository != null && operacionRepository.existsPreparandoByCategoria(id)) {
      throw new ApiException(
          ErrorCode.ROOM_OPERATION_IN_PROGRESS, Map.of("idCategoria", String.valueOf(id)));
    }
    if (habitacionRepository.countByIdCategoriaEstadoTrue(categoria.getId())) {
      throw new ApiException(ErrorCode.ACTIVE_DEPENDENCIES);
    }
    categoria.setEstado(false);
    try {
      categoria = repository.save(categoria);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirCategoriaCambio(categoria.getId());
    return toView(categoria);
  }

  private Categoria requerirCategoria(Integer id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(NOT_FOUND, "categoria no encontrada"));
  }

  private Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
  }

  private CategoriaView toView(Categoria c) {
    return new CategoriaView(
        c.getId(), c.getDescripcion(), c.getDetalle(), c.getCapacidad(), c.getEstado(), c.getVersion());
  }
}
