package pe.hotel.hotel.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body de PUT .../mantenimiento-completado y .../reactivar. */
public record BlockInput(@NotNull(message = "obligatorio") UUID blockId) {}
