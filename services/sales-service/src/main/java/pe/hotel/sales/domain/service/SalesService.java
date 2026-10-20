package pe.hotel.sales.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.hotel.sales.client.InventoryClient;
import pe.hotel.sales.client.ReceptionClient;
import pe.hotel.sales.domain.dto.CarritoItem;
import pe.hotel.sales.domain.dto.CatalogoItem;
import pe.hotel.sales.domain.dto.ConfirmacionCierre;
import pe.hotel.sales.domain.dto.DebitoItem;
import pe.hotel.sales.domain.dto.DebitoRequest;
import pe.hotel.sales.domain.dto.DebitoResultado;
import pe.hotel.sales.domain.dto.OperacionAdmisionRequest;
import pe.hotel.sales.domain.dto.OperacionVentaView;
import pe.hotel.sales.domain.dto.PagoRequest;
import pe.hotel.sales.domain.dto.ProductoStock;
import pe.hotel.sales.domain.dto.Receipt;
import pe.hotel.sales.domain.dto.RecepcionResumen;
import pe.hotel.sales.domain.dto.ResumenCierre;
import pe.hotel.sales.domain.dto.VentaAlta;
import pe.hotel.sales.domain.dto.VentaDetalleAlta;
import pe.hotel.sales.domain.dto.VentaDetalleView;
import pe.hotel.sales.domain.dto.VentaView;
import pe.hotel.sales.domain.model.CarritoRow;
import pe.hotel.sales.domain.model.CierreRow;
import pe.hotel.sales.domain.model.DetalleRow;
import pe.hotel.sales.domain.model.OperacionRow;
import pe.hotel.sales.domain.model.VentaRow;
import pe.hotel.sales.domain.repository.SalesRepository;
import pe.hotel.sales.domain.security.CurrentUser;
import pe.hotel.sales.platform.ApiException;
import pe.hotel.sales.platform.ApiResponse;
import pe.hotel.sales.platform.ErrorCode;

/**
 * Casos de uso de sales: carrito y catálogo privado, alta con admisión y débito, pago, anulación,
 * lecturas y cierres. Orquesta sin transacción distribuida: cada paso durable se confirma antes
 * del siguiente Feign y las transiciones son CAS con comprobación de filas.
 */
@Service
public class SalesService {

  private static final Logger log = LoggerFactory.getLogger(SalesService.class);
  private static final Set<String> MEDIOS = Set.of("EFECTIVO", "TARJETA", "OTRO");
  private static final Set<String> ESTADOS_VENTA = Set.of("PENDIENTE", "PAGADO");

  private final SalesRepository repo;
  private final ReceptionClient reception;
  private final InventoryClient inventory;
  private final SalesOutbox outbox;
  private final CurrentUser currentUser;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  public SalesService(
      SalesRepository repo,
      ReceptionClient reception,
      InventoryClient inventory,
      SalesOutbox outbox,
      CurrentUser currentUser,
      PlatformTransactionManager transactionManager) {
    this.repo = repo;
    this.reception = reception;
    this.inventory = inventory;
    this.outbox = outbox;
    this.currentUser = currentUser;
    this.tx = new TransactionTemplate(transactionManager);
  }

  // --------------------------------------------------------------- catálogo

  public record Pagina<T>(List<T> data, long total) {}

  public Pagina<CatalogoItem> catalogo(int idRecepcion, int page, int size) {
    RecepcionResumen estadia = recepcion(idRecepcion);
    requireAccesoRecepcion(estadia);
    if (!"ACTIVA".equals(estadia.estadoEstadia())) {
      throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(idRecepcion)));
    }
    List<ProductoStock> productos;
    try {
      ApiResponse<List<ProductoStock>> respuesta = inventory.productos(estadia.idHotel(), 0, 100);
      productos = respuesta == null || respuesta.data() == null ? List.of() : respuesta.data();
    } catch (FeignException ex) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    List<CatalogoItem> activos =
        productos.stream()
            .filter(p -> Boolean.TRUE.equals(p.estado()))
            .filter(p -> p.precio() != null && p.precio().signum() > 0)
            .map(
                p ->
                    new CatalogoItem(
                        p.idProducto(),
                        p.sku(),
                        p.nombre(),
                        p.detalle(),
                        p.imagenUrl(),
                        p.idHotel(),
                        p.precio(),
                        p.cantidad(),
                        p.umbralBajo(),
                        p.estado(),
                        p.fechaCreacion(),
                        p.version(),
                        p.productoVersion()))
            .toList();
    int from = Math.min(page * size, activos.size());
    int to = Math.min(from + size, activos.size());
    return new Pagina<>(activos.subList(from, to), activos.size());
  }

  // --------------------------------------------------------------- carrito

  public CarritoItem agregarCarrito(int idRecepcion, int idProducto, int cantidad) {
    if (cantidad < 1 || cantidad > 9999) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("cantidad", "entre 1 y 9999"));
    }
    RecepcionResumen estadia = recepcion(idRecepcion);
    requireAccesoRecepcion(estadia);
    if (!"ACTIVA".equals(estadia.estadoEstadia())) {
      throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(idRecepcion)));
    }
    ProductoStock producto = producto(idProducto, estadia.idHotel());
    CarritoRow fila;
    try {
      fila =
          repo.upsertCarrito(
              idRecepcion, estadia.idCliente(), estadia.idHotel(), idProducto, cantidad);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("cantidad", "máximo 9999 por línea"));
    }
    return carritoItem(fila, producto);
  }

  public List<CarritoItem> listarCarrito(int idRecepcion) {
    RecepcionResumen estadia = recepcion(idRecepcion);
    requireAccesoRecepcion(estadia);
    if ("CERRADA".equals(estadia.estadoEstadia())) {
      return List.of();
    }
    List<CarritoRow> filas = repo.carrito(idRecepcion);
    List<CarritoItem> items = new ArrayList<>(filas.size());
    for (CarritoRow fila : filas) {
      items.add(carritoItem(fila, producto(fila.idProducto(), estadia.idHotel())));
    }
    return items;
  }

  public void eliminarCarrito(int idRecepcion, int idProducto) {
    RecepcionResumen estadia = recepcion(idRecepcion);
    requireAccesoRecepcion(estadia);
    repo.quitarCarrito(idRecepcion, idProducto);
  }

  private CarritoItem carritoItem(CarritoRow fila, ProductoStock producto) {
    return new CarritoItem(
        fila.id(),
        fila.idRecepcion(),
        fila.idCliente(),
        fila.idHotel(),
        fila.idProducto(),
        producto.nombre(),
        producto.precio(),
        producto.imagenUrl(),
        fila.cantidad(),
        fila.createdAt(),
        fila.version());
  }

  // --------------------------------------------------------------- ventas

  public record Resultado(int status, VentaView venta) {}

  public Resultado crear(VentaAlta body) {
    validarAlta(body);
    RecepcionResumen estadia = recepcion(body.idRecepcion());
    requireAccesoRecepcion(estadia);
    if (!"ACTIVA".equals(estadia.estadoEstadia())) {
      throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(body.idRecepcion())));
    }
    for (VentaDetalleAlta detalle : body.detalles()) {
      ProductoStock producto = producto(detalle.idProducto(), estadia.idHotel());
      if (!Boolean.TRUE.equals(producto.estado()) || producto.precio() == null || producto.precio().signum() <= 0) {
        throw new ApiException(ErrorCode.PRODUCT_INACTIVE, Map.of("idProducto", String.valueOf(detalle.idProducto())));
      }
    }

    boolean pagar = "PAGADO".equals(body.estado());
    String medio = pagar ? body.medioPago() : null;
    String itemsJson = itemsJson(body.detalles());
    String hash = hash(estadia.idCliente() + ":" + body.idRecepcion() + ":" + itemsJson);
    UUID operationId = UUID.randomUUID();
    int idVenta =
        tx.execute(
            status ->
                repo.insertarVenta(
                    estadia.idRecepcion(),
                    estadia.idCliente(),
                    estadia.idHotel(),
                    itemsJson,
                    hash,
                    operationId,
                    currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
                    rolActual()));

    try {
      reception.admitir(
          estadia.idRecepcion(),
          new OperacionAdmisionRequest(
              operationId,
              idVenta,
              estadia.idHotel(),
              "VENTA",
              currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
              rolActual()));
    } catch (FeignException ex) {
      if (ex.status() == 409) {
        rechazar(idVenta, operationId, estadia);
        throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(estadia.idRecepcion())));
      }
      if (ex instanceof FeignException.Forbidden || ex.status() == 403) {
        rechazar(idVenta, operationId, estadia);
        throw new ApiException(ErrorCode.FORBIDDEN);
      }
      if (ex.status() == 404) {
        rechazar(idVenta, operationId, estadia);
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idRecepcion", String.valueOf(estadia.idRecepcion())));
      }
      // Timeout o caída: la venta queda RECHAZADA con finalizar durable, sin tocar inventory.
      rechazar(idVenta, operationId, estadia);
      return new Resultado(202, ver(idVenta));
    }

    Boolean preparado = tx.execute(status -> repo.prepararDebito(idVenta, operationId));
    if (!Boolean.TRUE.equals(preparado)) {
      return new Resultado(202, ver(idVenta));
    }

    try {
      ApiResponse<DebitoResultado> respuesta =
          inventory.descontar(
              new DebitoRequest(
                  idVenta,
                  estadia.idHotel(),
                  body.detalles().stream()
                      .map(d -> new DebitoItem(d.idProducto(), d.cantidad()))
                      .toList()));
      DebitoResultado resultado = respuesta == null ? null : respuesta.data();
      if (resultado == null) {
        throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
      }
      boolean confirmada =
          Boolean.TRUE.equals(
              tx.execute(
                  status -> {
                    boolean ok =
                        repo.confirmarVenta(idVenta, operationId, resultado, pagar, medio, null);
                    if (ok) {
                      repo.terminarOperacionSiCoincide(operationId, idVenta);
                      VentaRow venta = repo.venta(idVenta).orElseThrow();
                      outbox.emitirVenta(venta, repo.detalles(idVenta));
                      outbox.emitirFinalizar(
                          operationId,
                          venta.idHotel(),
                          venta.idRecepcion(),
                          venta.id(),
                          "VENTA",
                          "CONFIRMADA",
                          venta.version());
                    }
                    return ok;
                  }));
      if (!confirmada) {
        return new Resultado(202, ver(idVenta));
      }
      return new Resultado(201, ver(idVenta));
    } catch (FeignException ex) {
      if (ex.status() == 400 || ex.status() == 409) {
        ErrorCode code = codigoDeError(ex);
        rechazar(idVenta, operationId, estadia);
        throw new ApiException(code, camposDeError(ex));
      }
      compensar(idVenta, operationId, "resultado incierto del débito");
      return new Resultado(202, ver(idVenta));
    } catch (ApiException ex) {
      if (ex.code() == ErrorCode.STOCK_INSUFFICIENT
          || ex.code() == ErrorCode.PRODUCT_INACTIVE
          || ex.code() == ErrorCode.DEBIT_CANCELLED) {
        rechazar(idVenta, operationId, estadia);
        throw ex;
      }
      compensar(idVenta, operationId, "resultado incierto del débito");
      return new Resultado(202, ver(idVenta));
    }
  }

  private void rechazar(int idVenta, UUID operationId, RecepcionResumen estadia) {
    tx.executeWithoutResult(
        status -> {
          if (repo.rechazarVenta(idVenta, operationId)) {
            VentaRow venta = repo.venta(idVenta).orElseThrow();
            outbox.emitirFinalizar(
                operationId,
                venta.idHotel(),
                venta.idRecepcion(),
                venta.id(),
                "VENTA",
                "RECHAZADA",
                venta.version());
          }
        });
  }

  private void compensar(int idVenta, UUID operationId, String motivo) {
    tx.executeWithoutResult(
        status -> {
          if (repo.compensarVenta(idVenta, operationId, motivo)) {
            VentaRow venta = repo.venta(idVenta).orElseThrow();
            outbox.emitirRestituir(
                venta.id(),
                venta.idRecepcion(),
                motivo,
                venta.idHotel(),
                venta.version());
          }
        });
  }

  public Resultado pagar(int idVenta, PagoRequest body, String ifMatch) {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    VentaRow venta = ventaAccesible(idVenta);
    if (body == null || !"PAGADO".equals(body.estado())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("estado", "solo PAGADO"));
    }
    if (body.medioPago() == null || !MEDIOS.contains(body.medioPago())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("medioPago", "EFECTIVO, TARJETA u OTRO"));
    }
    if ("PAGADO".equals(venta.estado())) {
      return new Resultado(200, ver(idVenta));
    }
    if (!"CONFIRMADA".equals(venta.estadoOperacion()) && !"PENDIENTE".equals(venta.estadoOperacion())) {
      throw new ApiException(ErrorCode.DEBIT_CANCELLED, Map.of("idVenta", String.valueOf(idVenta)));
    }
    long version = parseIfMatch(ifMatch);
    if (version != venta.version()) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", "versión distinta a la vigente"));
    }
    OperacionRow abierta = repo.operacionAbierta(idVenta).orElse(null);
    if (abierta != null) {
      if ("PAGO".equals(abierta.tipo())) {
        return new Resultado(202, ver(idVenta));
      }
      throw new ApiException(ErrorCode.OPERATIONS_IN_PROGRESS, Map.of("idVenta", String.valueOf(idVenta)));
    }

    UUID operationId = UUID.randomUUID();
    String request = "{\"medioPago\":\"" + body.medioPago() + "\"}";
    tx.executeWithoutResult(
        status -> {
          repo.insertarOperacion(
              operationId,
              idVenta,
              "PAGO",
              "PREPARANDO",
              request,
              currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
              rolActual());
          repo.actualizarOperacionActual(idVenta, operationId);
        });

    try {
      reception.admitir(
          venta.idRecepcion(),
          new OperacionAdmisionRequest(
              operationId,
              idVenta,
              venta.idHotel(),
              "PAGO",
              currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
              rolActual()));
    } catch (FeignException ex) {
      tx.executeWithoutResult(
          status -> repo.terminarOperacionSiCoincide(operationId, idVenta));
      if (ex.status() == 409) {
        throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(venta.idRecepcion())));
      }
      if (ex.status() == 403) {
        throw new ApiException(ErrorCode.FORBIDDEN);
      }
      if (ex.status() == 404) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idRecepcion", String.valueOf(venta.idRecepcion())));
      }
      // Operación durable en recuperación: el job reintenta la misma admisión y el pago.
      return new Resultado(202, ver(idVenta));
    }

    // Pago aceptado por reception: se persiste ADMITIDA y se aplica si la venta ya está confirmada.
    boolean pagado =
        Boolean.TRUE.equals(
            tx.execute(
                status -> {
                  repo.marcarOperacionAdmitida(operationId);
                  boolean ok = repo.pagarVenta(idVenta, operationId, venta.version(), body.medioPago(), null);
                  if (ok) {
                    VentaRow actual = repo.venta(idVenta).orElseThrow();
                    outbox.emitirVenta(actual, repo.detalles(idVenta));
                    outbox.emitirFinalizar(
                        operationId,
                        actual.idHotel(),
                        actual.idRecepcion(),
                        actual.id(),
                        "PAGO",
                        "PAGO_REGISTRADO",
                        actual.version());
                  }
                  return ok;
                }));
    if (pagado) {
      return new Resultado(200, ver(idVenta));
    }
    VentaView actual = ver(idVenta);
    if ("PAGADO".equals(actual.estado())) {
      return new Resultado(200, actual);
    }
    return new Resultado(202, actual);
  }

  public Resultado anular(int idVenta, String motivo, String ifMatch) {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    VentaRow venta = ventaAccesible(idVenta);
    if ("PAGADO".equals(venta.estado())) {
      throw new ApiException(ErrorCode.SALE_ALREADY_PAID, Map.of("idVenta", String.valueOf(idVenta)));
    }
    if ("CANCELADA".equals(venta.estadoOperacion())) {
      return new Resultado(200, ver(idVenta));
    }
    if (motivo == null || motivo.isBlank() || motivo.length() > 300) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("motivo", "obligatorio entre 1 y 300 caracteres"));
    }
    if ("LEGACY".equals(venta.origen()) && !movimientoConciliado(idVenta)) {
      throw new ApiException(ErrorCode.LEGACY_MOVEMENT_UNVERIFIED, Map.of("idVenta", String.valueOf(idVenta)));
    }
    if (!"PENDIENTE".equals(venta.estadoOperacion())
        && !"CONFIRMADA".equals(venta.estadoOperacion())
        && !"CANCELACION_PENDIENTE".equals(venta.estadoOperacion())) {
      throw new ApiException(ErrorCode.DEBIT_CANCELLED, Map.of("idVenta", String.valueOf(idVenta)));
    }
    if ("CANCELACION_PENDIENTE".equals(venta.estadoOperacion())) {
      return new Resultado(202, ver(idVenta));
    }
    long version = parseIfMatch(ifMatch);
    if (version != venta.version()) {
      throw new ApiException(ErrorCode.PRECONDITION_FAILED, Map.of("If-Match", "versión distinta a la vigente"));
    }
    OperacionRow abierta = repo.operacionAbierta(idVenta).orElse(null);
    if (abierta != null && "ANULACION".equals(abierta.tipo())) {
      return new Resultado(202, ver(idVenta));
    }

    UUID operationId = UUID.randomUUID();
    String request = "{\"motivo\":\"" + escapar(motivo) + "\"}";
    tx.executeWithoutResult(
        status -> {
          repo.terminarOperacionesAbiertas(idVenta, operationId);
          repo.insertarOperacion(
              operationId,
              idVenta,
              "ANULACION",
              "PREPARANDO",
              request,
              currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
              rolActual());
          repo.actualizarOperacionActual(idVenta, operationId);
        });

    try {
      reception.admitir(
          venta.idRecepcion(),
          new OperacionAdmisionRequest(
              operationId,
              idVenta,
              venta.idHotel(),
              "ANULACION",
              currentUser.subjectId() == null ? 0 : currentUser.subjectId(),
              rolActual()));
    } catch (FeignException ex) {
      tx.executeWithoutResult(
          status -> repo.terminarOperacionSiCoincide(operationId, idVenta));
      if (ex.status() == 409) {
        throw new ApiException(ErrorCode.STAY_NOT_ACTIVE, Map.of("idRecepcion", String.valueOf(venta.idRecepcion())));
      }
      if (ex.status() == 403) {
        throw new ApiException(ErrorCode.FORBIDDEN);
      }
      if (ex.status() == 404) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idRecepcion", String.valueOf(venta.idRecepcion())));
      }
      // Admisión en recuperación: la operación sigue PREPARANDO y el job la reintenta.
      return new Resultado(202, ver(idVenta));
    }

    boolean cancelada =
        Boolean.TRUE.equals(
            tx.execute(
                status -> {
                  repo.marcarOperacionAdmitida(operationId);
                  if (!repo.solicitarCancelacion(idVenta, motivo)) {
                    repo.terminarOperacionSiCoincide(operationId, idVenta);
                    return false;
                  }
                  VentaRow actual = repo.venta(idVenta).orElseThrow();
                  outbox.emitirRestituir(
                      actual.id(),
                      actual.idRecepcion(),
                      motivo,
                      actual.idHotel(),
                      actual.version());
                  return true;
                }));
    if (!cancelada) {
      VentaView actual = ver(idVenta);
      return new Resultado(
          "CANCELADA".equals(actual.estadoOperacion()) ? 200 : 202, actual);
    }
    return new Resultado(202, ver(idVenta));
  }

  private boolean movimientoConciliado(int idVenta) {
    try {
      ApiResponse<pe.hotel.sales.domain.dto.MovimientoResumen> movimiento = inventory.movimiento(idVenta);
      return movimiento != null && movimiento.data() != null;
    } catch (FeignException ex) {
      if (ex.status() == 404) {
        return false;
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  // --------------------------------------------------------------- lecturas

  public Pagina<VentaView> listarVentas(
      int idHotel, String estado, String estadoOperacion, int page, int size) {
    if (!currentUser.isAdmin() && !currentUser.isEmpleado()) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (!currentUser.puedeAccederASede(idHotel)) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("idHotel", String.valueOf(idHotel)));
    }
    List<VentaView> data =
        repo.ventas(idHotel, estado, estadoOperacion, page, size).stream().map(this::toView).toList();
    return new Pagina<>(data, repo.contarVentas(idHotel, estado, estadoOperacion));
  }

  public Pagina<VentaView> ventasPorRecepcion(int idRecepcion, int page, int size) {
    RecepcionResumen estadia = recepcion(idRecepcion);
    requireAccesoRecepcion(estadia);
    List<VentaView> data =
        repo.ventasPorRecepcion(idRecepcion, page, size).stream().map(this::toView).toList();
    return new Pagina<>(data, repo.contarVentasPorRecepcion(idRecepcion));
  }

  public VentaView buscar(int idVenta) {
    return toView(ventaAccesible(idVenta));
  }

  public OperacionVentaView operacion(UUID operationId) {
    OperacionRow operacion =
        repo.operacion(operationId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    VentaRow venta = ventaAccesible(operacion.idVenta());
    return new OperacionVentaView(
        operacion.operationId(),
        operacion.idVenta(),
        operacion.tipo(),
        operacion.estado(),
        venta.estadoOperacion());
  }

  // ---------------------------------------------------------------- cierre

  public ResumenCierre resumenCierre(int idRecepcion, int idHotel) {
    SalesRepository.ResumenRow resumen = repo.resumen(idRecepcion, idHotel);
    boolean abiertas = repo.hayOperacionAbierta(idRecepcion, idHotel);
    if (abiertas) {
      throw new ApiException(
          ErrorCode.OPERATIONS_IN_PROGRESS, Map.of("idRecepcion", String.valueOf(idRecepcion)));
    }
    return new ResumenCierre(
        idRecepcion,
        idHotel,
        resumen.totalPendiente(),
        resumen.totalPagado(),
        resumen.pendientes(),
        false);
  }

  public Receipt confirmarCierre(ConfirmacionCierre body, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("Idempotency-Key", "obligatoria"));
    }
    if (!body.closureId().toString().equals(idempotencyKey.trim())) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, Map.of("Idempotency-Key", "debe ser el closureId del cuerpo"));
    }
    if (!MEDIOS.contains(body.medioPago())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("medioPago", "EFECTIVO, TARJETA u OTRO"));
    }
    return tx.execute(
        status -> {
          repo.bloquearRecepcion(body.idRecepcion());
          CierreRow existente = repo.cierre(body.closureId()).orElse(null);
          if (existente != null) {
            return leerReceipt(existente);
          }
          CierreRow otro = repo.cierrePorRecepcion(body.idRecepcion()).orElse(null);
          if (otro != null) {
            throw new ApiException(
                ErrorCode.CLOSURE_IN_PROGRESS,
                Map.of("idRecepcion", String.valueOf(body.idRecepcion())));
          }
          SalesRepository.ResumenRow resumen = repo.resumen(body.idRecepcion(), body.idHotel());
          if (repo.hayOperacionAbierta(body.idRecepcion(), body.idHotel())) {
            throw new ApiException(
                ErrorCode.OPERATIONS_IN_PROGRESS,
                Map.of("idRecepcion", String.valueOf(body.idRecepcion())));
          }
          if (resumen.totalPendiente().compareTo(body.expectedPendingTotal()) != 0) {
            throw new ApiException(
                ErrorCode.AMOUNT_MISMATCH,
                Map.of("expectedPendingTotal", body.expectedPendingTotal().toPlainString()));
          }
          BigDecimal pagadoAhora = resumen.totalPendiente();
          BigDecimal totalConsumos = resumen.totalPagado().add(pagadoAhora);
          List<Integer> pendientes = resumen.pendientes();
          repo.pagarPendientesDeCierre(
              body.idRecepcion(), body.idHotel(), body.medioPago(), body.closureId());
          for (Integer id : pendientes) {
            VentaRow venta = repo.venta(id).orElseThrow();
            outbox.emitirVenta(venta, repo.detalles(id));
          }
          Receipt receipt =
              new Receipt(
                  body.closureId(),
                  body.idRecepcion(),
                  body.idHotel(),
                  resumen.totalPagado(),
                  pagadoAhora,
                  totalConsumos,
                  body.medioPago(),
                  pendientes,
                  Instant.now());
          String receiptJson;
          try {
            receiptJson = mapper.writeValueAsString(receipt);
          } catch (Exception ex) {
            throw new IllegalStateException("no se pudo serializar el receipt", ex);
          }
          repo.insertarCierre(
              body.closureId(),
              body.idRecepcion(),
              body.idHotel(),
              hash(body.closureId() + ":" + body.idRecepcion() + ":" + body.expectedPendingTotal()),
              pagadoAhora,
              resumen.totalPagado(),
              body.medioPago(),
              receiptJson);
          return receipt;
        });
  }

  private Receipt leerReceipt(CierreRow fila) {
    try {
      return mapper.readValue(fila.receipt(), Receipt.class);
    } catch (Exception ex) {
      throw new IllegalStateException("receipt almacenado ilegible", ex);
    }
  }

  // ------------------------------------------------------------- auxiliares

  private void validarAlta(VentaAlta body) {
    String estado = body.estado() == null ? "PENDIENTE" : body.estado();
    if (!ESTADOS_VENTA.contains(estado)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("estado", "PENDIENTE o PAGADO"));
    }
    if (currentUser.isCliente() && "PAGADO".equals(estado)) {
      throw new ApiException(ErrorCode.FORBIDDEN, Map.of("estado", "solo A/E registran pago manual"));
    }
    if ("PAGADO".equals(estado)) {
      if (body.medioPago() == null || body.medioPago().isBlank()) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("medioPago", "obligatorio si estado=PAGADO"));
      }
      if (!MEDIOS.contains(body.medioPago())) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("medioPago", "EFECTIVO, TARJETA u OTRO"));
      }
    } else if (body.medioPago() != null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("medioPago", "solo con estado=PAGADO"));
    }
    Set<Integer> vistos = new HashSet<>();
    for (VentaDetalleAlta detalle : body.detalles()) {
      if (!vistos.add(detalle.idProducto())) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("detalles", "productos distintos"));
      }
    }
  }

  private RecepcionResumen recepcion(int idRecepcion) {
    try {
      ApiResponse<RecepcionResumen> respuesta = reception.recepcion(idRecepcion);
      if (respuesta == null || respuesta.data() == null) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idRecepcion", String.valueOf(idRecepcion)));
      }
      return respuesta.data();
    } catch (FeignException ex) {
      if (ex.status() == 404) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("idRecepcion", String.valueOf(idRecepcion)));
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private ProductoStock producto(int idProducto, int idHotel) {
    try {
      ApiResponse<ProductoStock> respuesta = inventory.producto(idProducto, idHotel);
      if (respuesta == null || respuesta.data() == null) {
        throw new ApiException(ErrorCode.PRODUCT_INACTIVE, Map.of("idProducto", String.valueOf(idProducto)));
      }
      return respuesta.data();
    } catch (FeignException ex) {
      if (ex.status() == 404) {
        throw new ApiException(ErrorCode.PRODUCT_INACTIVE, Map.of("idProducto", String.valueOf(idProducto)));
      }
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
  }

  private void requireAccesoRecepcion(RecepcionResumen estadia) {
    if (currentUser.isAdmin()) {
      return;
    }
    if (currentUser.isEmpleado()) {
      if (!currentUser.puedeAccederASede(estadia.idHotel())) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
      }
      return;
    }
    if (currentUser.isCliente()) {
      Integer subject = currentUser.subjectId();
      if (subject == null || !subject.equals(estadia.idCliente())) {
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
      }
      return;
    }
    throw new ApiException(ErrorCode.FORBIDDEN);
  }

  private VentaRow ventaAccesible(int idVenta) {
    VentaRow venta =
        repo.venta(idVenta)
            .orElseThrow(
                () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(idVenta))));
    if (currentUser.isAdmin()) {
      return venta;
    }
    if (currentUser.isEmpleado() && currentUser.puedeAccederASede(venta.idHotel())) {
      return venta;
    }
    if (currentUser.isCliente() && currentUser.subjectId() != null
        && currentUser.subjectId().equals(venta.idCliente())) {
      return venta;
    }
    throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("id", String.valueOf(idVenta)));
  }

  private VentaView ver(int idVenta) {
    return toView(repo.venta(idVenta).orElseThrow());
  }

  private VentaView toView(VentaRow venta) {
    List<VentaDetalleView> detalles =
        repo.detalles(venta.id()).stream()
            .map(
                d ->
                    new VentaDetalleView(
                        d.id(),
                        d.idProducto(),
                        d.nombreProducto(),
                        d.cantidad(),
                        d.precioUnitario(),
                        d.subTotal()))
            .toList();
    return new VentaView(
        venta.id(),
        venta.idRecepcion(),
        venta.idCliente(),
        venta.idHotel(),
        venta.total(),
        venta.estado(),
        venta.estadoOperacion(),
        venta.currentOperationId(),
        venta.medioPago(),
        venta.version(),
        venta.createdAt(),
        venta.paidAt(),
        detalles);
  }

  private String rolActual() {
    if (currentUser.isAdmin()) {
      return "ADMIN";
    }
    if (currentUser.isEmpleado()) {
      return "EMPLEADO";
    }
    if (currentUser.isCliente()) {
      return "CLIENTE";
    }
    return "DESCONOCIDO";
  }

  private String itemsJson(List<VentaDetalleAlta> detalles) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (VentaDetalleAlta detalle : detalles) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("idProducto", detalle.idProducto());
      item.put("cantidad", detalle.cantidad());
      items.add(item);
    }
    try {
      return mapper.writeValueAsString(items);
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo serializar request_items", ex);
    }
  }

  private long parseIfMatch(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, Map.of("If-Match", "ausente"));
    }
    String valor = ifMatch.trim();
    if (valor.startsWith("W/")) {
      valor = valor.substring(2);
    }
    valor = valor.replace("\"", "");
    try {
      return Long.parseLong(valor);
    } catch (NumberFormatException ex) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, Map.of("If-Match", "formato inválido"));
    }
  }

  private ErrorCode codigoDeError(FeignException ex) {
    try {
      JsonNode node = mapper.readTree(ex.contentUTF8());
      String code = node.path("error").path("code").asText("");
      return switch (code) {
        case "STOCK_INSUFFICIENT" -> ErrorCode.STOCK_INSUFFICIENT;
        case "PRODUCT_INACTIVE" -> ErrorCode.PRODUCT_INACTIVE;
        case "DEBIT_CANCELLED" -> ErrorCode.DEBIT_CANCELLED;
        case "IDEMPOTENCY_CONFLICT" -> ErrorCode.IDEMPOTENCY_CONFLICT;
        default -> ErrorCode.STOCK_INSUFFICIENT;
      };
    } catch (Exception ignored) {
      return ErrorCode.STOCK_INSUFFICIENT;
    }
  }

  private Map<String, String> camposDeError(FeignException ex) {
    try {
      JsonNode node = mapper.readTree(ex.contentUTF8());
      JsonNode fields = node.path("error").path("fields");
      if (fields.isObject()) {
        Map<String, String> out = new LinkedHashMap<>();
        fields
            .fields()
            .forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue().asText()));
        return out;
      }
    } catch (Exception ignored) {
      // Sin cuerpo útil: se devuelve un campo genérico.
    }
    return Map.of("idVenta", "el débito no se aplicó");
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 no disponible", ex);
    }
  }

  private static String escapar(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
