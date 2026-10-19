package pe.hotel.inventory.messaging;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.hotel.inventory.domain.service.StockService;

/**
 * Aplica el comando {@code stock.restituir} de forma atómica: registra el {@code commandId} en el
 * inbox y ejecuta el efecto sobre el stock —con su evento de resolución en el outbox— en la misma
 * transacción local (docs/especificacion/microservicios/inventory/api.md § Rabbit).
 *
 * <p>Si el efecto falla, el registro del inbox revierte con él: el reintento vuelve a aplicar el
 * comando en lugar de descartarlo como duplicado. El consumidor confirma (ack) solo cuando esta
 * llamada retorna, es decir con la transacción ya confirmada.
 */
@Component
@ConditionalOnProperty(
    prefix = "hotel.messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class StockRestituirHandler {

  /** Dedup del consumidor: un {@code commandId} se aplica exactamente una vez. */
  public static final String CONSUMER = "inventory-stock-restituir";

  private final StockService stockService;
  private final InboxStore inbox;

  public StockRestituirHandler(StockService stockService, InboxStore inbox) {
    this.stockService = stockService;
    this.inbox = inbox;
  }

  /**
   * @return {@code true} si el comando se aplicó en esta llamada; {@code false} si ya estaba
   *     procesado (duplicado descartado por el inbox).
   */
  @Transactional
  public boolean aplicar(UUID commandId, Integer idVenta, Integer idHotel, String motivo) {
    if (!inbox.claim(CONSUMER, commandId)) {
      return false;
    }
    stockService.restituir(idVenta, idHotel, motivo);
    return true;
  }
}
