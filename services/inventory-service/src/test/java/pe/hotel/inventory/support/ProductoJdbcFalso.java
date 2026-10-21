package pe.hotel.inventory.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link JdbcTemplate} en memoria con el esquema de {@code producto} y {@code producto_sede} que usa
 * {@code ProductoService}. Sirve para probar el servicio sin PostgreSQL: evalúa los predicados
 * {@code p.id}, {@code p.estado}, {@code ps.id_hotel} y {@code ps.estado} de la cláusula
 * {@code WHERE} y simula {@code fn_stock_ajustar} (lock, saldo no negativo y {@code version} de la
 * sede).
 *
 * <p>No sustituye a la base real: la base se prueba en {@code StockDbIT} y en la corrida real del
 * Gateway. Aquí se comprueba la regla que el servicio aplica.
 */
public final class ProductoJdbcFalso extends JdbcTemplate {

  /** Fila de {@code producto_sede} con su producto global; el estado y el saldo son mutables. */
  public static final class Fila {
    public int id;
    public String sku;
    public String nombre = "Producto";
    public String detalle = "Detalle";
    public String imagenUrl = "/assets/demo/producto-agua.png";
    public boolean estadoProducto = true;
    public int versionProducto = 1;
    public int idHotel;
    public BigDecimal precio = new BigDecimal("3.50");
    public int cantidad;
    public int umbralBajo = 5;
    public boolean estadoSede = true;
    public int versionSede = 1;

    public Fila(int id, int idHotel, int cantidad) {
      this.id = id;
      this.idHotel = idHotel;
      this.cantidad = cantidad;
      this.sku = "SKU-" + id;
    }

    public Fila estadoProducto(boolean valor) {
      this.estadoProducto = valor;
      return this;
    }

    public Fila estadoSede(boolean valor) {
      this.estadoSede = valor;
      return this;
    }

    public Fila versionSede(int valor) {
      this.versionSede = valor;
      return this;
    }
  }

  private final List<Fila> filas = new ArrayList<>();
  private final List<String> consultas = new ArrayList<>();
  private final List<Object[]> parametros = new ArrayList<>();
  private final List<Object> actoresAuditados = new ArrayList<>();

  public ProductoJdbcFalso(Fila... filas) {
    this.filas.addAll(Arrays.asList(filas));
  }

  /** SQL de la última consulta ejecutada. */
  public String ultimaConsulta() {
    return consultas.isEmpty() ? "" : consultas.get(consultas.size() - 1);
  }

  /** Parámetros de la última consulta ejecutada. */
  public Object[] ultimosParametros() {
    return parametros.isEmpty() ? new Object[0] : parametros.get(parametros.size() - 1);
  }

  /** SQL de la última consulta que contiene el fragmento dado (por ejemplo {@code fn_stock_ajustar}). */
  public String ultimaConsultaQueContenga(String fragmento) {
    for (int i = consultas.size() - 1; i >= 0; i--) {
      if (consultas.get(i).contains(fragmento)) {
        return consultas.get(i);
      }
    }
    throw new IllegalStateException("ninguna consulta contiene: " + fragmento);
  }

  /** Parámetros de la última consulta que contiene el fragmento dado. */
  public Object[] ultimosParametrosDe(String fragmento) {
    for (int i = parametros.size() - 1; i >= 0; i--) {
      if (consultas.get(i).contains(fragmento)) {
        return parametros.get(i);
      }
    }
    throw new IllegalStateException("ninguna consulta contiene: " + fragmento);
  }

  /** Actores auditados en los ajustes aplicados. */
  public List<Object> actoresAuditados() {
    return actoresAuditados;
  }

  /** Fila por id de producto y sede. */
  public Fila fila(int idProducto, int idHotel) {
    return filas.stream()
        .filter(f -> f.id == idProducto && f.idHotel == idHotel)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no hay fila para el producto " + idProducto));
  }

  @Override
  public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
    registrar(sql, args);
    List<T> resultado = new ArrayList<>();
    for (Fila fila : candidatas(sql, args)) {
      try {
        resultado.add(rowMapper.mapRow(resultSet(fila), resultado.size()));
      } catch (SQLException ex) {
        throw new DataAccessResourceFailureException("no se pudo mapear la fila", ex);
      }
    }
    return resultado;
  }

  @Override
  public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
    registrar(sql, args);
    if (sql.contains("fn_stock_ajustar")) {
      return requiredType.cast(ajustar(args));
    }
    throw new UnsupportedOperationException("consulta no simulada: " + sql);
  }

  private void registrar(String sql, Object[] args) {
    consultas.add(sql);
    parametros.add(args == null ? new Object[0] : args);
  }

  /** Fila que la consulta devuelve: el saldo se ajusta y la versión de la sede sube. */
  private Object ajustar(Object[] args) {
    int idProducto = ((Number) args[1]).intValue();
    int idHotel = ((Number) args[2]).intValue();
    int delta = ((Number) args[3]).intValue();
    Object actor = args[5];
    Fila fila =
        filas.stream()
            .filter(f -> f.id == idProducto && f.idHotel == idHotel)
            .findFirst()
            .orElseThrow(
                () ->
                    new DataAccessResourceFailureException(
                        "el producto " + idProducto + " no existe en la sede " + idHotel));
    int saldo = fila.cantidad + delta;
    if (saldo < 0) {
      throw new DataAccessResourceFailureException("el ajuste dejaría saldo negativo");
    }
    fila.cantidad = saldo;
    fila.versionSede = fila.versionSede + 1;
    actoresAuditados.add(actor);
    return saldo;
  }

  /** Aplica los predicados de la cláusula {@code WHERE} a las filas en memoria. */
  private List<Fila> candidatas(String sql, Object[] args) {
    String donde = sql.substring(sql.indexOf("WHERE") + "WHERE".length());
    int orden = donde.indexOf(" ORDER BY ");
    if (orden >= 0) {
      donde = donde.substring(0, orden);
    }
    List<Object> valores = args == null ? List.of() : Arrays.asList(args);
    List<Fila> candidatas = new ArrayList<>(filas);
    int indice = 0;
    for (String condicion : donde.split("\\s+AND\\s+")) {
      String limpia = condicion.trim();
      if (!limpia.endsWith("= ?")) {
        throw new UnsupportedOperationException("condición no simulada: " + limpia);
      }
      String columna = limpia.substring(0, limpia.length() - "= ?".length()).trim();
      if (indice >= valores.size()) {
        throw new UnsupportedOperationException("faltan parámetros para: " + limpia);
      }
      Object valor = valores.get(indice++);
      candidatas.removeIf(fila -> !cumple(fila, columna, valor));
    }
    return candidatas;
  }

  private boolean cumple(Fila fila, String columna, Object valor) {
    return switch (columna) {
      case "p.id" -> fila.id == ((Number) valor).intValue();
      case "p.estado" -> fila.estadoProducto == (Boolean) valor;
      case "ps.id_hotel" -> fila.idHotel == ((Number) valor).intValue();
      case "ps.estado" -> fila.estadoSede == (Boolean) valor;
      default -> throw new UnsupportedOperationException("columna no simulada: " + columna);
    };
  }

  /** {@link ResultSet} con la fila, para que el {@code RowMapper} real del servicio la mapee. */
  private ResultSet resultSet(Fila fila) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getInt("id")).thenReturn(fila.id);
    when(rs.getString("sku")).thenReturn(fila.sku);
    when(rs.getString("nombre")).thenReturn(fila.nombre);
    when(rs.getString("detalle")).thenReturn(fila.detalle);
    when(rs.getString("imagen_url")).thenReturn(fila.imagenUrl);
    when(rs.getInt("id_hotel")).thenReturn(fila.idHotel);
    when(rs.getObject("precio")).thenReturn(fila.precio);
    when(rs.getInt("cantidad")).thenReturn(fila.cantidad);
    when(rs.getInt("umbral_bajo")).thenReturn(fila.umbralBajo);
    when(rs.getBoolean("ps_estado")).thenReturn(fila.estadoSede);
    when(rs.getBoolean("estado")).thenReturn(fila.estadoProducto);
    when(rs.getObject("created_at", OffsetDateTime.class))
        .thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    when(rs.getInt("ps_version")).thenReturn(fila.versionSede);
    when(rs.getInt("version")).thenReturn(fila.versionProducto);
    return rs;
  }
}
