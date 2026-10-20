package pe.hotel.hotel.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import pe.hotel.hotel.domain.dto.HabitacionInput;
import pe.hotel.hotel.domain.dto.HabitacionUpdate;
import pe.hotel.hotel.domain.dto.HabitacionView;
import pe.hotel.hotel.domain.dto.ImagenHabitacionView;
import pe.hotel.hotel.domain.dto.ImagenInput;
import pe.hotel.hotel.domain.model.EstadoHabitacion;
import pe.hotel.hotel.domain.model.Habitacion;
import pe.hotel.hotel.domain.model.ImagenHabitacion;
import pe.hotel.hotel.domain.model.Piso;
import pe.hotel.hotel.domain.repository.CategoriaRepository;
import pe.hotel.hotel.domain.repository.EstadoHabitacionRepository;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.ImagenHabitacionRepository;
import pe.hotel.hotel.domain.repository.OperacionHabitacionRepository;
import pe.hotel.hotel.domain.repository.PisoRepository;
import pe.hotel.hotel.domain.security.CurrentUser;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;
import static org.springframework.http.HttpStatus.*;

/** Casos de uso de habitaciones: alta LISTA, edicion de catalogo y lecturas con aislamiento por sede. */
@Service
@Transactional(readOnly = true)
public class HabitacionService {

  @Autowired(required = false)
  private HabitacionRepository repository;
  @Autowired(required = false)
  private CategoriaRepository categoriaRepository;
  @Autowired(required = false)
  private EstadoHabitacionRepository estadoRepository;
  @Autowired(required = false)
  private ImagenHabitacionRepository imagenRepository;
  @Autowired(required = false)
  private PisoRepository pisoRepository;
  @Autowired(required = false)
  private CurrentUser currentUser;
  @Autowired(required = false)
  private OutboxService outboxService;
  @Autowired(required = false)
  private OperacionHabitacionRepository operacionRepository;

  public Page<HabitacionView> listar(Integer idHotel, Integer idCategoria, Pageable pageable) {
    if (idHotel == null) {
      throw new ResponseStatusException(BAD_REQUEST, "idHotel es obligatorio");
    }
    if (!currentUser.puedeAccederASede(idHotel)) {
      throw new ResponseStatusException(NOT_FOUND, "sede fuera de alcance");
    }
    Page<Habitacion> habitaciones;
    if (idCategoria != null) {
      habitaciones =
          repository.findPagedByHotelAndCategoriaAndEstado(idHotel, idCategoria, pageable);
    } else {
      habitaciones = repository.findPagedByHotelAndEstado(idHotel, pageable);
    }
    Map<Integer, List<ImagenHabitacionView>> galerias =
        galeriasPorHabitacion(habitaciones.getContent());
    return habitaciones.map(
        h -> toView(h, galerias.getOrDefault(h.getId(), List.of())));
  }

  public HabitacionView buscar(Integer id) {
    Optional<Habitacion> h = repository.findById(id);
    if (h.isEmpty() || !currentUser.puedeAccederASede(h.get().getIdHotel())) {
      throw new ResponseStatusException(NOT_FOUND, "habitacion no encontrada o fuera de alcance");
    }
    return toView(h.get());
  }

  /** Vista sin comprobación de alcance para el job interno y las respuestas de operación. */
  public HabitacionView vistaInterna(Integer id) {
    return repository
        .findById(id)
        .map(this::toView)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
  }

  @Transactional
  public HabitacionView registrar(HabitacionInput input) {
    if (!categoriaRepository.findById(input.idCategoria()).isPresent()) {
      throw new ResponseStatusException(BAD_REQUEST, "categoria inexistente");
    }
    if (input.numero() == null || input.numero().isBlank() || input.numero().length() > 10) {
      throw new ResponseStatusException(BAD_REQUEST, "numero invalido");
    }
    if (repository.existsActiveByIdHotelAndNumero(input.idHotel(), input.numero())) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
    }
    validarPrecio(input.precio());
    List<ImagenInput> imagenes = input.urlsImagenes() == null ? List.of() : input.urlsImagenes();
    if (imagenes.size() > 10) {
      throw new ResponseStatusException(BAD_REQUEST, "maximo 10 imagenes");
    }
    validarImagenes(imagenes);

    EstadoHabitacion lista = estadoRepository.findByCodigo("LISTA").orElseThrow();
    pe.hotel.hotel.domain.model.Categoria categoria =
        categoriaRepository
            .findById(input.idCategoria())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "categoria inexistente"));
    Piso piso = requerirPisoDeSede(input.idPiso(), input.idHotel());
    Habitacion h =
        new Habitacion(
            input.idHotel(),
            input.idPiso(),
            categoria,
            input.numero(),
            input.detalle(),
            input.precio(),
            lista);
    h.setPiso(piso);
    h.setPublicada(input.publicada());
    h = repository.save(h);
    repository.flush();

    int orden = 1;
    for (ImagenInput im : imagenes) {
      imagenRepository.save(new ImagenHabitacion(h, im.url(), im.alt(), orden++));
    }
    imagenRepository.flush();

    outboxService.emitirHabitacionCreada(
        h,
        h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
        h.getPiso() == null ? null : h.getPiso().getDescripcion(),
        "LISTA");
    return toView(h);
  }

  @Transactional
  public HabitacionView actualizar(Integer id, HabitacionUpdate input, String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ResponseStatusException(PRECONDITION_REQUIRED, "If-Match obligatorio");
    }
    Habitacion h = requerirHabitacion(id);
    Long versionEsperada = parseVersion(ifMatch);
    if (!versionEsperada.equals(h.getVersion())) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    if (operacionRepository != null && operacionRepository.existsVigente(id)) {
      throw new ApiException(ErrorCode.ROOM_OPERATION_IN_PROGRESS, Map.of("idHabitacion", String.valueOf(id)));
    }
    pe.hotel.hotel.domain.model.Categoria categoria =
        categoriaRepository
            .findById(input.idCategoria())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "categoria inexistente"));
    Piso piso = requerirPisoDeSede(input.idPiso(), h.getIdHotel());
    validarPrecio(input.precio());
    List<ImagenInput> imagenes = input.urlsImagenes() == null ? List.of() : input.urlsImagenes();
    if (imagenes.size() > 10) {
      throw new ResponseStatusException(BAD_REQUEST, "maximo 10 imagenes");
    }
    validarImagenes(imagenes);

    h.setIdPiso(input.idPiso());
    h.setPiso(piso);
    h.setCategoria(categoria);
    h.setDetalle(input.detalle());
    h.setPrecio(input.precio());
    h.setPublicada(input.publicada());
    imagenRepository.deleteByHabitacionId(h.getId());
    int orden = 1;
    for (ImagenInput im : imagenes) {
      imagenRepository.save(new ImagenHabitacion(h, im.url(), im.alt(), orden++));
    }
    imagenRepository.flush();
    try {
      h = repository.save(h);
      repository.flush();
    } catch (ObjectOptimisticLockingFailureException ex) {
      throw new ResponseStatusException(PRECONDITION_FAILED, "version distinta a la vigente");
    }
    outboxService.emitirHabitacionSnapshot(
        h,
        h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
        h.getPiso() == null ? null : h.getPiso().getDescripcion(),
        h.getEstadoHabitacion().getCodigo());
    return toView(h);
  }

  private Piso requerirPisoDeSede(Integer idPiso, Integer idHotel) {
    return pisoRepository
        .findById(idPiso)
        .filter(p -> p.getIdHotel().equals(idHotel))
        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "piso inexistente o de otra sede"));
  }

  private Habitacion requerirHabitacion(Integer id) {
    return repository
        .findById(id)
        .filter(h -> currentUser.puedeAccederASede(h.getIdHotel()))
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "habitacion no encontrada o fuera de alcance"));
  }

  private void validarPrecio(BigDecimal precio) {
    if (precio == null || precio.signum() <= 0) {
      throw new ResponseStatusException(BAD_REQUEST, "precio debe ser mayor que 0");
    }
    if (precio.remainder(BigDecimal.valueOf(0.01)).compareTo(BigDecimal.ZERO) != 0) {
      throw new ResponseStatusException(BAD_REQUEST, "precio debe ser multiplo de 0.01");
    }
  }

  private void validarImagenes(List<ImagenInput> imagenes) {
    java.util.Set<Integer> ordenes = new java.util.HashSet<>();
    for (ImagenInput im : imagenes) {
      if (im.url() == null
          || (!im.url().startsWith("https://") && !im.url().startsWith("/assets/"))) {
        throw new ResponseStatusException(BAD_REQUEST, "url de imagen no permitida (https o /assets/)");
      }
      if (im.url().length() > 500) {
        throw new ResponseStatusException(BAD_REQUEST, "url de imagen demasiado larga");
      }
      if (im.alt() == null || im.alt().isBlank() || im.alt().length() > 150) {
        throw new ResponseStatusException(BAD_REQUEST, "alt de imagen invalida");
      }
      if (im.orden() == null || !ordenes.add(im.orden())) {
        throw new ResponseStatusException(BAD_REQUEST, "orden de imagen duplicado");
      }
    }
  }

  private Long parseVersion(String ifMatch) {
    String v = ifMatch.trim();
    if (v.startsWith("\"") && v.endsWith("\"")) {
      v = v.substring(1, v.length() - 1);
    }
    return Long.valueOf(v);
  }

  private HabitacionView toView(Habitacion h) {
    return toView(h, galeriaDe(h.getId()));
  }

  private List<ImagenHabitacionView> galeriaDe(Integer idHabitacion) {
    return imagenRepository.findByHabitacionIdOrderByOrdenAsc(idHabitacion).stream()
        .map(this::toImagenView)
        .toList();
  }

  private Map<Integer, List<ImagenHabitacionView>> galeriasPorHabitacion(
      List<Habitacion> habitaciones) {
    List<Integer> ids = habitaciones.stream().map(Habitacion::getId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Integer, List<ImagenHabitacionView>> galerias = new LinkedHashMap<>();
    for (ImagenHabitacion imagen : imagenRepository.findByHabitacionIdInOrderByOrdenAsc(ids)) {
      galerias
          .computeIfAbsent(imagen.getIdHabitacion(), clave -> new ArrayList<>())
          .add(toImagenView(imagen));
    }
    return galerias;
  }

  private ImagenHabitacionView toImagenView(ImagenHabitacion imagen) {
    return new ImagenHabitacionView(imagen.getUrl(), imagen.getAlt(), imagen.getOrden());
  }

  private HabitacionView toView(Habitacion h, List<ImagenHabitacionView> urlsImagenes) {
    String blockId = null;
    String endpoint = null;
    if (operacionRepository != null) {
      OperacionHabitacionRepository.Operacion vigente =
          operacionRepository.findActiveByHabitacion(h.getId()).orElse(null);
      if (vigente != null) {
        blockId = vigente.blockId().toString();
        endpoint = "/api/habitacion/operaciones/" + blockId;
      }
    }
    return new HabitacionView(
        h.getId(),
        h.getIdHotel(),
        h.getNumero(),
        h.getIdPiso(),
        h.getPiso() == null ? null : h.getPiso().getDescripcion(),
        h.getIdCategoria(),
        h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
        h.getDetalle(),
        h.getPrecio(),
        h.getPublicada(),
        urlsImagenes,
        h.getEstado(),
        h.getIdEstadoHabitacion(),
        new pe.hotel.hotel.domain.dto.EstadoFisicoView(
            h.getEstadoHabitacion().getId(),
            h.getEstadoHabitacion().getCodigo(),
            h.getEstadoHabitacion().getDescripcion(),
            h.getEstadoHabitacion().getEstado(),
            h.getEstadoHabitacion().getVersion()),
        new pe.hotel.hotel.domain.dto.OcupacionView("DESCONOCIDA", null, null),
        blockId,
        endpoint,
        h.getVersion());
  }
}
