package pe.hotel.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.hotel.reporting.config.ReportingProperties;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;
import pe.hotel.reporting.platform.Meta;
import pe.hotel.reporting.projection.ProjectionStore;
import pe.hotel.reporting.report.security.CurrentUser;
import pe.hotel.reporting.report.security.SedeAccess;

/**
 * Guardia de consistencia eventual y aislamiento por sede, sin base de datos: 503 mientras el
 * bootstrap no está LISTO, 503 cuando la proyección no es utilizable y 404 para sede ajena.
 */
class ReportingGuardTests {

  private ProjectionStore store;
  private ConnectivityState connectivity;
  private ReportingProperties properties;
  private ReportingMetaService metaService;

  @BeforeEach
  void setUp() {
    store = mock(ProjectionStore.class);
    connectivity = new ConnectivityState();
    properties = new ReportingProperties();
    properties.setDegradedTimeoutMinutes(5);
    metaService = new ReportingMetaService(store, connectivity, properties);
  }

  @Test
  void sinBootstrapListoRespondeNotReady() {
    when(store.isBootstrapReady()).thenReturn(false);
    assertThatThrownBy(() -> metaService.meta(null, null, null))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.REPORTING_NOT_READY);
  }

  @Test
  void sinConexionActivaFueraDelUmbralRespondeUnavailable() {
    when(store.isBootstrapReady()).thenReturn(true);
    // Última conexión activa hace más de `degraded-timeout-minutes` (5): la proyección ya no es
    // utilizable y los reportes responden 503 REPORTING_UNAVAILABLE.
    retrocederUltimaConexion(6);

    assertThat(connectivity.usable(properties.getDegradedTimeoutMinutes())).isFalse();
    assertThatThrownBy(() -> metaService.meta(null, null, null))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.REPORTING_UNAVAILABLE);
  }

  @Test
  void dentroDelUmbralLaProyeccionSigueUtilizable() {
    when(store.isBootstrapReady()).thenReturn(true);
    when(store.hasDegradedCheckpoint()).thenReturn(false);
    retrocederUltimaConexion(3);

    assertThat(connectivity.usable(properties.getDegradedTimeoutMinutes())).isTrue();
    Meta meta = metaService.meta(null, null, null);
    assertThat(meta.degraded()).isTrue();
  }

  @Test
  void metaDeReporteDeclaraConsistenciaEventualYBootstrapCompleto() {
    when(store.isBootstrapReady()).thenReturn(true);
    when(store.minUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-10-20T13:20:36Z"));
    when(store.hasDegradedCheckpoint()).thenReturn(false);

    Meta meta = metaService.meta(0, 20, 3L);

    assertThat(meta.consistency()).isEqualTo("EVENTUAL");
    assertThat(meta.bootstrapComplete()).isTrue();
    assertThat(meta.degraded()).isFalse();
    assertThat(meta.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-10-20T13:20:36Z"));
    assertThat(meta.totalElements()).isEqualTo(3L);
  }

  @Test
  void dltMarcaElReporteComoDegradado() {
    when(store.isBootstrapReady()).thenReturn(true);
    when(store.hasDegradedCheckpoint()).thenReturn(true);

    Meta meta = metaService.meta(null, null, null);

    assertThat(meta.degraded()).isTrue();
  }

  @Test
  void sedeAjenaDeEmpleadoEsNotFound() {
    CurrentUser user = mock(CurrentUser.class);
    when(user.isCliente()).thenReturn(false);
    when(user.isAdmin()).thenReturn(false);
    when(user.isEmpleado()).thenReturn(true);
    when(user.sedes()).thenReturn(List.of(1));
    SedeAccess access = new SedeAccess(user, mock(JdbcTemplate.class));

    assertThatThrownBy(() -> access.require(2, true))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void empleadoSinSedeEsValidacionEnLosListados() {
    CurrentUser user = mock(CurrentUser.class);
    when(user.isCliente()).thenReturn(false);
    when(user.isAdmin()).thenReturn(false);
    when(user.isEmpleado()).thenReturn(true);
    SedeAccess access = new SedeAccess(user, mock(JdbcTemplate.class));

    assertThatThrownBy(() -> access.require(null, true))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void clienteNoAccedeAReportes() {
    CurrentUser user = mock(CurrentUser.class);
    when(user.isCliente()).thenReturn(true);
    SedeAccess access = new SedeAccess(user, mock(JdbcTemplate.class));

    assertThatThrownBy(() -> access.require(1, true))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  void adminSinSedeEnElDashboardPideElConsolidado() {
    CurrentUser user = mock(CurrentUser.class);
    when(user.isCliente()).thenReturn(false);
    when(user.isAdmin()).thenReturn(true);
    when(user.isEmpleado()).thenReturn(false);
    SedeAccess access = new SedeAccess(user, mock(JdbcTemplate.class));

    assertThat(access.require(null, false)).isNull();
  }

  @Test
  void sedeInexistenteDeAdminEsNotFound() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), any(Class.class), anyInt())).thenReturn(0);
    CurrentUser user = mock(CurrentUser.class);
    when(user.isCliente()).thenReturn(false);
    when(user.isAdmin()).thenReturn(true);
    when(user.isEmpleado()).thenReturn(false);
    SedeAccess access = new SedeAccess(user, jdbc);

    assertThatThrownBy(() -> access.require(999, true))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).code())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  /** Retrocede la última conexión activa para simular el paso del umbral de degradación. */
  @SuppressWarnings("unchecked")
  private void retrocederUltimaConexion(int minutos) {
    try {
      Field field = ConnectivityState.class.getDeclaredField("snapshot");
      field.setAccessible(true);
      AtomicReference<ConnectivityState.Snapshot> reference =
          (AtomicReference<ConnectivityState.Snapshot>) field.get(connectivity);
      reference.set(
          new ConnectivityState.Snapshot(false, Instant.now().minus(minutos, ChronoUnit.MINUTES)));
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("no se pudo simular la última conexión", error);
    }
  }
}
