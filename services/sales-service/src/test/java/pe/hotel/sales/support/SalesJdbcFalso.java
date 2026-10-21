package pe.hotel.sales.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link JdbcTemplate} en memoria con el esquema de {@code venta} y {@code operacion_venta} que usa
 * {@code SalesRepository}. Evalúa en memoria el {@code WHERE} de las consultas de recuperación para
 * comprobar qué operaciones selecciona la recuperación sin levantar PostgreSQL.
 *
 * <p>Una condición que no simula hace fallar la prueba: el SQL y la prueba no pueden divergir en
 * silencio. El SQL real de la recuperación se prueba contra la base en la corrida de integración;
 * aquí se prueba la regla de selección.
 */
public final class SalesJdbcFalso extends JdbcTemplate {

  /** Fila de {@code venta} con lo que la recuperación consulta. */
  public static final class VentaFila {
    public int id;
    public int idRecepcion = 22;
    public int idCliente = 100;
    public int idHotel = 1;
    public String estado = "PENDIENTE";
    public String estadoOperacion = "CONFIRMADA";
    public UUID currentOperationId;
    public long version = 2;
  }

  /** Fila de {@code operacion_venta} con lo que la recuperación consulta. */
  public static final class OperacionFila {
    public UUID operationId;
    public int idVenta;
    public String tipo = "PAGO";
    public String estado = "PREPARANDO";
    public Instant createdAt = Instant.now().minusSeconds(600);
    public Instant nextAttemptAt = Instant.now().minusSeconds(5);
    public int attempts;
  }

  private final List<VentaFila> ventas = new ArrayList<>();
  private final List<OperacionFila> operaciones = new ArrayList<>();

  public SalesJdbcFalso(VentaFila... ventas) {
    this.ventas.addAll(Arrays.asList(ventas));
  }

  /** Alta una venta. */
  public SalesJdbcFalso ventas(VentaFila... nuevas) {
    this.ventas.addAll(Arrays.asList(nuevas));
    return this;
  }

  /** Alta una operación de la venta dada. */
  public SalesJdbcFalso operacion(OperacionFila operacion) {
    this.operaciones.add(operacion);
    return this;
  }

  @Override
  public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
    if (!sql.contains("FROM venta v JOIN operacion_venta o")) {
      throw new UnsupportedOperationException("consulta no simulada: " + sql);
    }
    String where = sql.substring(sql.indexOf("WHERE") + "WHERE".length()).replaceAll("\\s+", " ").trim();
    List<Object> parametros = args == null ? List.of() : new ArrayList<>(Arrays.asList(args));
    List<T> resultado = new ArrayList<>();
    for (VentaFila venta : ventas) {
      OperacionFila operacion = operacionDe(venta);
      if (operacion == null) {
        // El JOIN es por current_operation_id: la venta sin operación vigente no sale.
        continue;
      }
      if (!cumple(where, venta, operacion, parametros)) {
        continue;
      }
      try {
        resultado.add(rowMapper.mapRow(resultSet(venta, operacion), resultado.size()));
      } catch (SQLException ex) {
        throw new DataAccessResourceFailureException("no se pudo mapear la fila", ex);
      }
    }
    return resultado;
  }

  private OperacionFila operacionDe(VentaFila venta) {
    if (venta.currentOperationId == null) {
      return null;
    }
    for (OperacionFila operacion : operaciones) {
      if (venta.currentOperationId.equals(operacion.operationId)) {
        return operacion;
      }
    }
    return null;
  }

  /** Evalúa las condiciones del {@code WHERE} en el orden en que aparecen. */
  private boolean cumple(
      String where, VentaFila venta, OperacionFila operacion, List<Object> parametros) {
    for (String condicion : where.split(" AND ")) {
      if (!cumpleCondicion(condicion.trim(), venta, operacion, parametros)) {
        return false;
      }
    }
    return true;
  }

  private boolean cumpleCondicion(
      String condicion, VentaFila venta, OperacionFila operacion, List<Object> parametros) {
    if (condicion.startsWith("o.tipo = ")) {
      return operacion.tipo.equals(literal(condicion));
    }
    if (condicion.startsWith("o.estado = ")) {
      return operacion.estado.equals(literal(condicion));
    }
    if (condicion.startsWith("v.estado = ")) {
      return venta.estado.equals(literal(condicion));
    }
    if (condicion.startsWith("v.estado_operacion = ")) {
      return venta.estadoOperacion.equals(literal(condicion));
    }
    if (condicion.startsWith("v.estado_operacion IN (")) {
      return lista(condicion).contains(venta.estadoOperacion);
    }
    if (condicion.equals("o.next_attempt_at <= now()")) {
      return !operacion.nextAttemptAt.isAfter(Instant.now());
    }
    if (condicion.startsWith("o.created_at < now() - (")) {
      if (parametros.isEmpty()) {
        throw new UnsupportedOperationException("falta el parámetro de antigüedad: " + condicion);
      }
      int segundos = ((Number) parametros.remove(0)).intValue();
      return operacion.createdAt.isBefore(Instant.now().minusSeconds(segundos));
    }
    throw new UnsupportedOperationException("condición no simulada: " + condicion);
  }

  /** Literal entre comillas simples de una condición {@code columna = 'valor'}. */
  private String literal(String condicion) {
    int primero = condicion.indexOf('\'');
    int ultimo = condicion.lastIndexOf('\'');
    if (primero < 0 || ultimo <= primero) {
      throw new UnsupportedOperationException("literal no simulado: " + condicion);
    }
    return condicion.substring(primero + 1, ultimo);
  }

  /** Literales de una condición {@code columna IN ('a','b')}. */
  private List<String> lista(String condicion) {
    int abre = condicion.indexOf('(');
    int cierra = condicion.lastIndexOf(')');
    if (abre < 0 || cierra <= abre) {
      throw new UnsupportedOperationException("lista no simulada: " + condicion);
    }
    List<String> valores = new ArrayList<>();
    for (String parte : condicion.substring(abre + 1, cierra).split(",")) {
      valores.add(parte.trim().replace("'", ""));
    }
    return valores;
  }

  /** {@link ResultSet} con la fila, para que el {@code RowMapper} real del repositorio la mapee. */
  private ResultSet resultSet(VentaFila venta, OperacionFila operacion) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getInt("id")).thenReturn(venta.id);
    when(rs.getObject("operation_id", UUID.class)).thenReturn(operacion.operationId);
    when(rs.getString("tipo")).thenReturn(operacion.tipo);
    when(rs.getString("estado_operacion")).thenReturn(venta.estadoOperacion);
    return rs;
  }
}
