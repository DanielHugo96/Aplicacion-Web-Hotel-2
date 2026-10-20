package pe.hotel.reception.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Forma completa de Recepcion del contrato (docs/contracts/openapi/reception.yaml). */
public record RecepcionView(
    Integer idRecepcion,
    Integer idHotel,
    Integer idCliente,
    Integer idHabitacion,
    String numero,
    String categoriaNombre,
    String pisoNombre,
    String detalleHabitacion,
    BigDecimal precioHabitacion,
    String nombre,
    String apellido,
    String tipoDocumento,
    String documento,
    String correo,
    LocalDate fechaEntrada,
    LocalDate fechaSalida,
    Instant fechaSalidaConfirmacion,
    BigDecimal precioInicial,
    BigDecimal adelanto,
    String medioPagoAdelanto,
    BigDecimal precioRestante,
    BigDecimal totalPagado,
    BigDecimal costoPenalidad,
    String motivoPenalidad,
    String observacion,
    @JsonProperty("estado") boolean estado,
    String estadoEstadia,
    boolean archivada,
    boolean vencida,
    Long version,
    UUID closureId,
    UUID cleaningCycleId,
    Object cierre) {}
