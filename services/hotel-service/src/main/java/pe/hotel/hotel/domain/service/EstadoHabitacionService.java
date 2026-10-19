package pe.hotel.hotel.domain.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.EstadoFisicoView;
import pe.hotel.hotel.domain.model.EstadoHabitacion;
import pe.hotel.hotel.domain.repository.EstadoHabitacionRepository;
import static org.springframework.http.HttpStatus.*;

/** Catalogo fijo de estados fisicos: solo lectura de etiqueta; alta/baja retiradas (409). */
@Service
@Transactional(readOnly = true)
public class EstadoHabitacionService {

  @Autowired(required = false)
  private EstadoHabitacionRepository repository;

  public List<EstadoFisicoView> listar() {
    return repository.findByEstadoTrueOrderByIdAsc().stream().map(this::toView).toList();
  }

  public EstadoFisicoView buscar(Integer id) {
    return repository
        .findActiveById(id)
        .map(this::toView)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "estado fisico no encontrado"));
  }

  @Transactional
  public EstadoFisicoView actualizar(Integer id, String descripcion, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    EstadoHabitacion estado = requerirEstado(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(estado.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    estado.setDescripcion(descripcion);
    try {
      estado = repository.save(estado);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    return toView(estado);
  }

  public void registrar() {
    throw new ResponseStatusException(CONFLICT, "los estados fisicos del sistema no se crean");
  }

  public void eliminar(Integer id) {
    throw new ResponseStatusException(CONFLICT, "los estados fisicos del sistema no se eliminan");
  }

  private EstadoHabitacion requerirEstado(Integer id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "estado fisico no encontrado"));
  }

  private Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
  }

  private EstadoFisicoView toView(EstadoHabitacion e) {
    return new EstadoFisicoView(e.getId(), e.getCodigo(), e.getDescripcion(), e.getEstado(), e.getVersion());
  }
}
