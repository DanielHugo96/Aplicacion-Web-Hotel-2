package pe.hotel.hotel.domain.service;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.PisoInput;
import pe.hotel.hotel.domain.dto.PisoView;
import pe.hotel.hotel.domain.model.Hotel;
import pe.hotel.hotel.domain.model.Piso;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.HotelRepository;
import pe.hotel.hotel.domain.repository.PisoRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import static org.springframework.http.HttpStatus.*;

/** Casos de uso de pisos por sede. */
@Service
@Transactional(readOnly = true)
public class PisoService {

  @Autowired(required = false)
  private PisoRepository repository;
  @Autowired(required = false)
  private HotelRepository hotelRepository;
  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private CurrentUser currentUser;
  @Autowired(required = false)
  private OutboxService outboxService;

  public List<PisoView> listar(Integer idHotel, Boolean estado) {
    if (idHotel == null) {
      throw new ResponseStatusException(BAD_REQUEST, "idHotel es obligatorio");
    }
    Hotel hotel = requerirSede(idHotel);
    List<Piso> pisos = repository.findByHotelIdAndEstadoTrueOrderByIdAsc(idHotel);
    if (estado != null) {
      pisos = pisos.stream().filter(p -> p.getEstado().equals(estado)).toList();
    }
    return pisos.stream().map(this::toView).toList();
  }

  public PisoView buscar(Integer id) {
    Optional<Piso> piso = repository.findById(id);
    if (piso.isEmpty()
        || !currentUser.puedeAccederASede(piso.get().getIdHotel())
        || !piso.get().getEstado()) {
      throw new ResponseStatusException(NOT_FOUND, "piso no encontrado o fuera de alcance");
    }
    return toView(piso.get());
  }

  @Transactional
  public PisoView registrar(PisoInput input) {
    if (input.idHotel() == null) {
      throw new ResponseStatusException(BAD_REQUEST, "idHotel es obligatorio");
    }
    Hotel hotel = requerirSede(input.idHotel());
    if (repository.findByHotelIdAndDescripcion(input.idHotel(), input.descripcion()).isPresent()) {
      throw new ResponseStatusException(CONFLICT, "descripcion duplicada en la sede");
    }
    Piso piso = new Piso(hotel, input.descripcion());
    piso.setEstado(input.estado() == null ? true : input.estado());
    piso = repository.save(piso);
    repository.flush();
    return toView(piso);
  }

  @Transactional
  public PisoView actualizar(Integer id, PisoInput input, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Piso piso = requerirPiso(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(piso.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (input.idHotel() != null && !piso.getIdHotel().equals(input.idHotel())) {
      throw new ResponseStatusException(BAD_REQUEST, "la sede de un piso no se modifica");
    }
    if (Boolean.FALSE.equals(input.estado())
        && habitacionRepository.countByIdHotelAndEstadoTrue(piso.getIdHotel()) > 0) {
      throw new ResponseStatusException(CONFLICT, "aún tiene habitaciones activas");
    }
    if (!input.descripcion().equals(piso.getDescripcion())
        && repository.findByHotelIdAndDescripcion(input.idHotel(), input.descripcion()).isPresent()) {
      throw new ResponseStatusException(CONFLICT, "descripcion duplicada en la sede");
    }
    piso.setDescripcion(input.descripcion());
    piso.setEstado(input.estado());
    try {
      piso = repository.save(piso);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirPisoCambio(piso.getIdHotel(), piso.getId());
    return toView(piso);
  }

  @Transactional
  public PisoView eliminar(Integer id, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Piso piso = requerirPiso(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(piso.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (habitacionRepository.existsActiveByIdPisoAndIdHotel(piso.getId(), piso.getIdHotel())) {
      throw new ResponseStatusException(CONFLICT, "aún tiene habitaciones activas");
    }
    piso.setEstado(false);
    try {
      piso = repository.save(piso);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    return toView(piso);
  }

  private Hotel requerirSede(Integer idHotel) {
    return hotelRepository
        .findById(idHotel)
        .filter(h -> currentUser.isAdmin() || currentUser.puedeAccederASede(h.getId()))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    NOT_FOUND, "sede no encontrada o fuera de alcance"));
  }

  private Piso requerirPiso(Integer id) {
    return repository
        .findById(id)
        .filter(p -> currentUser.puedeAccederASede(p.getIdHotel()))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    NOT_FOUND, "piso no encontrado o fuera de alcance"));
  }

  private Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
  }

  private PisoView toView(Piso p) {
    return new PisoView(p.getId(), p.getIdHotel(), p.getDescripcion(), p.getEstado(), p.getVersion());
  }
}
