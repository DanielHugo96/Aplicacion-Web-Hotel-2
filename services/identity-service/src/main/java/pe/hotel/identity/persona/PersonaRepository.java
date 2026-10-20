package pe.hotel.identity.persona;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code persona}, {@code persona_sede} y {@code tipo_persona}.
 *
 * <p>Las credenciales nunca se comparan en SQL: la búsqueda trae el {@code clave_hash} y la
 * comprobación es {@code BCryptPasswordEncoder.matches} en Java
 * (docs/especificacion/microservicios/identity/base-de-datos.md § Procedimientos y funciones).
 */
@Repository
public class PersonaRepository {

  /** Rol fijo del cliente; el registro público no permite elegir otro. */
  public static final int ID_TIPO_CLIENTE = 3;

  private static final String SELECT_BASE =
      """
      SELECT p.id, p.tipo_documento, p.documento, p.nombre, p.apellido, p.correo, p.clave_hash,
             p.foto_url, p.id_tipo_persona, t.codigo, p.estado, p.version, p.created_at
        FROM persona p
        JOIN tipo_persona t ON t.id = p.id_tipo_persona
      """;

  private static final String SELECT_TIPO_BASE =
      """
      SELECT id, descripcion, codigo, estado, version
        FROM tipo_persona
      """;

  private final JdbcTemplate jdbcTemplate;

  public PersonaRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Persona por correo normalizado, con sus sedes; vacía si no existe o está inactiva. */
  public Optional<Persona> buscarPorCorreo(String correo) {
    return primero(SELECT_BASE + " WHERE p.correo = ?", correo);
  }

  /** Persona por id, con sus sedes. */
  public Optional<Persona> buscarPorId(int id) {
    return primero(SELECT_BASE + " WHERE p.id = ?", id);
  }

  private Optional<Persona> primero(String sql, Object... argumentos) {
    // queryForObject lanza EmptyResultDataAccessException si no hay fila: aquí "no existe" es un
    // valor normal, no un fallo.
    return jdbcTemplate.query(sql, mapper(), argumentos).stream().findFirst();
  }

  public boolean existeCorreo(String correo) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM persona WHERE correo = ?)", Boolean.class, correo));
  }

  public boolean existeDocumento(String tipoDocumento, String documento) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM persona WHERE tipo_documento = ? AND documento = ?)",
            Boolean.class,
            tipoDocumento,
            documento));
  }

  /**
   * Listado paginado de personas con filtros opcionales. Orden estable por id ascendente.
   */
  public List<Persona> listar(Integer idTipoPersona, String documento, int offset, int limit) {
    StringBuilder sql = new StringBuilder(SELECT_BASE + " WHERE 1=1");
    java.util.List<Object> args = new java.util.ArrayList<>();
    if (idTipoPersona != null) {
      sql.append(" AND p.id_tipo_persona = ?");
      args.add(idTipoPersona);
    }
    if (documento != null && !documento.isBlank()) {
      sql.append(" AND p.documento = ?");
      args.add(documento.trim());
    }
    sql.append(" ORDER BY p.id LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbcTemplate.query(sql.toString(), mapper(), args.toArray());
  }

  /** Total de filas para paginación con los mismos filtros. */
  public long contar(Integer idTipoPersona, String documento) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM persona p WHERE 1=1");
    java.util.List<Object> args = new java.util.ArrayList<>();
    if (idTipoPersona != null) {
      sql.append(" AND p.id_tipo_persona = ?");
      args.add(idTipoPersona);
    }
    if (documento != null && !documento.isBlank()) {
      sql.append(" AND p.documento = ?");
      args.add(documento.trim());
    }
    return jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
  }

  /**
   * Alta de persona. El rol y el estado los fija quien llama (el registro público siempre CLIENTE
   * activo); el hash llega ya calculado con BCrypt desde Java.
   *
   * @return id generado por la identidad de la tabla
   */
  public int insertar(
      String tipoDocumento,
      String documento,
      String nombre,
      String apellido,
      String correo,
      String claveHash,
      int idTipoPersona) {
    Integer id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO persona (tipo_documento, documento, nombre, apellido, correo, clave_hash,
                                 foto_url, id_tipo_persona, estado, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, true, 1, now(), now())
            RETURNING id
            """,
            (rs, fila) -> rs.getInt("id"),
            tipoDocumento,
            documento,
            nombre,
            apellido,
            correo,
            claveHash,
            idTipoPersona);
    if (id == null) {
      throw new IllegalStateException("la base no devolvio el id de la persona creada");
    }
    return id;
  }

  /**
   * Actualización general de persona con control de versión optimista: {@code version} se incrementa
   * solo si la fila coincide con la esperada. Devuelve filas afectadas (0 si la versión no casa →
   * 412).
   *
   * <p>No toca documento ni contraseña (esos van por otros caminos).
   */
  public int actualizar(
      int id,
      String nombre,
      String apellido,
      String correo,
      String fotoUrl,
      Integer idTipoPersona,
      Boolean estado,
      long versionEsperada) {
    int filas =
        jdbcTemplate.update(
            """
            UPDATE persona
               SET nombre = ?, apellido = ?, correo = ?, foto_url = ?,
                   id_tipo_persona = ?, estado = ?, version = version + 1, updated_at = now()
             WHERE id = ? AND version = ?
            """,
            nombre,
            apellido,
            correo,
            fotoUrl,
            idTipoPersona,
            estado,
            id,
            versionEsperada);
    if (filas == 0) {
      throw new OptimisticLockingFailureException(
          "la persona " + id + " tiene version distinta a la esperada " + versionEsperada);
    }
    return filas;
  }

  /**
   * Baja lógica: pone {@code estado = false} con control de versión. Devuelve filas afectadas.
   */
  public int desactivar(int id, long versionEsperada) {
    int filas =
        jdbcTemplate.update(
            """
            UPDATE persona
               SET estado = false, version = version + 1, updated_at = now()
             WHERE id = ? AND version = ?
            """,
            id,
            versionEsperada);
    if (filas == 0) {
      throw new OptimisticLockingFailureException(
          "la persona " + id + " tiene version distinta a la esperada " + versionEsperada);
    }
    return filas;
  }

  /** Reemplaza las sedes de una persona: borra las anteriores e inserta las nuevas. */
  public void reemplazarSedes(int idPersona, List<Integer> sedes) {
    jdbcTemplate.update("DELETE FROM persona_sede WHERE id_persona = ?", idPersona);
    for (Integer sede : sedes) {
      jdbcTemplate.update(
          "INSERT INTO persona_sede (id_persona, id_hotel) VALUES (?, ?)", idPersona, sede);
    }
  }

  /** Perfil propio con control de versión: devuelve las filas afectadas. Con cero, 412. */
  public int actualizarPerfil(
      int id, String nombre, String apellido, String fotoUrl, long versionEsperada) {
    return jdbcTemplate.update(
        """
        UPDATE persona
           SET nombre = ?, apellido = ?, foto_url = ?, version = version + 1, updated_at = now()
         WHERE id = ? AND version = ?
        """,
        nombre,
        apellido,
        fotoUrl,
        id,
        versionEsperada);
  }

  public Persona leerDespuesDeActualizar(int id) {
    return buscarPorId(id)
        .orElseThrow(() -> new IllegalStateException("la persona actualizada no existe: " + id));
  }

  /** Cambio de clave. No incrementa {@code version}. */
  public void actualizarClave(int id, String claveHash) {
    jdbcTemplate.update(
        "UPDATE persona SET clave_hash = ?, updated_at = now() WHERE id = ?", claveHash, id);
  }

  /**
   * Cuenta ADMIN activos con clave_hash configurada. Se usa para la protección del último ADMIN:
   * tras una baja o degradación debe quedar al menos uno.
   */
  public long contarAdminActivosConClave() {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM persona p
        JOIN tipo_persona t ON t.id = p.id_tipo_persona
        WHERE t.codigo = 'ADMIN' AND p.estado = true AND p.clave_hash IS NOT NULL
        """,
        Long.class);
  }

  /**
   * Bloquea la fila fija del rol ADMIN con {@code SELECT FOR UPDATE}. Es el mutex que serializa
   * altas, cambios de rol/estado y bajas de administradores.
   */
  public void bloquearRolAdmin() {
    jdbcTemplate.queryForObject(
        "SELECT id FROM tipo_persona WHERE codigo = 'ADMIN' FOR UPDATE", (rs, n) -> rs.getInt("id"));
  }

  /** Tipo de persona por id; vacía si el id no pertenece al catálogo fijo (1..3). */
  public Optional<TipoPersona> buscarTipoPersona(int id) {
    return jdbcTemplate.query(
            SELECT_TIPO_BASE + " WHERE id = ?", tipoPersonaMapper(), id)
        .stream()
        .findFirst();
  }

  /** Los tres roles del catálogo fijo, ordenados por id. */
  public List<TipoPersona> listarTiposPersona() {
    return jdbcTemplate.query(SELECT_TIPO_BASE + " ORDER BY id", tipoPersonaMapper());
  }

  /**
   * Edita solo la etiqueta descripcion de un tipo de persona con control de versión. Código,
   * permisos y estado nunca cambian por esta vía.
   */
  public int actualizarTipoPersona(int id, String descripcion, long versionEsperada) {
    int filas =
        jdbcTemplate.update(
            """
            UPDATE tipo_persona
               SET descripcion = ?, version = version + 1
             WHERE id = ? AND version = ?
            """,
            descripcion,
            id,
            versionEsperada);
    if (filas == 0) {
      throw new OptimisticLockingFailureException(
          "el tipo de persona " + id + " tiene version distinta a la esperada " + versionEsperada);
    }
    return filas;
  }

  /**
   * Cliente vigente para lectura máquina: solo persona activa de tipo CLIENTE, sin secreto.
   * Equivalente a {@code fn_cliente_vigente(p_id)}.
   */
  public Optional<ClienteVigente> clienteVigente(int id) {
    return jdbcTemplate.query(
            """
            SELECT p.id, p.tipo_documento, p.documento, p.nombre, p.apellido, p.correo,
                   p.id_tipo_persona, p.version
              FROM persona p
              JOIN tipo_persona t ON t.id = p.id_tipo_persona
             WHERE p.id = ? AND p.estado = true AND t.codigo = 'CLIENTE'
            """,
            (rs, rowNum) ->
                new ClienteVigente(
                    rs.getInt("id"),
                    rs.getString("tipo_documento"),
                    rs.getString("documento"),
                    rs.getString("nombre"),
                    rs.getString("apellido"),
                    rs.getString("correo"),
                    true,
                    rs.getInt("id_tipo_persona"),
                    rs.getLong("version")),
            id)
        .stream()
        .findFirst();
  }

  private RowMapper<Persona> mapper() {
    return new RowMapper<>() {
      @Override
      public Persona mapRow(ResultSet rs, int rowNum) throws SQLException {
        int id = rs.getInt("id");
        String codigo = rs.getString("codigo");
        return new Persona(
            id,
            rs.getString("tipo_documento"),
            rs.getString("documento"),
            rs.getString("nombre"),
            rs.getString("apellido"),
            rs.getString("correo"),
            rs.getString("clave_hash"),
            rs.getString("foto_url"),
            rs.getInt("id_tipo_persona"),
            codigo,
            Persona.tipoPersonaDe(codigo),
            rs.getBoolean("estado"),
            rs.getLong("version"),
            rs.getObject("created_at", OffsetDateTime.class),
            sedesDe(id));
      }
    };
  }

  private RowMapper<TipoPersona> tipoPersonaMapper() {
    return new RowMapper<>() {
      @Override
      public TipoPersona mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TipoPersona(
            rs.getInt("id"),
            rs.getString("descripcion"),
            rs.getString("codigo"),
            rs.getBoolean("estado"),
            rs.getLong("version"));
      }
    };
  }

  private List<Integer> sedesDe(int idPersona) {
    return jdbcTemplate.queryForList(
        "SELECT id_hotel FROM persona_sede WHERE id_persona = ? ORDER BY id_hotel", Integer.class,
        idPersona);
  }
}

/** Datos mínimos de cliente vigente expuestos por la ruta interna. */
record ClienteVigente(
    int idPersona,
    String tipoDocumento,
    String documento,
    String nombre,
    String apellido,
    String correo,
    boolean estado,
    int idTipoPersona,
    long version) {}
