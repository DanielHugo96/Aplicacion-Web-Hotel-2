package pe.hotel.sales.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.hotel.sales.domain.model.DetalleRow;
import pe.hotel.sales.domain.model.OperacionRow;
import pe.hotel.sales.domain.model.VentaRow;
import pe.hotel.sales.domain.repository.SalesRepository;

/**
 * {@code SalesRepository} en memoria con las mismas reglas CAS que el SQL real
 * ([docs/especificacion/microservicios/sales/base-de-datos.md § Funciones/procedimientos]). Sirve
 * para probar la máquina de estados de la venta y de la operación sin PostgreSQL.
 *
 * <p>No sustituye a la base real: el SQL de la recuperación se prueba contra {@link
 * SalesJdbcFalso} y la base real, en la corrida de integración con el Compose levantado. Aquí se
 * comprueba la regla que el servicio y el recuperador aplican.
 */
public class SalesRepositoryFalso extends SalesRepository {

  /** Estado mutable de una venta. */
  public static final class Venta {
    public int id = 1;
    public int idRecepcion = 22;
    public int idCliente = 100;
    public int idHotel = 1;
    public String estado = "PENDIENTE";
    public String estadoOperacion = "CONFIRMADA";
    public boolean admitida = true;
    public String origen = "NUEVA";
    public long inventoryVersion;
    public BigDecimal total = new BigDecimal("8.00");
    public String medioPago;
    public UUID currentOperationId;
    public String motivoCancelacion;
    public long version = 2;
    public Instant createdAt = Instant.now().minusSeconds(600);
    public Instant updatedAt = Instant.now().minusSeconds(600);
    public Instant paidAt;
    public UUID closureId;
  }

  /** Estado mutable de una operación de venta. */
  public static final class Operacion {
    public UUID operationId = UUID.randomUUID();
    public int idVenta = 1;
    public String tipo = "PAGO";
    public String estado = "PREPARANDO";
    public String request = "{\"medioPago\":\"TARJETA\"}";
    public int actorId = 1;
    public String actorRol = "ADMIN";
    public Instant createdAt = Instant.now().minusSeconds(600);
    public Instant nextAttemptAt = Instant.now().minusSeconds(5);
    public int attempts;
  }

  private final Map<Integer, Venta> ventas = new LinkedHashMap<>();
  private final Map<UUID, Operacion> operaciones = new LinkedHashMap<>();
  private final Map<Integer, List<DetalleRow>> detalles = new LinkedHashMap<>();
  private Runnable alSeleccionar = () -> {};

  public SalesRepositoryFalso() {
    super(new JdbcTemplate());
  }

  /** Alta una venta con su detalle. */
  public Venta venta(Venta venta) {
    this.ventas.put(venta.id, venta);
    this.detalles.put(venta.id, List.of(new DetalleRow(1, venta.id, 2, "Snack", 1, new BigDecimal("8.00"), new BigDecimal("8.00"))));
    return venta;
  }

  /** Alta una operación de una venta. */
  public Operacion operacion(Operacion operacion) {
    this.operaciones.put(operacion.operationId, operacion);
    Venta venta = this.ventas.get(operacion.idVenta);
    if (venta != null) {
      venta.currentOperationId = operacion.operationId;
    }
    return operacion;
  }

  /** Operación vigente de la venta (la que {@code current_operation_id} apunta). */
  public Operacion operacionDeLaVenta(int idVenta) {
    Venta venta = this.ventas.get(idVenta);
    if (venta == null || venta.currentOperationId == null) {
      return null;
    }
    return this.operaciones.get(venta.currentOperationId);
  }

  /**
   * Acción que se ejecuta al terminar la selección de candidatas: simula el cambio de estado que
   * ocurre entre que la recuperación elige la operación y la reintenta (por ejemplo, el cierre
   * paga la venta en ese hueco).
   */
  public void alSeleccionar(Runnable accion) {
    this.alSeleccionar = accion;
  }

  @Override
  public Optional<VentaRow> venta(int id) {
    Venta venta = this.ventas.get(id);
    if (venta == null) {
      return Optional.empty();
    }
    return Optional.of(
        new VentaRow(
            venta.id,
            venta.idRecepcion,
            venta.idCliente,
            venta.idHotel,
            venta.estado,
            venta.estadoOperacion,
            venta.admitida,
            venta.origen,
            venta.inventoryVersion,
            venta.total,
            venta.medioPago,
            "[]",
            venta.currentOperationId,
            venta.motivoCancelacion,
            venta.version,
            venta.createdAt,
            venta.updatedAt,
            venta.paidAt,
            venta.closureId));
  }

  @Override
  public List<DetalleRow> detalles(int idVenta) {
    return this.detalles.getOrDefault(idVenta, List.of());
  }

  @Override
  public Optional<OperacionRow> operacion(UUID operationId) {
    Operacion operacion = this.operaciones.get(operationId);
    return operacion == null ? Optional.empty() : Optional.of(fila(operacion));
  }

  @Override
  public Optional<OperacionRow> operacionAbierta(int idVenta) {
    return this.operaciones.values().stream()
        .filter(operacion -> operacion.idVenta == idVenta && !"TERMINAL".equals(operacion.estado))
        .findFirst()
        .map(SalesRepositoryFalso::fila);
  }

  @Override
  public void insertarOperacion(
      UUID operationId,
      int idVenta,
      String tipo,
      String estado,
      String requestJson,
      int actorId,
      String actorRol) {
    Operacion operacion = new Operacion();
    operacion.operationId = operationId;
    operacion.idVenta = idVenta;
    operacion.tipo = tipo;
    operacion.estado = estado;
    operacion.request = requestJson;
    operacion.actorId = actorId;
    operacion.actorRol = actorRol;
    operacion.createdAt = Instant.now();
    operacion.nextAttemptAt = Instant.now();
    this.operaciones.put(operationId, operacion);
  }

  @Override
  public void actualizarOperacionActual(int idVenta, UUID operationId) {
    Venta venta = this.ventas.get(idVenta);
    if (venta != null) {
      venta.currentOperationId = operationId;
    }
  }

  @Override
  public void marcarOperacionAdmitida(UUID operationId) {
    Operacion operacion = this.operaciones.get(operationId);
    if (operacion != null && "PREPARANDO".equals(operacion.estado)) {
      operacion.estado = "ADMITIDA";
      operacion.nextAttemptAt = Instant.now();
    }
  }

  @Override
  public void terminarOperacionSiCoincide(UUID operationId, int idVenta) {
    Operacion operacion = this.operaciones.get(operationId);
    if (operacion != null && operacion.idVenta == idVenta && !"TERMINAL".equals(operacion.estado)) {
      operacion.estado = "TERMINAL";
    }
  }

  @Override
  public void terminarOperacionesAbiertas(int idVenta, UUID excepto) {
    this.operaciones.values().stream()
        .filter(operacion -> operacion.idVenta == idVenta)
        .filter(operacion -> !"TERMINAL".equals(operacion.estado))
        .filter(operacion -> !operacion.operationId.equals(excepto))
        .forEach(operacion -> operacion.estado = "TERMINAL");
  }

  @Override
  public boolean pagarVenta(
      int idVenta, UUID operationId, long version, String medioPago, UUID closureId) {
    Venta venta = this.ventas.get(idVenta);
    if (venta == null) {
      return false;
    }
    if (!"CONFIRMADA".equals(venta.estadoOperacion) || !"PENDIENTE".equals(venta.estado)) {
      return false;
    }
    if (venta.version != version) {
      return false;
    }
    venta.estado = "PAGADO";
    venta.medioPago = medioPago;
    venta.paidAt = Instant.now();
    venta.closureId = closureId != null ? closureId : venta.closureId;
    venta.version = venta.version + 1;
    terminarOperacionSiCoincide(operationId, idVenta);
    return true;
  }

  @Override
  public boolean solicitarCancelacion(int idVenta, String motivo) {
    Venta venta = this.ventas.get(idVenta);
    if (venta == null || !"PENDIENTE".equals(venta.estado)) {
      return false;
    }
    if (!"PENDIENTE".equals(venta.estadoOperacion)
        && !"CONFIRMADA".equals(venta.estadoOperacion)) {
      return false;
    }
    venta.estadoOperacion = "CANCELACION_PENDIENTE";
    venta.motivoCancelacion = motivo;
    venta.version = venta.version + 1;
    return true;
  }

  @Override
  public boolean rechazarVenta(int idVenta, UUID operationId) {
    Venta venta = this.ventas.get(idVenta);
    if (venta == null || !"PENDIENTE".equals(venta.estadoOperacion)) {
      return false;
    }
    venta.estadoOperacion = "RECHAZADA";
    venta.version = venta.version + 1;
    terminarOperacionSiCoincide(operationId, idVenta);
    return true;
  }

  @Override
  public void posponerOperacion(UUID operationId, int segundos) {
    Operacion operacion = this.operaciones.get(operationId);
    if (operacion != null) {
      operacion.attempts = operacion.attempts + 1;
      operacion.nextAttemptAt = Instant.now().plusSeconds(segundos);
    }
  }

  @Override
  public List<Recuperable> preparandoVencidas(int staleSeconds) {
    List<Recuperable> candidatas = new ArrayList<>();
    Instant ahora = Instant.now();
    for (Venta venta : this.ventas.values()) {
      Operacion operacion = operacionDeLaVenta(venta.id);
      if (operacion == null || !"PREPARANDO".equals(operacion.estado)) {
        continue;
      }
      if ("VENTA".equals(operacion.tipo)
          && "PENDIENTE".equals(venta.estadoOperacion)
          && operacion.createdAt.isBefore(ahora.minusSeconds(staleSeconds))) {
        candidatas.add(recuperable(venta, operacion));
      }
      if ("PAGO".equals(operacion.tipo)
          && "PENDIENTE".equals(venta.estado)
          && ("PENDIENTE".equals(venta.estadoOperacion) || "CONFIRMADA".equals(venta.estadoOperacion))
          && !operacion.nextAttemptAt.isAfter(ahora)) {
        candidatas.add(recuperable(venta, operacion));
      }
      if ("ANULACION".equals(operacion.tipo)
          && ("PENDIENTE".equals(venta.estadoOperacion) || "CONFIRMADA".equals(venta.estadoOperacion))
          && !operacion.nextAttemptAt.isAfter(ahora)) {
        candidatas.add(recuperable(venta, operacion));
      }
    }
    alSeleccionar.run();
    return candidatas;
  }

  @Override
  public List<Recuperable> operacionesPorReintentar(String tipo) {
    List<Recuperable> candidatas = new ArrayList<>();
    Instant ahora = Instant.now();
    for (Venta venta : this.ventas.values()) {
      Operacion operacion = operacionDeLaVenta(venta.id);
      if (operacion != null
          && tipo.equals(operacion.tipo)
          && "ADMITIDA".equals(operacion.estado)
          && !operacion.nextAttemptAt.isAfter(ahora)) {
        candidatas.add(recuperable(venta, operacion));
      }
    }
    return candidatas;
  }

  @Override
  public List<Recuperable> debitoVencido(int staleSeconds) {
    return List.of();
  }

  @Override
  public List<Recuperable> cancelacionesPendientes() {
    return List.of();
  }

  @Override
  public boolean existeComandoRestituirPendiente(int idVenta) {
    return false;
  }

  private Recuperable recuperable(Venta venta, Operacion operacion) {
    return new Recuperable(venta.id, operacion.operationId, operacion.tipo, venta.estadoOperacion);
  }

  private static OperacionRow fila(Operacion operacion) {
    return new OperacionRow(
        operacion.operationId,
        operacion.idVenta,
        operacion.tipo,
        operacion.estado,
        operacion.request,
        operacion.actorId,
        operacion.actorRol,
        operacion.createdAt,
        operacion.nextAttemptAt,
        operacion.attempts);
  }
}
