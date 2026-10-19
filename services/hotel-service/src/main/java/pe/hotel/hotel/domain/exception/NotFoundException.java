package pe.hotel.hotel.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Recurso no encontrado o fuera de alcance (anti-enumeracion). */
public class NotFoundException extends ResponseStatusException {

  public NotFoundException(String reason) {
    super(HttpStatus.NOT_FOUND, reason);
  }
}
