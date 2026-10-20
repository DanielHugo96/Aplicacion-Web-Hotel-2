package pe.hotel.identity.persona;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.hotel.identity.auth.PersonaView;
import pe.hotel.identity.auth.ETag;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ApiResponse;
import pe.hotel.identity.platform.CorrelationId;
import pe.hotel.identity.platform.ErrorCode;
import pe.hotel.identity.platform.Meta;

/**
 * Rutas de personas, tipos de persona y lectura interna de cliente vigente.
 *
 * <p>Toda la identidad proviene del JWT validado (sub, roles, tipoPersona, sedes). No se leen
 * cabeceras de identidad falsificables.
 */
@RestController
@RequestMapping("/api")
public class PersonaController {

  private final PersonaService personas;

  public PersonaController(PersonaService personas) {
    this.personas = personas;
  }

  /* ====================================================================
   * Personas: listado y búsqueda
   * ==================================================================== */

  @GetMapping("/persona/listar")
  public ResponseEntity<ApiResponse<PersonaListView>> listar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "20") int size,
      @RequestParam(name = "idTipoPersona", required = false) Integer idTipoPersona,
      @RequestParam(name = "documento", required = false) String documento) {

    Map<String, Object> claims = reclamos(jwt);
    String rol = (String) claims.get("rol");
    List<Integer> sedes = sedesDe(claims);
    Integer idHotelDelActor = sedes.isEmpty() ? null : sedes.get(0);

    PersonaService.PersonaList lista =
        personas.listar(page, size, idTipoPersona, documento, rol, sedes);

    List<PersonaView> vistas =
        lista.personas().stream().map(PersonaView::de).collect(Collectors.toList());

    // Auditoría PERSONA_SEARCH: consulta humana de PII ajena (A/E).
    String actorId = String.valueOf(claims.get("idPersona"));
    personas.auditarSearch(actorId, idHotelDelActor, CorrelationId.current());

    return ResponseEntity.ok()
        .eTag(ETag.de(lista.total()))
        .body(ApiResponse.ok(
            new PersonaListView(vistas),
            Meta.of(CorrelationId.current(), page, size, lista.total())));
  }

  @GetMapping("/persona/buscar/{id}")
  public ResponseEntity<ApiResponse<PersonaView>> buscar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id,
      @RequestParam(name = "documento", required = false) String documento) {

    Map<String, Object> claims = reclamos(jwt);
    String rol = (String) claims.get("rol");
    Integer idDelToken = idDeSub(jwt);
    String actorId = String.valueOf(claims.get("idPersona"));
    List<Integer> sedes = sedesDe(claims);
    Integer idHotelDelActor = sedes.isEmpty() ? null : sedes.get(0);

    Persona persona = personas.buscar(id, documento, rol, idDelToken);

    // Auditoría PERSONA_READ: lectura humana de PII ajena (A/E).
    personas.auditarRead(actorId, id, idHotelDelActor, CorrelationId.current());

    return ResponseEntity.ok()
        .eTag(ETag.de(persona.version()))
        .body(ApiResponse.ok(PersonaView.de(persona), Meta.of(CorrelationId.current())));
  }

  /* ====================================================================
   * Personas: escritura
   * ==================================================================== */

  @PostMapping("/persona/registrar")
  public ResponseEntity<ApiResponse<PersonaView>> registrar(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody PersonaAltaRequest request) {

    Map<String, Object> claims = reclamos(jwt);
    String rol = (String) claims.get("rol");
    Integer idTipoDelActor = idTipoDe(claims);
    List<Integer> sedesDelActor = sedesDe(claims);
    String actorId = String.valueOf(claims.get("idPersona"));
    Integer idHotelDelActor = sedesDelActor.isEmpty() ? null : sedesDelActor.get(0);

    Persona creada = personas.registrar(
        request.tipoDocumento(),
        request.documento(),
        request.nombre(),
        request.apellido(),
        request.correo(),
        request.clave(),
        request.idTipoPersona(),
        request.sedes(),
        rol,
        actorId,
        idHotelDelActor,
        CorrelationId.current());

    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(ETag.de(creada.version()))
        .body(ApiResponse.ok(PersonaView.de(creada), Meta.of(CorrelationId.current())));
  }

  @PutMapping("/persona/actualizar/{id}")
  public ResponseEntity<ApiResponse<PersonaView>> actualizar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody PersonaUpdateRequest request) {

    Map<String, Object> claims = reclamos(jwt);
    String rol = (String) claims.get("rol");
    String actorId = String.valueOf(claims.get("idPersona"));
    List<Integer> sedes = sedesDe(claims);
    Integer idHotelDelActor = sedes.isEmpty() ? null : sedes.get(0);

    Persona actualizada = personas.actualizar(
        id,
        request.nombre(),
        request.apellido(),
        request.correo(),
        request.fotoUrl(),
        request.idTipoPersona(),
        request.sedes(),
        request.estado(),
        ifMatch,
        CorrelationId.current(),
        rol,
        actorId,
        idHotelDelActor);

    return ResponseEntity.ok()
        .eTag(ETag.de(actualizada.version()))
        .body(ApiResponse.ok(PersonaView.de(actualizada), Meta.of(CorrelationId.current())));
  }

  @DeleteMapping("/persona/eliminar/{id}")
  public ResponseEntity<ApiResponse<PersonaView>> eliminar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {

    Map<String, Object> claims = reclamos(jwt);
    String rol = (String) claims.get("rol");
    String actorId = String.valueOf(claims.get("idPersona"));
    List<Integer> sedes = sedesDe(claims);
    Integer idHotelDelActor = sedes.isEmpty() ? null : sedes.get(0);

    Persona eliminada = personas.eliminar(
        id, ifMatch, CorrelationId.current(), rol, actorId, idHotelDelActor);

    return ResponseEntity.ok()
        .eTag(ETag.de(eliminada.version()))
        .body(ApiResponse.ok(PersonaView.de(eliminada), Meta.of(CorrelationId.current())));
  }

  /* ====================================================================
   * Tipos de persona (catálogo fijo)
   * ==================================================================== */

  @GetMapping("/tipopersona/listar")
  public ResponseEntity<ApiResponse<TipoPersonaListView>> listarTiposPersona(
      @AuthenticationPrincipal Jwt jwt) {
    validarAutenticado(jwt);
    List<TipoPersona> tipos = personas.listarTiposPersona();
    List<TipoPersonaView> vistas =
        tipos.stream().map(TipoPersonaView::new).collect(Collectors.toList());
    return ResponseEntity.ok()
        .body(ApiResponse.ok(new TipoPersonaListView(vistas), Meta.of(CorrelationId.current())));
  }

  @GetMapping("/tipopersona/buscar/{id}")
  public ResponseEntity<ApiResponse<TipoPersonaView>> buscarTipoPersona(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id) {
    validarAutenticado(jwt);
    TipoPersona tipo = personas.buscarTipoPersona(id);
    return ResponseEntity.ok()
        .eTag(ETag.de(tipo.version()))
        .body(ApiResponse.ok(new TipoPersonaView(tipo), Meta.of(CorrelationId.current())));
  }

  @PutMapping("/tipopersona/actualizar/{id}")
  public ResponseEntity<ApiResponse<TipoPersonaView>> actualizarTipoPersona(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") int id,
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody TipoPersonaUpdateRequest request) {

    validarAdmin(jwt);
    Map<String, Object> claims = reclamos(jwt);
    String actorId = String.valueOf(claims.get("idPersona"));
    List<Integer> sedes = sedesDe(claims);
    Integer idHotelDelActor = sedes.isEmpty() ? null : sedes.get(0);

    TipoPersona actualizado = personas.actualizarTipoPersona(
        id, request.descripcion(), ifMatch, CorrelationId.current(), actorId, idHotelDelActor);

    return ResponseEntity.ok()
        .eTag(ETag.de(actualizado.version()))
        .body(ApiResponse.ok(new TipoPersonaView(actualizado), Meta.of(CorrelationId.current())));
  }

  @PostMapping("/tipopersona/registrar")
  public ResponseEntity<ApiResponse<Void>> registrarTipoPersonaRetirado() {
    personas.registrarTipoPersonaRetirado();
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @DeleteMapping("/tipopersona/eliminar/{id}")
  public ResponseEntity<ApiResponse<Void>> eliminarTipoPersonaRetirado(@PathVariable("id") int id) {
    personas.eliminarTipoPersonaRetirado();
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  /* ====================================================================
   * Privados
   * ==================================================================== */

  private void validarAutenticado(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
  }

  private void validarAdmin(Jwt jwt) {
    Map<String, Object> claims = reclamos(jwt);
    if (!"ADMIN".equals(claims.get("rol"))) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private Map<String, Object> reclamos(Jwt jwt) {
    String sub = jwt.getSubject();
    int idPersona = sub == null ? -1 : Integer.parseInt(sub);
    @SuppressWarnings("unchecked")
    List<String> roles = jwt.getClaimAsStringList("roles");
    String rol = roles == null || roles.isEmpty() ? null : roles.get(0);
    Object sedes = jwt.getClaim("sedes");
    String tipoPersona = jwt.getClaimAsString("tipoPersona");
    return Map.of(
        "idPersona", idPersona,
        "rol", rol,
        "tipoPersona", tipoPersona,
        "sedes", sedes == null ? List.of() : sedes);
  }

  /**
   * Normaliza el claim {@code sedes} a {@code List<Integer>}. El decoder JWT entrega los números
   * como {@code Long}; el cast directo a {@code List<Integer>} se borra en ejecución y falla al
   * leer el primer elemento (ClassCastException). Cualquier valor que no sea una lista de números
   * se descarta.
   */
  private List<Integer> sedesDe(Map<String, Object> claims) {
    Object valor = claims.get("sedes");
    if (!(valor instanceof List<?> lista)) {
      return List.of();
    }
    List<Integer> sedes = new java.util.ArrayList<>(lista.size());
    for (Object elemento : lista) {
      if (elemento instanceof Number numero) {
        sedes.add(numero.intValue());
      }
    }
    return sedes;
  }

  private Integer idDeSub(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null) {
      return null;
    }
    try {
      return Integer.parseInt(sub);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Integer idTipoDe(Map<String, Object> claims) {
    Object v = claims.get("idTipoPersona");
    return v == null ? null : ((Number) v).intValue();
  }

  /* Vistas de respuesta */

  public record PersonaAltaRequest(
      String tipoDocumento, String documento, String nombre, String apellido,
      String correo, String clave, Integer idTipoPersona, List<Integer> sedes) {}

  public record PersonaUpdateRequest(
      String nombre, String apellido, String correo, String fotoUrl,
      Integer idTipoPersona, List<Integer> sedes, Boolean estado) {}

  public record TipoPersonaUpdateRequest(String descripcion) {}

  public record PersonaListView(List<PersonaView> personas) {}

  public record TipoPersonaListView(List<TipoPersonaView> tipos) {}

  public record TipoPersonaView(
      int idTipoPersona, String descripcion, String codigo, boolean estado, long version) {
    public TipoPersonaView(TipoPersona tipo) {
      this(tipo.idTipoPersona(), tipo.descripcion(), tipo.codigo(), tipo.estado(), tipo.version());
    }
  }

  public record ClienteVigenteView(
      int idPersona, String tipoDocumento, String documento, String nombre, String apellido,
      String correo, boolean estado, int idTipoPersona, long version) {
    public ClienteVigenteView(pe.hotel.identity.persona.ClienteVigente v) {
      this(v.idPersona(), v.tipoDocumento(), v.documento(), v.nombre(), v.apellido(),
          v.correo(), v.estado(), v.idTipoPersona(), v.version());
    }
  }
}
