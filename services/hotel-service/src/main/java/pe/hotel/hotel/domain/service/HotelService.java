package pe.hotel.hotel.domain.service;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.hotel.hotel.domain.dto.HotelInput;
import pe.hotel.hotel.domain.dto.HotelView;
import pe.hotel.hotel.domain.model.Hotel;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.HotelRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;
import static org.springframework.http.HttpStatus.*;

/** Casos de uso de sedes: CRUD con aislamiento por sede, version/ETag e idempotencia. */
@Service
@Transactional(readOnly = true)
public class HotelService {

  @Autowired(required = false)
  private HotelRepository repository;
  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private CurrentUser currentUser;
  @Autowired(required = false)
  private OutboxService outboxService;

  public List<HotelView> listar(Boolean estado) {
    List<Hotel> hoteles;
    if (currentUser.isAdmin()) {
      hoteles =
          estado == null ? repository.findAll() : repository.findByEstado(estado);
    } else {
      List<Integer> sedes = currentUser.sedes();
      hoteles = repository.findByIdInAndEstado(sedes, estado == null ? true : estado);
    }
    return hoteles.stream().map(this::toView).toList();
  }

  public HotelView buscar(Integer id) {
    Optional<Hotel> hotel =
        currentUser.isAdmin()
            ? repository.findById(id)
            : repository.findByIdInAndEstado(List.of(id), true).stream().findFirst();
    if (hotel.isEmpty()
        || (currentUser.isEmpleado() && !currentUser.puedeAccederASede(id))) {
      throw new ResponseStatusException(NOT_FOUND, "sede no encontrada o fuera de alcance");
    }
    return toView(hotel.get());
  }

  @Transactional
  public HotelView registrar(HotelInput input) {
    if (repository.existsBySlug(input.slug())) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
    }
    Hotel hotel =
        new Hotel(
            input.slug(),
            input.nombre(),
            input.descripcion(),
            input.direccion(),
            input.ciudad(),
            input.telefono(),
            input.correoContacto(),
            input.publicado());
    hotel = repository.save(hotel);
    repository.flush();
    outboxService.emitirSedeSnapshot(hotel);
    return toView(hotel);
  }

  @Transactional
  public HotelView actualizar(Integer id, HotelInput input, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Hotel hotel = requerirHotel(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(hotel.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (Boolean.TRUE.equals(hotel.getPublicado())
        && !input.slug().equals(hotel.getSlug())) {
      throw new ResponseStatusException(BAD_REQUEST, "slug no modificable tras la publicación");
    }
    hotel.setSlug(input.slug());
    hotel.setNombre(input.nombre());
    hotel.setDescripcion(input.descripcion());
    hotel.setDireccion(input.direccion());
    hotel.setCiudad(input.ciudad());
    hotel.setTelefono(input.telefono());
    hotel.setCorreoContacto(input.correoContacto());
    hotel.setPublicado(input.publicado());
    hotel.setEstado(input.estado());
    try {
      hotel = repository.save(hotel);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirSedeSnapshot(hotel);
    return toView(hotel);
  }

  @Transactional
  public HotelView eliminar(Integer id, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Hotel hotel = requerirHotel(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(hotel.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    long habitacionesActivas = habitacionRepository.countByIdHotelAndEstadoTrue(id);
    if (habitacionesActivas > 0) {
      throw new ApiException(ErrorCode.ACTIVE_DEPENDENCIES);
    }
    hotel.setEstado(false);
    try {
      hotel = repository.save(hotel);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirSedeSnapshot(hotel);
    return toView(hotel);
  }

  private Hotel requerirHotel(Integer id) {
    return repository
        .findById(id)
        .filter(h -> currentUser.isAdmin() || currentUser.puedeAccederASede(h.getId()))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    NOT_FOUND, "sede no encontrada o fuera de alcance"));
  }

  private Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
  }

  private HotelView toView(Hotel h) {
    return new HotelView(
        h.getId(),
        h.getSlug(),
        h.getNombre(),
        h.getDescripcion(),
        h.getDireccion(),
        h.getCiudad(),
        h.getTelefono(),
        h.getCorreoContacto(),
        h.getEstado(),
        h.getPublicado(),
        h.getVersion());
  }
}
