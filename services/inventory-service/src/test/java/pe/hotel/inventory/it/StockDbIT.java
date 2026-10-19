package pe.hotel.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import pe.hotel.inventory.domain.dto.Debito;
import pe.hotel.inventory.domain.dto.DebitoItem;
import pe.hotel.inventory.domain.service.StockService;

/**
 * Pruebas de integración contra {@code inventory_db} real (Compose). Aplica las migraciones con el
 * usuario migrador y valida las funciones SQL de stock y los seeds como el usuario runtime.
 *
 * <p>Se nombra {@code *IT} para que surefire no las ejecute en {@code mvn verify} (que no garantiza
 * base de datos); se lanzan a mano contra el Compose levantado, pasando las credenciales por
 * entorno:
 *
 * <pre>
 *   SPRING_DATASOURCE_USERNAME=inventory_app \
 *   SPRING_DATASOURCE_PASSWORD=<POSTGRES_INVENTORY_APP_PASSWORD> \
 *   SPRING_FLYWAY_USER=inventory_migrator \
 *   SPRING_FLYWAY_PASSWORD=<POSTGRES_INVENTORY_MIGRATOR_PASSWORD> \
 *   ./mvnw -pl inventory-service test -Dtest=StockDbIT -DfailIfNoTests=false
 * </pre>
 */
class StockDbIT {

  private static final String URL =
      System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:55432/inventory_db");
  private static final String USER =
      System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "inventory_migrator");
  private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
  private static final String FLYWAY_USER =
      System.getenv().getOrDefault("SPRING_FLYWAY_USER", "inventory_migrator");
  private static final String FLYWAY_PASSWORD = System.getenv("SPRING_FLYWAY_PASSWORD");
  private static final String POSTGRES_PASSWORD = System.getenv("POSTGRES_PASSWORD");

  private static final Integer HOTEL = 1;
  private static final Integer PRODUCTO_AGUA = 1;
  private static final Integer PRODUCTO_ASEO = 3;

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void aplicarMigraciones() {
    if (PASSWORD == null || FLYWAY_PASSWORD == null || POSTGRES_PASSWORD == null) {
      throw new IllegalStateException(
          "Defina SPRING_DATASOURCE_PASSWORD, SPRING_FLYWAY_PASSWORD y POSTGRES_PASSWORD antes de ejecutar este IT");
    }
    // Recrear la base desde cero para que las migraciones siempre se apliquen con el cuerpo actual
    // de V3 (iterado durante el desarrollo).
    JdbcTemplate postgres =
        dataSource("jdbc:postgresql://localhost:55432/postgres", "postgres", POSTGRES_PASSWORD);
    System.out.println("DEBUG dropping inventory_db...");
    postgres.execute(
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'inventory_db' AND pid <> pg_backend_pid()");
    postgres.update("DROP DATABASE IF EXISTS inventory_db");
    System.out.println("DEBUG dropped");
    postgres.update("CREATE DATABASE inventory_db OWNER inventory_migrator");
    System.out.println("DEBUG created");

    Flyway flyway =
        Flyway.configure()
            .dataSource(URL, FLYWAY_USER, FLYWAY_PASSWORD)
            .locations("classpath:db/migration")
            .load();
    flyway.migrate();
    System.out.println("DEBUG migrated");
    jdbc = dataSource(USER, PASSWORD);
    List<String> funcs =
        jdbc.queryForList(
            "SELECT proname || '(' || pg_get_function_arguments(oid) || ')' FROM pg_proc WHERE proname = 'fn_stock_descontar'",
            String.class);
    System.out.println("DEBUG DB FUNCTIONS: " + funcs);
    try {
      java.util.Map<String, Object> r =
          jdbc.queryForObject(
              "SELECT fn_stock_descontar(100::integer, 1::integer, '[{\"idProducto\":1,\"cantidad\":2}]'::jsonb, 'abc'::char(64)) AS resultado",
              (rs, rowNum) -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("estado", rs.getString("estado"));
                m.put("resultado", rs.getString("resultado"));
                return m;
              });
      System.out.println("DEBUG CALL resultado = " + r);
    } catch (Exception ex) {
      System.out.println("DEBUG CALL ERROR: " + ex);
      System.out.println("DEBUG CALL ERROR msg: " + ex.getMessage());
      if (ex.getCause() != null) System.out.println("DEBUG CALL ERROR cause: " + ex.getCause());
    }
    jdbc = dataSource(USER, PASSWORD);
  }

  private static JdbcTemplate dataSource(String url, String user, String password) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(url);
    ds.setUsername(user);
    ds.setPassword(password);
    return new JdbcTemplate(ds);
  }

  private static StockService stockService() {
    return new StockService(jdbc, null);
  }

  private static JdbcTemplate dataSource(String user, String password) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(URL);
    ds.setUsername(user);
    ds.setPassword(password);
    return new JdbcTemplate(ds);
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM movimiento_detalle");
    jdbc.update("DELETE FROM movimiento_venta");
    jdbc.update("DELETE FROM ajuste_stock");
    jdbc.update(
        "UPDATE producto_sede SET cantidad = CASE id_producto WHEN 1 THEN 20 WHEN 2 THEN 10 WHEN 3 THEN 5 END, version = 1, estado = true");
  }

  private static String sha256(Integer idVenta, Integer idHotel, String itemsJson) {
    String canonical = idVenta + "|" + idHotel + "|" + itemsJson;
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(d.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private Debito debito(Integer idVenta, List<DebitoItem> items) {
    return new Debito(idVenta, HOTEL, items);
  }

  private String itemsJson(List<DebitoItem> items) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(",");
      DebitoItem it = items.get(i);
      sb.append("{\"idProducto\":").append(it.idProducto()).append(",\"cantidad\":").append(it.cantidad()).append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  private Integer stock(Integer idProducto) {
    return jdbc.queryForObject(
        "SELECT cantidad FROM producto_sede WHERE id_producto = ? AND id_hotel = ?",
        Integer.class,
        idProducto,
        HOTEL);
  }

  @Test
  void seedsTienen9ProductosSedeConValoresEsperados() {
    Integer productos = jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class);
    Integer sedes = jdbc.queryForObject("SELECT count(*) FROM producto_sede", Integer.class);
    assertThat(productos).isEqualTo(3);
    assertThat(sedes).isEqualTo(9);

    java.util.Map<String, Object> p1s1 =
        jdbc.queryForMap(
            "SELECT precio, cantidad, umbral_bajo, estado, version FROM producto_sede WHERE id_producto=1 AND id_hotel=1");
    assertThat(new java.math.BigDecimal(p1s1.get("precio").toString()))
        .isEqualByComparingTo(new java.math.BigDecimal("3.50"));
    assertThat(p1s1.get("cantidad")).isEqualTo(20);
    assertThat(p1s1.get("umbral_bajo")).isEqualTo(5);
    assertThat(p1s1.get("estado")).isEqualTo(true);
    assertThat(((Number) p1s1.get("version")).intValue()).isEqualTo(1);

    java.util.Map<String, Object> p3s1 =
        jdbc.queryForMap(
            "SELECT precio, cantidad FROM producto_sede WHERE id_producto=3 AND id_hotel=1");
    assertThat(new java.math.BigDecimal(p3s1.get("precio").toString()))
        .isEqualByComparingTo(new java.math.BigDecimal("5.00"));
    assertThat(p3s1.get("cantidad")).isEqualTo(5);
  }

  @Test
  void stk01DosDebitosDe15ConcurrenteSobreStock20UnoGanaStockFinal5() {
    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 15));
    StockService stockService = stockService();

    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<StockService.DescontarResultado> r1 = new AtomicReference<>();
    AtomicReference<StockService.DescontarResultado> r2 = new AtomicReference<>();

    Thread t1 =
        new Thread(
            () -> {
              try {
                r1.set(stockService.descontar(debito(1001, items)));
              } finally {
                latch.countDown();
              }
            });
    Thread t2 =
        new Thread(
            () -> {
              try {
                r2.set(stockService.descontar(debito(1002, items)));
              } finally {
                latch.countDown();
              }
            });
    t1.start();
    t2.start();
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    StockService.DescontarResultado g1 = r1.get();
    StockService.DescontarResultado g2 = r2.get();
    boolean unoDescontado =
        (g1.estado() == StockService.Estado.DESCONTADO)
            ^ (g2.estado() == StockService.Estado.DESCONTADO);
    assertThat(unoDescontado).isTrue();
    StockService.DescontarResultado ganador =
        g1.estado() == StockService.Estado.DESCONTADO ? g1 : g2;
    assertThat(ganador.resultado().estado()).isEqualTo("DESCONTADO");
    assertThat(ganador.resultado().total())
        .isEqualByComparingTo(new java.math.BigDecimal("52.50")); // 15 * 3.50

    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(5);
    Long descontados =
        jdbc.queryForObject("SELECT count(*) FROM movimiento_venta WHERE estado='DESCONTADO'", Long.class);
    assertThat(descontados).isEqualTo(1L);
  }

  @Test
  void stk02VentaMultitemConLineaInsuficienteRechazaSinDescuentoParcial() {
    StockService stockService = stockService();
    List<DebitoItem> items =
        List.of(new DebitoItem(PRODUCTO_AGUA, 2), new DebitoItem(PRODUCTO_ASEO, 10));
    StockService.DescontarResultado res = stockService.descontar(debito(2001, items));

    assertThat(res.estado()).isEqualTo(StockService.Estado.RECHAZADO);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
    assertThat(stock(PRODUCTO_ASEO)).isEqualTo(5);
    Long movimientos =
        jdbc.queryForObject("SELECT count(*) FROM movimiento_venta WHERE id_venta = 2001", Long.class);
    assertThat(movimientos).isEqualTo(1L);
  }

  @Test
  void stk04RestitucionAntesDelDescuentoCreaLapidaYBloqueaDebitoTardio() {
    StockService stockService = stockService();
    stockService.restituir(3001, HOTEL, "Anulación antes de débito");
    java.util.Map<String, Object> movimiento =
        jdbc.queryForMap(
            "SELECT estado, resultado FROM movimiento_venta WHERE id_venta = 3001");
    assertThat(movimiento.get("estado")).isEqualTo("RESTITUIDO");

    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 2));
    StockService.DescontarResultado res = stockService.descontar(debito(3001, items));
    assertThat(res.estado()).isEqualTo(StockService.Estado.RECHAZADO);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
  }

  @Test
  void dobleRestitucionNoDuplicaStock() {
    StockService stockService = stockService();
    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 3));
    StockService.DescontarResultado res = stockService.descontar(debito(4001, items));
    assertThat(res.estado()).isEqualTo(StockService.Estado.DESCONTADO);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(17);

    stockService.restituir(4001, HOTEL, "Anulación");
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
    stockService.restituir(4001, HOTEL, "Anulación duplicada");
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
  }

  @Test
  void idempotenciaMismoIdVentaYHashDevuelveMismoResultado() {
    StockService stockService = stockService();
    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 4));
    StockService.DescontarResultado r1 = stockService.descontar(debito(5001, items));
    assertThat(r1.estado()).isEqualTo(StockService.Estado.DESCONTADO);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(16);

    StockService.DescontarResultado r2 = stockService.descontar(debito(5001, items));
    assertThat(r2.estado()).isEqualTo(StockService.Estado.DESCONTADO);
    assertThat(r2.resultado().total()).isEqualByComparingTo(r1.resultado().total());
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(16);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movimiento_venta WHERE id_venta = 5001", Long.class))
        .isEqualTo(1L);
  }
}
