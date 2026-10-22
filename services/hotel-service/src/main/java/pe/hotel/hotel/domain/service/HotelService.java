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

  public Page<HotelView> listar(Boolean estado, Pageable pageable) {
    Page<Hotel> hoteles;
    if (currentUser.isAdmin()) {
      hoteles =
          estado == null
              ? repository.findPagedAll(pageable)
              : repository.findPagedByEstado(estado, pageable);
    } else {
      List<Integer> sedes = currentUser.sedes();
      hoteles =
          repository.findPagedByIdInAndEstado(
              sedes, estado == null ? true : estado, pageable);
    }
    return hoteles.map(this::toView);
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
    String imagenUrl = normalizar(input.imagenUrl());
    String imagenAlt = normalizar(input.imagenAlt());
    validarImagen(imagenUrl, imagenAlt);
    Hotel hotel =
        new Hotel(
            input.slug(),
            input.nombre(),
            input.descripcion(),
            input.direccion(),
            input.ciudad(),
            input.telefono(),
            input.correoContacto(),
            imagenUrl,
            imagenAlt,
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
    String imagenUrl = normalizar(input.imagenUrl());
    String imagenAlt = normalizar(input.imagenAlt());
    validarImagen(imagenUrl, imagenAlt);
    hotel.setSlug(input.slug());
    hotel.setNombre(input.nombre());
    hotel.setDescripcion(input.descripcion());
    hotel.setDireccion(input.direccion());
    hotel.setCiudad(input.ciudad());
    hotel.setTelefono(input.telefono());
    hotel.setCorreoContacto(input.correoContacto());
    hotel.setImagenUrl(imagenUrl);
    hotel.setImagenAlt(imagenAlt);
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

  /**
   * Recorta el valor y trata la cadena vacía como ausente: la foto de la sede es opcional y su
   * contrato la expresa con {@code null}.
   */
  private static String normalizar(String valor) {
    if (valor == null) {
      return null;
    }
    String limpio = valor.trim();
    return limpio.isEmpty() ? null : limpio;
  }

  /**
   * Regla de URL de imagen de docs/especificacion/03-contrato-comun.md § Límites de campos comunes:
   * https o {@code /assets/}, ≤ 500, y toda URL viaja con su descripción alternativa (≤ 150). Una
   * URL sin alt y un alt sin URL se rechazan con 400 {@code VALIDATION_ERROR} sobre el campo.
   */
  private static void validarImagen(String imagenUrl, String imagenAlt) {
    if (imagenUrl == null && imagenAlt == null) {
      return;
    }
    if (imagenUrl == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          Map.of("imagenAlt", "se rechaza una descripción sin URL de imagen"));
    }
    if (!imagenUrl.startsWith("https://") && !imagenUrl.startsWith("/assets/")) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          Map.of("imagenUrl", "url de imagen no permitida (https o /assets/)"));
    }
    if (imagenUrl.length() > 500) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("imagenUrl", "no puede superar 500 caracteres"));
    }
    if (imagenAlt == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("imagenAlt", "obligatorio cuando hay imagenUrl"));
    }
    if (imagenAlt.length() > 150) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("imagenAlt", "no puede superar 150 caracteres"));
    }
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
        h.getImagenUrl(),
        h.getImagenAlt(),
        h.getEstado(),
        h.getPublicado(),
        h.getVersion());
  }
}
