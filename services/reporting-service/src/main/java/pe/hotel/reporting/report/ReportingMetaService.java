package pe.hotel.reporting.report;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import pe.hotel.reporting.config.ReportingProperties;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.CorrelationId;
import pe.hotel.reporting.platform.ErrorCode;
import pe.hotel.reporting.platform.Meta;
import pe.hotel.reporting.projection.ProjectionStore;

/**
 * Guardia y meta de consistencia eventual de los reportes:
 * 503 REPORTING_NOT_READY hasta bootstrap LISTO, 503 REPORTING_UNAVAILABLE fuera del umbral de
 * conexión y 200 con degraded=true ante DLT/desconexión conocida dentro del umbral.
 */
@Service
public class ReportingMetaService {

  private final ProjectionStore store;
  private final ConnectivityState connectivity;
  private final ReportingProperties properties;

  public ReportingMetaService(
      ProjectionStore store, ConnectivityState connectivity, ReportingProperties properties) {
    this.store = store;
    this.connectivity = connectivity;
    this.properties = properties;
  }

  public Meta meta(Integer page, Integer size, Long totalElements) {
    if (!store.isBootstrapReady()) {
      throw new ApiException(ErrorCode.REPORTING_NOT_READY);
    }
    if (!connectivity.usable(properties.getDegradedTimeoutMinutes())) {
      throw new ApiException(ErrorCode.REPORTING_UNAVAILABLE);
    }
    OffsetDateTime updatedAt = store.minUpdatedAt();
    boolean degraded = store.hasDegradedCheckpoint() || !connectivity.isConnected();
    return Meta.report(
        CorrelationId.current(), updatedAt, degraded, true, page, size, totalElements);
  }
}
