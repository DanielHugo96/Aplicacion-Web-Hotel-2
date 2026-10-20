package pe.hotel.reporting.projection;

import java.util.List;

/** Los 6 topics del grupo {@code reporting-proyeccion-v1} (05-mensajeria.md § Topics). */
public final class ReportingTopics {

  public static final String PERSONA = "identity.persona.v1";
  public static final String SEDE = "hotel.sede.v1";
  public static final String HABITACION = "hotel.habitacion.v1";
  public static final String RECEPCION = "reception.recepcion.v1";
  public static final String STOCK = "inventory.stock.v1";
  public static final String VENTA = "sales.venta.v1";

  public static final List<String> ALL =
      List.of(PERSONA, SEDE, HABITACION, RECEPCION, STOCK, VENTA);

  private ReportingTopics() {}
}
