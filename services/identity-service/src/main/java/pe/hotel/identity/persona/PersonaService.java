package pe.hotel.identity.persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.identity.auth.Claves;
import pe.hotel.identity.auth.ETag;
import pe.hotel.identity.client.HotelClient;
import pe.hotel.identity.platform.ApiException;
import pe.hotel.identity.platform.ErrorCode;
import pe.hotel.identity.messaging.Destination;
import pe.hotel.identity.messaging.OutboxStore;

/**
 * Lógica de negocio de personas, tipos de persona, sedes asignadas, auditoría PII y
 * anonimización, sobre las tablas creadas por la migración V4 y las tablas técnicas de la
 * plataforma.
 *
 * <p>El mutex del último ADMIN se adquiere con {@code SELECT FOR UPDATE} sobre la fila fija
 * {@code tipo_persona.codigo='ADMIN'} antes de tocar la persona, y se verifica dentro de la misma
 * transacción que quede al menos un ADMIN activo con {@code clave_hash}.
 *
 * <p>La validación de sedes por Feign se ejecuta antes del commit y fuera de la transacción SQL
 * (docs/especificacion/03-contrato-comun.md § Servicio a servicio).
 */
@Service
public class PersonaService {

  private static final String CANAL_PERSONA = "identity.persona.v1";
  private static final String TIPO_EVENTO = "PersonaSnapshot";
  private static final Logger log = LoggerFactory.getLogger(PersonaService.class);

  private final PersonaRepository personas;
  private final HotelClient hotel;
  private final PasswordEncoder passwordEncoder;
  private final ObjectProvider<OutboxStore> outboxProvider;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();

  public PersonaService(
      PersonaRepository personas,
      HotelClient hotel,
      PasswordEncoder passwordEncoder,
      ObjectProvider<OutboxStore> outboxProvider,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.personas = personas;
    this.hotel = hotel;
    this.passwordEncoder = passwordEncoder;
    this.outboxProvider = outboxProvider;
    this.jdbc = jdbc;
  }

  /* ====================================================================
   * Lectura
   * ==================================================================== */

  /** Listado paginado. EMPLEADO exige documento exacto y solo ve CLIENTE. */
  @Transactional(readOnly = true)
  public PersonaList listar(int page, int size, Integer idTipoPersona, String documento, String rol, List<Integer> sedes) {
    if ("EMPLEADO".equals(rol)) {
      if (documento == null || documento.isBlank()) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR,
            Map.of("documento", "obligatorio para consultar como empleado"));
      }
      idTipoPersona = PersonaRepository.ID_TIPO_CLIENTE;
    }
    List<Persona> filas = personas.listar(idTipoPersona, documento, page * size, size);
    long total = personas.contar(idTipoPersona, documento);
    return new PersonaList(filas, page, size, total);
  }

  /** Búsqueda por id con reglas de alcance y anti-enumeración. */
  @Transactional(readOnly = true)
  public Persona buscar(int id, String documento, String rol, Integer idPersonaDelToken) {
    Optional<Persona> persona = personas.buscarPorId(id);
    if (persona.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    Persona p = persona.get();

    switch (rol) {
      case "ADMIN":
        return p;
      case "CLIENTE":
        if (idPersonaDelToken == null || idPersonaDelToken != p.id()) {
          throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return p;
      case "EMPLEADO":
        if (!"CLIENTE".equals(p.codigoRol())) {
          throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (documento == null || !documento.equals(p.documento())) {
          throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return p;
      default:
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
  }

  /* ====================================================================
   * Escritura de personas
   * ==================================================================== */

  @Transactional
  public Persona registrar(
      String tipoDocumento,
      String documento,
      String nombre,
      String apellido,
      String correo,
      String clave,
      Integer idTipoPersona,
      List<Integer> sedes,
      String rolDelActor,
      String actorId,
      Integer idHotelDelActor,
      String correlationId) {

    String tipoDoc = DatosPersona.normalizarTipoDocumento(tipoDocumento);
    String doc = documento == null ? null : documento.trim();
    String nom = nombre == null ? null : nombre.trim();
    String ape = apellido == null ? null : apellido.trim();
    String email = correo == null ? null : correo.trim().toLowerCase();

    Map<String, String> errores = new LinkedHashMap<>();
    if (!DatosPersona.tipoDocumentoValido(tipoDoc)) {
      errores.put("tipoDocumento", "debe ser DNI, PASAPORTE o CE");
    }
    if (!DatosPersona.documentoValido(tipoDoc, doc)) {
      errores.put("documento", "DNI de 8 dígitos; PASAPORTE o CE de 6 a 20 alfanuméricos");
    }
    if (!DatosPersona.textoValido(nom, DatosPersona.MAX_NOMBRE)) {
      errores.put("nombre", "obligatorio, hasta 100 caracteres");
    }
    if (!DatosPersona.textoValido(ape, DatosPersona.MAX_NOMBRE)) {
      errores.put("apellido", "obligatorio, hasta 100 caracteres");
    }
    if (email == null || !Claves.correoValido(email)) {
      errores.put("correo", "formato de correo invalido");
    }
    if (!errores.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, errores);
    }

    // EMPLEADO solo crea CLIENTE sin sedes.
    if ("EMPLEADO".equals(rolDelActor)) {
      if (idTipoPersona == null || idTipoPersona != PersonaRepository.ID_TIPO_CLIENTE) {
        throw new ApiException(ErrorCode.FORBIDDEN);
      }
      if (sedes != null && !sedes.isEmpty()) {
        throw new ApiException(ErrorCode.FORBIDDEN);
      }
      idTipoPersona = PersonaRepository.ID_TIPO_CLIENTE;
    }

    // ADMIN/EMPLEADO requieren clave; un huésped sin credenciales no es personal.
    boolean esPersonal = idTipoPersona != PersonaRepository.ID_TIPO_CLIENTE;
    if (esPersonal && (clave == null || clave.isBlank())) {
      throw new ApiException(ErrorCode.STAFF_CREDENTIAL_REQUIRED);
    }

    String claveHash = clave == null ? null : passwordEncoder.encode(clave);

    if (personas.existeCorreo(email)) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("correo", "ya registrado por otra persona"));
    }
    if (personas.existeDocumento(tipoDoc, doc)) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("documento", "ya registrado por otra persona"));
    }

    // Mutex del último ADMIN antes de la mutación.
    if (esPersonal && "ADMIN".equals(codigoDe(idTipoPersona))) {
      personas.bloquearRolAdmin();
    }

    int id;
    try {
      id = personas.insertar(tipoDoc, doc, nom, ape, email, claveHash, idTipoPersona);
    } catch (DuplicateKeyException carrera) {
      if (personas.existeCorreo(email) || personas.existeDocumento(tipoDoc, doc)) {
        throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("correo", "ya registrado"));
      }
      throw carrera;
    }

    // Sedes: solo personal, validadas por Feign antes del commit y fuera de transacción SQL.
    if (esPersonal && (sedes == null)) {
      sedes = List.of();
    }
    if (esPersonal && !sedes.isEmpty()) {
      validarSedes(sedes);
    }
    if (esPersonal) {
      personas.reemplazarSedes(id, sedes == null ? List.of() : sedes);
    }

    Persona creada = personas.buscarPorId(id).orElseThrow();
    emitirSnapshot(creada, correlationId);
    auditar("PERSONA_CREATE", String.valueOf(id), null, null, correlationId,
        Map.of("campos", List.of("estado", "sedes", "idTipoPersona"),
            "despues", Map.of("estado", true, "idTipoPersona", idTipoPersona, "sedes", sedes)),
        actorId, idHotelDelActor);
    return creada;
  }

  @Transactional
  public Persona actualizar(
      int id,
      String nombre,
      String apellido,
      String correo,
      String fotoUrl,
      Integer idTipoPersona,
      List<Integer> sedes,
      Boolean estado,
      String ifMatch,
      String correlationId,
      String rolDelActor,
      String actorId,
      Integer idHotelDelActor) {

    if (!"ADMIN".equals(rolDelActor)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }

    Optional<Persona> existente = personas.buscarPorId(id);
    if (existente.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    Persona antes = existente.get();

    long versionEsperada = ETag.versionDe(ifMatch);
    if (versionEsperada < 1) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    if (antes.version() != versionEsperada) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED,
          Map.of("If-Match", "la version enviada no es la vigente"));
    }

    String email = correo == null ? null : correo.trim().toLowerCase();
    Map<String, String> errores = new LinkedHashMap<>();
    if (DatosPersona.textoValido(nombre, DatosPersona.MAX_NOMBRE) == false && nombre != null) {
      errores.put("nombre", "obligatorio, hasta 100 caracteres");
    }
    if (DatosPersona.textoValido(apellido, DatosPersona.MAX_NOMBRE) == false && apellido != null) {
      errores.put("apellido", "obligatorio, hasta 100 caracteres");
    }
    if (email != null && !Claves.correoValido(email)) {
      errores.put("correo", "formato de correo invalido");
    }
    if (!errores.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, errores);
    }

    // No se cambia el documento ni la contraseña por este DTO.

    boolean promoviendoAPersonal =
        idTipoPersona != null
            && idTipoPersona != antes.idTipoPersona()
            && idTipoPersona != PersonaRepository.ID_TIPO_CLIENTE;
    boolean degradandoDePersonal =
        antes.idTipoPersona() != PersonaRepository.ID_TIPO_CLIENTE
            && (idTipoPersona == null || idTipoPersona == PersonaRepository.ID_TIPO_CLIENTE);

    // Mutex del último ADMIN para cualquier cambio de rol, estado o baja.
    boolean tocaAdmin =
        "ADMIN".equals(codigoDe(antes.idTipoPersona()))
        || (idTipoPersona != null && "ADMIN".equals(codigoDe(idTipoPersona)))
        || Boolean.FALSE.equals(estado);
    if (tocaAdmin) {
      personas.bloquearRolAdmin();
    }

    // Promover a personal exige clave_hash existente.
    if (promoviendoAPersonal && antes.claveHash() == null) {
      throw new ApiException(ErrorCode.STAFF_CREDENTIAL_REQUIRED);
    }

    // Sedes validadas por Feign antes del commit, fuera de transacción SQL.
    List<Integer> sedesFinales = sedes == null ? List.of() : sedes;
    boolean esPersonalDespues =
        idTipoPersona != null && idTipoPersona != PersonaRepository.ID_TIPO_CLIENTE;
    boolean eraPersonal = antes.idTipoPersona() != PersonaRepository.ID_TIPO_CLIENTE;
    if (esPersonalDespues && !sedesFinales.isEmpty()) {
      validarSedes(sedesFinales);
    }
    if (esPersonalDespues || eraPersonal) {
      personas.reemplazarSedes(id, sedesFinales);
    }

    String correoFinal = email != null ? email : antes.correo();
    if (email != null && !email.equals(antes.correo()) && personas.existeCorreo(email)) {
      throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, Map.of("correo", "ya registrado por otra persona"));
    }
    String tipoDocFinal = antes.tipoDocumento();
    String docFinal = antes.documento();
    String nomFinal = nombre != null ? nombre.trim() : antes.nombre();
    String apeFinal = apellido != null ? apellido.trim() : antes.apellido();
    String fotoFinal = fotoUrl != null ? fotoUrl : antes.fotoUrl();
    Integer rolFinal = idTipoPersona != null ? idTipoPersona : antes.idTipoPersona();
    Boolean estadoFinal = estado != null ? estado : antes.estado();

    personas.actualizar(id, nomFinal, apeFinal, correoFinal, fotoFinal, rolFinal, estadoFinal, antes.version());

    // Después del cambio, verificar que quede al menos un ADMIN activo con clave.
    // Si esta operación desactiva al último ADMIN, revertir con 409 (rollback transaccional).
    boolean quitaAdmin = "ADMIN".equals(codigoDe(antes.idTipoPersona()))
        || Boolean.FALSE.equals(estadoFinal);
    if (quitaAdmin && personas.contarAdminActivosConClave() == 0) {
      throw new ApiException(ErrorCode.LAST_ADMIN_PROTECTED);
    }

    Persona despues = personas.buscarPorId(id).orElseThrow();
    emitirSnapshot(despues, correlationId);

    // Auditar cambios de rol, sedes y estado.
    Map<String, Object> despuesMap = new LinkedHashMap<>();
    Map<String, Object> antesMap = new LinkedHashMap<>();
    java.util.List<String> campos = new java.util.ArrayList<>();
    if (rolFinal != antes.idTipoPersona()) {
      campos.add("idTipoPersona");
      antesMap.put("idTipoPersona", antes.idTipoPersona());
      despuesMap.put("idTipoPersona", rolFinal);
    }
    if (!java.util.Objects.equals(sedesFinales, antes.sedes())) {
      campos.add("sedes");
      antesMap.put("sedes", antes.sedes());
      despuesMap.put("sedes", sedesFinales);
    }
    if (estadoFinal != antes.estado()) {
      campos.add("estado");
      antesMap.put("estado", antes.estado());
      despuesMap.put("estado", estadoFinal);
    }
    if (!campos.isEmpty()) {
      auditar("PERSONA_UPDATE", String.valueOf(id), antesMap, despuesMap, correlationId,
          Map.of("campos", campos, "antes", antesMap, "despues", despuesMap),
          actorId, idHotelDelActor);
    }
    return despues;
  }

  @Transactional
  public Persona eliminar(
      int id, String ifMatch, String correlationId, String rolDelActor,
      String actorId, Integer idHotelDelActor) {
    if (!"ADMIN".equals(rolDelActor)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }

    Optional<Persona> existente = personas.buscarPorId(id);
    if (existente.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    Persona antes = existente.get();

    long versionEsperada = ETag.versionDe(ifMatch);
    if (versionEsperada < 1) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    if (antes.version() != versionEsperada) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED,
          Map.of("If-Match", "la version enviada no es la vigente"));
    }

    // Mutex del último ADMIN también en bajas.
    if ("ADMIN".equals(codigoDe(antes.idTipoPersona()))) {
      personas.bloquearRolAdmin();
    }

    personas.desactivar(id, antes.version());

    // Después de la baja, verificar que quede al menos un ADMIN activo con clave.
    if (personas.contarAdminActivosConClave() == 0) {
      throw new ApiException(ErrorCode.LAST_ADMIN_PROTECTED);
    }

    Persona despues = personas.buscarPorId(id).orElseThrow();
    emitirSnapshot(despues, correlationId);

    auditar("PERSONA_DELETE", String.valueOf(id),
        Map.of("estado", true, "idTipoPersona", antes.idTipoPersona(), "sedes", antes.sedes()),
        Map.of("estado", false),
        correlationId,
        Map.of("campos", List.of("estado"),
            "antes", Map.of("estado", true, "idTipoPersona", antes.idTipoPersona(), "sedes", antes.sedes()),
            "despues", Map.of("estado", false)),
        actorId, idHotelDelActor);
    return despues;
  }

  /* ====================================================================
   * Tipos de persona (catálogo fijo)
   * ==================================================================== */

  @Transactional(readOnly = true)
  public List<TipoPersona> listarTiposPersona() {
    return personas.listarTiposPersona();
  }

  @Transactional(readOnly = true)
  public TipoPersona buscarTipoPersona(int id) {
    return personas.buscarTipoPersona(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Transactional
  public TipoPersona actualizarTipoPersona(
      int id, String descripcion, String ifMatch, String correlationId,
      String actorId, Integer idHotelDelActor) {
    Optional<TipoPersona> existente = personas.buscarTipoPersona(id);
    if (existente.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    TipoPersona antes = existente.get();

    long versionEsperada = ETag.versionDe(ifMatch);
    if (versionEsperada < 1) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "obligatorio"));
    }
    if (antes.version() != versionEsperada) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED,
          Map.of("If-Match", "la version enviada no es la vigente"));
    }

    String desc = descripcion == null ? null : descripcion.trim();
    if (desc == null || desc.isBlank() || desc.length() > 80) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR,
          Map.of("descripcion", "obligatoria, hasta 80 caracteres"));
    }

    personas.actualizarTipoPersona(id, desc, antes.version());
    TipoPersona despues = personas.buscarTipoPersona(id).orElseThrow();

    auditar("TIPOPERSONA_UPDATE", String.valueOf(id),
        Map.of("descripcion", antes.descripcion()),
        Map.of("descripcion", despues.descripcion()),
        correlationId,
        Map.of("campos", List.of("descripcion"),
            "antes", Map.of("descripcion", antes.descripcion()),
            "despues", Map.of("descripcion", despues.descripcion())),
        actorId, idHotelDelActor);
    return despues;
  }

  /** Crear roles del catálogo fijo no está permitido: 409 FIXED_ROLE_CATALOG. */
  public void registrarTipoPersonaRetirado() {
    throw new ApiException(ErrorCode.FIXED_ROLE_CATALOG, Map.of("ruta", "/api/tipopersona/registrar"));
  }

  /** Eliminar roles del catálogo fijo no está permitido: 409 FIXED_ROLE_CATALOG. */
  public void eliminarTipoPersonaRetirado() {
    throw new ApiException(ErrorCode.FIXED_ROLE_CATALOG, Map.of("ruta", "/api/tipopersona/eliminar"));
  }

  /* ====================================================================
   * Lectura máquina: cliente vigente
   * ==================================================================== */

  @Transactional(readOnly = true)
  public ClienteVigente clienteVigente(int id) {
    return personas.clienteVigente(id)
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  /* ====================================================================
   * Anonimización manual de inactivas > 5 años
   * ==================================================================== */

  @Transactional
  public void anonimizar(int id, String actor, Integer idHotelDelActor, String correlationId) {
    Optional<Persona> existente = personas.buscarPorId(id);
    if (existente.isEmpty()) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    Persona p = existente.get();
    if (p.estado()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR,
          Map.of("id", "solo se anonimizan personas inactivas"));
    }
    OffsetDateTime haceCincoAnos = OffsetDateTime.now().minus(5, ChronoUnit.YEARS);
    if (p.createdAt() != null && p.createdAt().isAfter(haceCincoAnos)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR,
          Map.of("id", "la persona no lleva mas de 5 anos inactiva"));
    }

    String anonimoNombre = "ANON_" + id;
    String anonimoApellido = "ANON_" + id;
    String anonimoDocumento = "ANON" + String.format("%07d", id);
    String anonimoCorreo = "anon_" + id + "@anon.test";

    jdbc.update(
        """
        UPDATE persona
           SET nombre = ?, apellido = ?, documento = ?, correo = ?,
               version = version + 1, updated_at = now()
         WHERE id = ? AND estado = false
        """,
        anonimoNombre, anonimoApellido, anonimoDocumento, anonimoCorreo, id);

    Persona despues = personas.buscarPorId(id).orElseThrow();
    emitirSnapshot(despues, correlationId);

    auditar("PERSONA_ANONYMIZE", String.valueOf(id),
        Map.of("nombre", p.nombre(), "apellido", p.apellido(), "documento", p.documento(), "correo", p.correo()),
        null,
        correlationId,
        Map.of("campos", List.of("nombre", "apellido", "documento", "correo"),
            "antes", Map.of("nombre", p.nombre(), "apellido", p.apellido(), "documento", p.documento(), "correo", p.correo())),
        actor, idHotelDelActor);
  }

  /* ====================================================================
   * Privados
   * ==================================================================== */

  private void validarSedes(List<Integer> sedes) {
    for (Integer sede : sedes) {
      try {
        HotelClient.HotelSede hs = hotel.datos(sede);
        if (!hs.estado()) {
          throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
              Map.of("sedes", "sede " + sede + " inactiva"));
        }
      } catch (ApiException e) {
        throw e;
      } catch (Exception e) {
        // Hotel caído, timeout o cualquier error de Feign: 503 sin asignación optimista.
        throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
      }
    }
  }

  private void emitirSnapshot(Persona p, String correlationId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("idPersona", p.id());
    payload.put("nombre", p.nombre());
    payload.put("apellido", p.apellido());
    payload.put("idTipoPersona", p.idTipoPersona());
    payload.put("estado", p.estado());
    payload.put("version", p.version());
    try {
      java.util.Map<String, Object> evento = new java.util.LinkedHashMap<>();
      evento.put("eventId", UUID.randomUUID().toString());
      evento.put("type", TIPO_EVENTO);
      evento.put("schemaVersion", 1);
      evento.put("aggregateType", "persona");
      evento.put("aggregateId", String.valueOf(p.id()));
      evento.put("aggregateVersion", p.version());
      evento.put("idHotel", null);
      evento.put("occurredAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
      evento.put(
          "correlationId",
          correlationId == null || correlationId.isBlank()
              ? UUID.randomUUID().toString()
              : correlationId);
      evento.put("payload", payload);
      String json = mapper.writeValueAsString(evento);
      OutboxStore outbox = outboxProvider.getIfAvailable();
      if (outbox != null) {
        outbox.enqueue(
            Destination.KAFKA,
            CANAL_PERSONA,
            String.valueOf(p.id()),
            String.valueOf(p.id()),
            p.version(),
            TIPO_EVENTO,
            json);
      }
    } catch (Exception e) {
      throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }
  }

  /** Registra PERSONA_SEARCH (listar) con actor, recurso, id_hotel y correlación. */
  public void auditarSearch(String actor, Integer idHotel, String correlationId) {
    java.util.Map<String, Object> changes = new java.util.LinkedHashMap<>();
    changes.put("campos", List.of("consulta"));
    changes.put("antes", null);
    java.util.Map<String, Object> despues = new java.util.LinkedHashMap<>();
    despues.put("documento", null);
    changes.put("despues", despues);
    auditar("PERSONA_SEARCH", "listar", null, null, correlationId, changes, actor, idHotel);
  }

  /** Registra PERSONA_READ (buscar) con actor, recurso, id_hotel y correlación. */
  public void auditarRead(String actor, int resourceId, Integer idHotel, String correlationId) {
    java.util.Map<String, Object> changes = new java.util.LinkedHashMap<>();
    changes.put("campos", List.of("lectura"));
    changes.put("antes", null);
    java.util.Map<String, Object> despues = new java.util.LinkedHashMap<>();
    despues.put("idPersona", resourceId);
    changes.put("despues", despues);
    auditar("PERSONA_READ", String.valueOf(resourceId), null, null, correlationId, changes, actor, idHotel);
  }

  private void auditar(
      String action, String resourceId,
      Map<String, Object> antes, Map<String, Object> despues,
      String correlationId, Map<String, Object> changes,
      String actor, Integer idHotel) {
    try {
      String changesJson = mapper.writeValueAsString(changes);
      UUID idUuid = UUID.randomUUID();
      UUID correlationUuid;
      try {
        correlationUuid =
            correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID()
                : UUID.fromString(correlationId);
      } catch (IllegalArgumentException noEsUuid) {
        correlationUuid = UUID.randomUUID();
      }
      jdbc.update(
          """
          INSERT INTO audit_log (id, actor, action, resource_id, id_hotel, correlation_id, occurred_at, changes)
          VALUES (?, ?, ?, ?, ?, ?, now(), CAST(? AS jsonb))
          """,
          idUuid,
          actor == null || actor.isBlank() ? "system" : actor,
          action,
          resourceId,
          idHotel,
          correlationUuid,
          changesJson);
    } catch (Exception e) {
      log.error("auditoria_fallida action={} resource_id={} error={}", action, resourceId,
          e.getMessage(), e);
      throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private static String codigoDe(int idTipoPersona) {
    return switch (idTipoPersona) {
      case 1 -> "ADMIN";
      case 2 -> "EMPLEADO";
      case 3 -> "CLIENTE";
      default -> "?";
    };
  }

  /** Resultado paginado de listar personas. */
  public record PersonaList(List<Persona> personas, int page, int size, long total) {}
}
