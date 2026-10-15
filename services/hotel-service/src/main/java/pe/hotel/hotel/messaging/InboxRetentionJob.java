package pe.hotel.hotel.messaging;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Purga operativa del inbox tras la retención configurada (30 días por defecto), alineada con la
 * idempotencia. Nunca borra pendientes.
 */
public class InboxRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(InboxRetentionJob.class);

  private final InboxStore inboxStore;
  private final MessagingProperties properties;

  public InboxRetentionJob(InboxStore inboxStore, MessagingProperties properties) {
    this.inboxStore = inboxStore;
    this.properties = properties;
  }

  @Scheduled(cron = "${hotel.inbox.purge-cron:0 30 3 * * *}")
  public void purge() {
    try {
      int retentionDays = properties.getInbox().getRetentionDays();
      int purged = inboxStore.purgeOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
      if (purged > 0) {
        log.info("inbox_purge purged={} retention_days={}", purged, retentionDays);
      }
    } catch (Exception ex) {
      log.warn("inbox_purge_error error={}", ex.getMessage());
    }
  }
}
