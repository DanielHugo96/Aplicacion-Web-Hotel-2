package pe.hotel.reporting.report;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import pe.hotel.reporting.platform.ApiException;
import pe.hotel.reporting.platform.ErrorCode;

/**
 * Validación de rangos de los reportes: ISO-8601 con offset, {@code inicio<fin}, máximo de días y,
 * para ocupación, medianoche de America/Lima (docs/especificacion/microservicios/reporting/api.md).
 */
public final class ReportRange {

  public static final ZoneId LIMA = ZoneId.of("America/Lima");

  private ReportRange() {}

  public record Range(OffsetDateTime inicio, OffsetDateTime fin, LocalDate inicioLima, LocalDate finLima) {}

  public static Range parse(String inicio, String fin, int maxDays, boolean midnightLima) {
    if (inicio == null || inicio.isBlank()) {
      throw validation("inicio", "parámetro obligatorio");
    }
    if (fin == null || fin.isBlank()) {
      throw validation("fin", "parámetro obligatorio");
    }
    OffsetDateTime start;
    OffsetDateTime end;
    try {
      start = OffsetDateTime.parse(inicio);
    } catch (DateTimeParseException ex) {
      throw validation("inicio", "ISO-8601 con offset");
    }
    try {
      end = OffsetDateTime.parse(fin);
    } catch (DateTimeParseException ex) {
      throw validation("fin", "ISO-8601 con offset");
    }
    if (!start.isBefore(end)) {
      throw validation("inicio", "debe ser anterior a fin");
    }
    if (end.isAfter(start.plusDays(maxDays))) {
      throw validation("inicio", "el intervalo supera " + maxDays + " días");
    }
    if (midnightLima && (!isMidnightLima(start) || !isMidnightLima(end))) {
      throw validation("inicio", "ocupación exige medianoche America/Lima");
    }
    return new Range(
        start, end, start.atZoneSameInstant(LIMA).toLocalDate(), end.atZoneSameInstant(LIMA).toLocalDate());
  }

  public static Duration duration(Range range) {
    return Duration.between(range.inicio(), range.fin());
  }

  private static boolean isMidnightLima(OffsetDateTime value) {
    ZonedDateTime lima = value.atZoneSameInstant(LIMA);
    return lima.toLocalTime().equals(LocalTime.MIDNIGHT);
  }

  private static ApiException validation(String field, String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, Map.of(field, message));
  }
}
