package pe.hotel.identity.persona;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
   * Perfil propio con control de versión: devuelve las filas afectadas. Con cero, la versión no era
   * la vigente y la respuesta es 412.
   */
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

  /**
   * Cambio de clave. No incrementa {@code version}: el ETag del perfil describe nombre, apellido y
   * foto, y cambiar la clave no invalida lo que el cliente tiene en pantalla.
   */
  public void actualizarClave(int id, String claveHash) {
    jdbcTemplate.update(
        "UPDATE persona SET clave_hash = ?, updated_at = now() WHERE id = ?", claveHash, id);
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

  private List<Integer> sedesDe(int idPersona) {
    return jdbcTemplate.queryForList(
        "SELECT id_hotel FROM persona_sede WHERE id_persona = ? ORDER BY id_hotel", Integer.class,
        idPersona);
  }
}