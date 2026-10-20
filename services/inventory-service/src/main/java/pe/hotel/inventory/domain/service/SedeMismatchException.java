package pe.hotel.inventory.domain.service;

/**
 * La sede del comando de restitución no coincide con la del movimiento de stock ya registrado. Es un
 * rechazo permanente: el consumidor de {@code stock.restituir} lo lleva a la DLQ con alerta y no
 * mueve stock de otra sede (docs/especificacion/microservicios/inventory/api.md § Rabbit).
 */
public class SedeMismatchException extends RuntimeException {

  public SedeMismatchException(Integer idVenta, Integer idHotelMovimiento, Integer idHotelComando) {
    super(
        "sede del comando ("
            + idHotelComando
            + ") no coincide con la del movimiento "
            + idVenta
            + " ("
            + idHotelMovimiento
            + ")");
  }
}
