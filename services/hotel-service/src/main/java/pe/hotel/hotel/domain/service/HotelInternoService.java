package pe.hotel.hotel.domain.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.hotel.hotel.domain.dto.HabitacionInterna;
import pe.hotel.hotel.domain.dto.HotelInterno;
import pe.hotel.hotel.domain.repository.HabitacionRepository;
import pe.hotel.hotel.domain.repository.HotelRepository;
import pe.hotel.hotel.platform.ApiException;
import pe.hotel.hotel.platform.ErrorCode;

/** Rutas internas /internal/** para servicios (token maquina por scope, sin Gateway). */
@Service
public class HotelInternoService {

  @Autowired(required = false)
  private HabitacionRepository habitacionRepository;
  @Autowired(required = false)
  private HotelRepository hotelRepository;

  public HabitacionInterna habitacionInterna(Integer id) {
    if (habitacionRepository == null) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
    }
    return habitacionRepository
        .findById(id)
        .map(
            h ->
                new HabitacionInterna(
                    h.getId(),
                    h.getIdHotel(),
                    h.getNumero(),
                    h.getDetalle(),
                    h.getIdCategoria(),
                    h.getCategoria() == null ? null : h.getCategoria().getDescripcion(),
                    h.getPiso() == null ? null : h.getPiso().getDescripcion(),
                    h.getPrecio(),
                    h.getEstadoHabitacion() == null ? null : h.getEstadoHabitacion().getCodigo(),
                    h.getEstado(),
                    h.getPiso() != null
                        && h.getPiso().getHotel() != null
                        && h.getPiso().getHotel().getEstado(),
                    h.getPiso() != null
                        && h.getPiso().getHotel() != null
                        && h.getPiso().getHotel().getEstado()
                        ? h.getPiso().getHotel().getNombre()
                        : null,
                    h.getVersion()))
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
  }

  public HotelInterno hotelInterno(Integer id) {
    if (hotelRepository == null) {
      throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
    }
    return hotelRepository
        .findActiveById(id)
        .map(h -> new HotelInterno(h.getId(), h.getNombre(), h.getEstado(), h.getVersion()))
        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
  }
}
