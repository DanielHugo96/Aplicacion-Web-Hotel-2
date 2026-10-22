package pe.hotel.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import pe.hotel.inventory.domain.dto.Debito;
import pe.hotel.inventory.domain.dto.DebitoItem;
import pe.hotel.inventory.domain.service.OutboxService;
import pe.hotel.inventory.domain.service.StockService;
import pe.hotel.inventory.messaging.Destination;
import pe.hotel.inventory.messaging.MessageValidator;
import pe.hotel.inventory.messaging.OutboxMessage;
import pe.hotel.inventory.messaging.OutboxStore;

/**
 * Pruebas de integración contra {@code inventory_db} real (Compose). Aplica las migraciones con el
 * usuario migrador y valida las funciones SQL de stock, los seeds, el outbox y los JSON Schemas como
 * el usuario runtime.
 *
 * <p>Se nombra {@code *IT} para que surefire no las ejecute en {@code mvn verify} (que no garantiza
 * base de datos); se lanzan a mano contra el Compose levantado, pasando las credenciales por
 * entorno:
 *
 * <pre>
 *   SPRING_DATASOURCE_USERNAME=inventory_app \
 *   SPRING_DATASOURCE_PASSWORD=&lt;POSTGRES_INVENTORY_APP_PASSWORD&gt; \
 *   SPRING_FLYWAY_USER=inventory_migrator \
 *   SPRING_FLYWAY_PASSWORD=&lt;POSTGRES_INVENTORY_MIGRATOR_PASSWORD&gt; \
 *   POSTGRES_PASSWORD=&lt;POSTGRES_PASSWORD&gt; \
 *   bash scripts/mvn.sh -pl inventory-service test -Dtest=StockDbIT -DfailIfNoTests=false
 * </pre>
 */
class StockDbIT {

  private static final String URL =
      System.getenv()
          .getOrDefault(
              "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:55432/inventory_db");
  private static final String USER =
      System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "inventory_app");
  private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
  private static final String FLYWAY_USER =
      System.getenv().getOrDefault("SPRING_FLYWAY_USER", "inventory_migrator");
  private static final String FLYWAY_PASSWORD = System.getenv("SPRING_FLYWAY_PASSWORD");
  private static final String POSTGRES_PASSWORD = System.getenv("POSTGRES_PASSWORD");

  private static final Integer HOTEL = 1;
  private static final Integer PRODUCTO_AGUA = 1;
  private static final Integer PRODUCTO_ASEO = 3;

  private static JdbcTemplate jdbc;
  private static JdbcTemplate migrador;
  private static OutboxStore outboxStore;

  @BeforeAll
  static void aplicarMigraciones() {
    if (PASSWORD == null || FLYWAY_PASSWORD == null || POSTGRES_PASSWORD == null) {
      throw new IllegalStateException(
          "Defina SPRING_DATASOURCE_PASSWORD, SPRING_FLYWAY_PASSWORD y POSTGRES_PASSWORD antes de ejecutar este IT");
    }
    // Recrear la base desde cero para que las migraciones siempre se apliquen con el cuerpo actual
    // de V3 (iterado durante el desarrollo) y reaplicar los privilegios que deja el init de
    // infra/postgres/init/01-bases-y-roles.sh para el usuario runtime.
    JdbcTemplate postgres =
        dataSource("jdbc:postgresql://localhost:55432/postgres", "postgres", POSTGRES_PASSWORD);
    postgres.execute(
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'inventory_db' AND pid <> pg_backend_pid()");
    postgres.update("DROP DATABASE IF EXISTS inventory_db");
    postgres.update("CREATE DATABASE inventory_db OWNER inventory_migrator ENCODING 'UTF8'");
    postgres.update("REVOKE CONNECT ON DATABASE inventory_db FROM PUBLIC");
    postgres.update(
        "GRANT CONNECT ON DATABASE inventory_db TO inventory_app, inventory_migrator");

    JdbcTemplate creador =
        dataSource("jdbc:postgresql://localhost:55432/inventory_db", "postgres", POSTGRES_PASSWORD);
    creador.update("REVOKE ALL ON SCHEMA public FROM PUBLIC");
    creador.update("GRANT USAGE, CREATE ON SCHEMA public TO inventory_migrator");
    creador.update("GRANT USAGE ON SCHEMA public TO inventory_app");
    creador.update(
        "ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA public"
            + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO inventory_app");
    creador.update(
        "ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA public"
            + " GRANT USAGE, SELECT ON SEQUENCES TO inventory_app");
    creador.update(
        "ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA public"
            + " GRANT EXECUTE ON FUNCTIONS TO inventory_app");

    Flyway flyway =
        Flyway.configure()
            .dataSource(URL, FLYWAY_USER, FLYWAY_PASSWORD)
            .locations("classpath:db/migration")
            .load();
    flyway.migrate();

    jdbc = dataSource(USER, PASSWORD);
    migrador = dataSource(FLYWAY_USER, FLYWAY_PASSWORD);
    outboxStore = new OutboxStore(jdbc);
  }

  private static JdbcTemplate dataSource(String url, String user, String password) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(url);
    ds.setUsername(user);
    ds.setPassword(password);
    return new JdbcTemplate(ds);
  }

  private static JdbcTemplate dataSource(String user, String password) {
    return dataSource(URL, user, password);
  }

  private static StockService stockService() {
    return new StockService(jdbc, new OutboxService(outboxStore, new ObjectMapper()));
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM movimiento_detalle");
    jdbc.update("DELETE FROM movimiento_venta");
    jdbc.update("DELETE FROM ajuste_stock");
    jdbc.update("DELETE FROM outbox");
    jdbc.update("DELETE FROM inbox");
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
      if (i > 0) {
        sb.append(",");
      }
      DebitoItem it = items.get(i);
      sb.append("{\"idProducto\":")
          .append(it.idProducto())
          .append(",\"cantidad\":")
          .append(it.cantidad())
          .append("}");
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

  private Long filasOutbox(String type) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM outbox WHERE type = ?", Long.class, type);
  }

  // ---------------------------------------------------------------- seeds

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
  void seedsReejecutadosNoDuplicanNiReponenVentas() {
    // Simula una venta ya realizada y reejecuta los seeds como el migrador (igual que Flyway).
    stockService().descontar(debito(9001, List.of(new DebitoItem(PRODUCTO_AGUA, 7))));
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(13);

    for (String sentencia : sentenciasDeSeeds()) {
      migrador.update(sentencia);
    }

    assertThat(jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class)).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM producto_sede", Integer.class)).isEqualTo(9);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(13);
  }

  private static List<String> sentenciasDeSeeds() {
    return sentenciasDeMigracion("db/migration/V4__seeds_inventario.sql");
  }

  private static List<String> sentenciasDeMigracion(String ruta) {
    try {
      String sql =
          new String(
              new ClassPathResource(ruta).getInputStream().readAllBytes(),
              StandardCharsets.UTF_8);
      return java.util.Arrays.stream(sql.split(";"))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .filter(s -> s.lines().anyMatch(l -> !l.trim().startsWith("--") && !l.isBlank()))
          .toList();
    } catch (Exception ex) {
      throw new IllegalStateException("no se pudo leer la migración " + ruta, ex);
    }
  }

  /**
   * Imágenes de los productos sembrados: rutas propias en {@code /assets/productos/} y ningún resto
   * de {@code /assets/demo/} (docs/especificacion/microservicios/inventory/base-de-datos.md#seeds).
   */
  @Test
  void seedsDeProductosApuntanAAssetsProductosPropios() {
    List<java.util.Map<String, Object>> filas =
        jdbc.queryForList("SELECT id, imagen_url FROM producto ORDER BY id");

    assertThat(filas).hasSize(3);
    assertThat(filas)
        .extracting(f -> String.valueOf(f.get("imagen_url")))
        .containsExactly(
            "/assets/productos/agua-500ml.png",
            "/assets/productos/snack.png",
            "/assets/productos/kit-aseo.png");
    assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM producto WHERE imagen_url LIKE '/assets/demo/%'", Integer.class))
        .isZero();
  }

  /** La corrección de V6 no pisa una imagen editada desde el panel: solo reconoce el valor anterior. */
  @Test
  void laCorreccionDeImagenesNoPisaUnaImagenEditada() {
    String editada = "/assets/productos/agua-500ml-editorial.png";
    migrador.update("UPDATE producto SET imagen_url = ? WHERE id = 1", editada);

    for (String sentencia : sentenciasDeMigracion("db/migration/V6__seeds_productos_imagenes.sql")) {
      migrador.update(sentencia);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT imagen_url FROM producto WHERE id = 1", String.class))
        .isEqualTo(editada);
    assertThat(
            jdbc.queryForObject(
                "SELECT imagen_url FROM producto WHERE id = 2", String.class))
        .isEqualTo("/assets/productos/snack.png");

    migrador.update(
        "UPDATE producto SET imagen_url = '/assets/productos/agua-500ml.png' WHERE id = 1");
  }

  // ------------------------------------------------ migración y checks

  @Test
  void migracionDejaTablasTecnicasYChecksDeIntegridad() {
    for (String tabla : List.of("api_idempotency", "outbox", "inbox")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                  Integer.class,
                  tabla))
          .as("tabla técnica %s", tabla)
          .isEqualTo(1);
    }

    // sku duplicado (UNIQUE producto.sku).
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO producto (sku, nombre) VALUES ('AGUA-500', 'Repetido')"))
        .isInstanceOf(DataAccessException.class);
    // precio no positivo.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO producto_sede (id_producto, id_hotel, precio, cantidad, umbral_bajo) VALUES (1, 9, 0, 1, 1)"))
        .isInstanceOf(DataAccessException.class);
    // cantidad negativa.
    assertThatThrownBy(
            () -> jdbc.update("UPDATE producto_sede SET cantidad = -1 WHERE id_producto = 1"))
        .isInstanceOf(DataAccessException.class);
    // delta 0 en el ajuste.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO ajuste_stock (id, id_producto, id_hotel, delta, motivo, actor_id, saldo_resultante) VALUES (?, 1, 1, 0, 'delta cero', 1, 20)",
                    UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class);
    // id_venta duplicado (PK movimiento_venta).
    jdbc.update(
        "INSERT INTO movimiento_venta (id_venta, id_hotel, request_hash, estado, version) VALUES (8001, 1, NULL, 'RESTITUIDO', 1)");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO movimiento_venta (id_venta, id_hotel, request_hash, estado, version) VALUES (8001, 1, NULL, 'RESTITUIDO', 1)"))
        .isInstanceOf(DataAccessException.class);
  }

  // ---------------------------------------------------- descuento (STK01/STK02)

  @Test
  void stk01DosDebitosDe15ConcurrenteSobreStock20UnoGanaStockFinal5() throws Exception {
    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 15));
    StockService stockService = stockService();

    CountDownLatch listos = new CountDownLatch(2);
    CountDownLatch salida = new CountDownLatch(1);
    AtomicReference<StockService.DescontarResultado> r1 = new AtomicReference<>();
    AtomicReference<StockService.DescontarResultado> r2 = new AtomicReference<>();

    Thread t1 =
        new Thread(
            () -> {
              listos.countDown();
              try {
                salida.await();
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
              r1.set(stockService.descontar(debito(1001, items)));
            });
    Thread t2 =
        new Thread(
            () -> {
              listos.countDown();
              try {
                salida.await();
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
              r2.set(stockService.descontar(debito(1002, items)));
            });
    t1.start();
    t2.start();
    listos.await();
    salida.countDown();
    t1.join();
    t2.join();

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
        jdbc.queryForObject(
            "SELECT count(*) FROM movimiento_venta WHERE estado='DESCONTADO'", Long.class);
    assertThat(descontados).isEqualTo(1L);
  }

  @Test
  void stk02VentaMultitemConLineaInsuficienteRechazaSinDescuentoParcial() {
    StockService stockService = stockService();
    List<DebitoItem> items =
        List.of(new DebitoItem(PRODUCTO_AGUA, 2), new DebitoItem(PRODUCTO_ASEO, 10));
    StockService.DescontarResultado res = stockService.descontar(debito(2001, items));

    assertThat(res.estado()).isEqualTo(StockService.Estado.RECHAZADO);
    assertThat(res.errorCode()).isEqualTo(pe.hotel.inventory.platform.ErrorCode.STOCK_INSUFFICIENT);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
    assertThat(stock(PRODUCTO_ASEO)).isEqualTo(5);
    Long movimientos =
        jdbc.queryForObject("SELECT count(*) FROM movimiento_venta WHERE id_venta = 2001", Long.class);
    assertThat(movimientos).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movimiento_detalle WHERE id_venta = 2001", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM movimiento_venta WHERE id_venta = 2001", String.class))
        .isEqualTo("RECHAZADO");
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
    // El reintento idempotente no republica eventos.
    assertThat(filasOutbox("MovimientoStockResuelto")).isEqualTo(1L);
  }

  @Test
  void otroHashParaElMismoIdVentaEsConflictoDeIdempotencia() {
    StockService stockService = stockService();
    stockService.descontar(debito(6001, List.of(new DebitoItem(PRODUCTO_AGUA, 1))));
    StockService.DescontarResultado otro =
        stockService.descontar(debito(6001, List.of(new DebitoItem(PRODUCTO_AGUA, 2))));
    assertThat(otro.estado()).isEqualTo(StockService.Estado.IDEMPOTENCY_CONFLICT);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(19);
  }

  // ---------------------------------------------------- restitución (STK04)

  @Test
  void stk04RestitucionAntesDelDescuentoCreaLapidaYBloqueaDebitoTardio() {
    StockService stockService = stockService();
    stockService.restituir(3001, HOTEL, "Anulación antes de débito");
    java.util.Map<String, Object> movimiento =
        jdbc.queryForMap("SELECT estado, resultado, request_hash FROM movimiento_venta WHERE id_venta = 3001");
    assertThat(movimiento.get("estado")).isEqualTo("RESTITUIDO");
    assertThat(movimiento.get("request_hash")).isNull();

    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 2));
    StockService.DescontarResultado res = stockService.descontar(debito(3001, items));
    assertThat(res.estado()).isEqualTo(StockService.Estado.RECHAZADO);
    assertThat(res.errorCode()).isEqualTo(pe.hotel.inventory.platform.ErrorCode.DEBIT_CANCELLED);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM movimiento_venta WHERE id_venta = 3001", String.class))
        .isEqualTo("RESTITUIDO");
  }

  @Test
  void dobleRestitucionNoDuplicaStockYConservaElResultadoDescontado() {
    StockService stockService = stockService();
    List<DebitoItem> items = List.of(new DebitoItem(PRODUCTO_AGUA, 3));
    StockService.DescontarResultado res = stockService.descontar(debito(4001, items));
    assertThat(res.estado()).isEqualTo(StockService.Estado.DESCONTADO);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(17);

    stockService.restituir(4001, HOTEL, "Anulación");
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);
    stockService.restituir(4001, HOTEL, "Anulación duplicada");
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(20);

    java.util.Map<String, Object> movimiento =
        jdbc.queryForMap("SELECT estado, resultado FROM movimiento_venta WHERE id_venta = 4001");
    assertThat(movimiento.get("estado")).isEqualTo("RESTITUIDO");
    // El resultado DESCONTADO se conserva para auditoría y se le añade restituidoAt.
    assertThat(movimiento.get("resultado").toString()).contains("DESCONTADO").contains("restituidoAt");
    // Una mutación real por fase: débito (DESCONTADO) y una única restitución publicada.
    assertThat(filasOutbox("MovimientoStockResuelto")).isEqualTo(2L);
  }

  @Test
  void restitucionConSedeDistintaNoMueveStockAjeno() {
    StockService stockService = stockService();
    stockService.descontar(debito(7001, List.of(new DebitoItem(PRODUCTO_AGUA, 2))));
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(18);
    assertThatThrownBy(() -> stockService.restituir(7001, 2, "Sede equivocada"))
        .isInstanceOf(pe.hotel.inventory.domain.service.SedeMismatchException.class);
    assertThat(stock(PRODUCTO_AGUA)).isEqualTo(18);
    assertThat(
            jdbc.queryForObject(
                "SELECT estado FROM movimiento_venta WHERE id_venta = 7001", String.class))
        .isEqualTo("DESCONTADO");
  }

  // ------------------------------------------------------------- ajustes

  @Test
  void ajusteRegistraFilaYBloqueaSaldoNegativo() {
    Integer saldo =
        jdbc.queryForObject(
            "SELECT fn_stock_ajustar(CAST(? AS uuid), 3, 1, 4, 'Reposición', 1)",
            Integer.class,
            UUID.randomUUID());
    assertThat(saldo).isEqualTo(9);
    assertThat(stock(PRODUCTO_ASEO)).isEqualTo(9);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ajuste_stock", Integer.class)).isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbc.queryForObject(
                    "SELECT fn_stock_ajustar(CAST(? AS uuid), 3, 1, -10, 'Dejaría negativo', 1)",
                    Integer.class,
                    UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class);
    assertThat(stock(PRODUCTO_ASEO)).isEqualTo(9);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ajuste_stock", Integer.class)).isEqualTo(1);
  }

  // -------------------------------------------------------------- outbox

  @Test
  void cadaMutacionDejaUnaFilaDeOutboxYSuPayloadValidaContraElSchema() {
    StockService stockService = stockService();
    stockService.descontar(debito(11001, List.of(new DebitoItem(PRODUCTO_AGUA, 2))));
    assertThat(filasOutbox("MovimientoStockResuelto")).isEqualTo(1L);
    assertThat(filasOutbox("ProductoStockSnapshot")).isEqualTo(1L);

    stockService.restituir(11001, HOTEL, "Anulación");
    assertThat(filasOutbox("MovimientoStockResuelto")).isEqualTo(2L);
    // Dos mutaciones reales sobre la misma sede/producto: una por el débito y otra por la restitución.
    assertThat(filasOutbox("ProductoStockSnapshot")).isEqualTo(2L);

    MessageValidator validator = new MessageValidator("schemas");
    List<OutboxMessage> filas =
        jdbc.query(
            "SELECT id, destination, channel, partition_key, aggregate_id, aggregate_version, type, payload::text AS payload, created_at, attempts, next_attempt_at FROM outbox ORDER BY created_at",
            (rs, rowNum) ->
                new OutboxMessage(
                    rs.getObject("id", UUID.class),
                    Destination.valueOf(rs.getString("destination")),
                    rs.getString("channel"),
                    rs.getString("partition_key"),
                    rs.getString("aggregate_id"),
                    rs.getLong("aggregate_version"),
                    rs.getString("type"),
                    rs.getString("payload"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                    rs.getInt("attempts"),
                    rs.getObject("next_attempt_at", java.time.OffsetDateTime.class).toInstant()));
    assertThat(filas).hasSize(4);
    for (OutboxMessage fila : filas) {
      validator.validate(fila);
      assertThat(fila.destination()).isEqualTo(Destination.KAFKA);
    }
    // Clave compuesta idHotel:idProducto y versión incrementada por sede (débito 2, restitución 3).
    List<String> clavesSnapshot =
        jdbc.queryForList(
            "SELECT partition_key FROM outbox WHERE type = 'ProductoStockSnapshot' ORDER BY aggregate_version",
            String.class);
    assertThat(clavesSnapshot).containsExactly("1:1", "1:1");
    List<Long> versiones =
        jdbc.queryForList(
            "SELECT aggregate_version FROM outbox WHERE type = 'ProductoStockSnapshot' ORDER BY aggregate_version",
            Long.class);
    assertThat(versiones).containsExactly(2L, 3L);
    // La key del movimiento es idVenta serializado.
    assertThat(
            jdbc.queryForList(
                "SELECT partition_key FROM outbox WHERE type = 'MovimientoStockResuelto' ORDER BY created_at",
                String.class))
        .containsExactly("11001", "11001");
  }

  /**
   * El {@code aggregateId} del snapshot es la clave compuesta {@code idHotel:idProducto}
   * (docs/contracts/events/inventory.stock.v1.ProductoStockSnapshot.schema.json) y coincide con el
   * {@code partition_key} de Kafka. Se usa un producto no simétrico con la sede para que una
   * inversión de campos no quede oculta.
   */
  @Test
  void snapshotUsaAggregateIdIdHotelIdProductoEnProductoNoSimetrico() {
    stockService().descontar(debito(12001, List.of(new DebitoItem(PRODUCTO_ASEO, 1))));
    java.util.Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT partition_key, aggregate_id, aggregate_version FROM outbox WHERE type = 'ProductoStockSnapshot'");
    assertThat(fila.get("partition_key")).isEqualTo("1:3");
    assertThat(fila.get("aggregate_id")).isEqualTo("1:3");
    assertThat(((Number) fila.get("aggregate_version")).longValue()).isEqualTo(2L);
  }

  @Test
  void laSecuenciaDeProductoContinuaTrasLosSeedsConIdExplicito() {
    Integer id =
        jdbc.queryForObject(
            "INSERT INTO producto (sku, nombre) VALUES ('SEQ-01', 'Secuencia') RETURNING id",
            Integer.class);
    try {
      assertThat(id).isGreaterThan(3);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM producto WHERE id = ?", Integer.class, id))
          .isEqualTo(1);
    } finally {
      jdbc.update("DELETE FROM producto WHERE sku = 'SEQ-01'");
    }
  }

  @Test
  void eventIdDeBootstrapEsEstableParaLaMismaSedeYProducto() {
    String primera = OutboxService.eventIdBootstrap("producto-sede", "1:1");
    String segunda = OutboxService.eventIdBootstrap("producto-sede", "1:1");
    assertThat(primera).isEqualTo(segunda).isNotEqualTo(OutboxService.eventIdBootstrap("producto-sede", "1:2"));
  }

  @Test
  void sha256DelDebitoEsEstableParaLaMismaTupla() {
    String a = sha256(1, 1, itemsJson(List.of(new DebitoItem(1, 2))));
    String b = sha256(1, 1, itemsJson(List.of(new DebitoItem(1, 2))));
    assertThat(a).hasSize(64).isEqualTo(b);
  }
}
