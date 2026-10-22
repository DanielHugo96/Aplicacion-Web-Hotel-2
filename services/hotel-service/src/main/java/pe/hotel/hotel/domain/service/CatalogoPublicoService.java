package pe.hotel.hotel.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.hotel.hotel.domain.dto.CategoriaPublica;
import pe.hotel.hotel.domain.dto.HotelPublico;
import pe.hotel.hotel.domain.dto.ImagenPublica;
import pe.hotel.hotel.domain.exception.NotFoundException;

/** Catalogo publico de solo lectura: cuatro GET sin token, con datos curados y cache de 60 s. */
@Service
public class CatalogoPublicoService {

  /** Tope de la galeria publica por categoria (contrato {@code CategoriaPublica.imagenes}). */
  private static final int MAX_IMAGENES_GALERIA = 10;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired(required = false)
  private JdbcTemplate jdbcTemplate;

  public List<HotelPublico> listarHoteles(String slug) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT f.id_hotel, f.slug, f.nombre, h.descripcion, h.direccion, h.ciudad, "
                + "h.telefono, h.correo_contacto, h.imagen_url, h.imagen_alt, "
                + "f.id_categoria, f.capacidad, f.precio_desde, "
                + "f.imagenes::text AS imagenes "
                + "FROM fn_catalogo_publico(?, NULL) f "
                + "JOIN hotel h ON h.id = f.id_hotel "
                + "ORDER BY f.id_hotel, f.id_categoria",
            slug);
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Integer, List<Map<String, Object>>> porHotel =
        rows.stream()
            .collect(
                Collectors.groupingBy(
                    r -> ((Number) r.get("id_hotel")).intValue(),
                    Collectors.toCollection(ArrayList::new)));
    return porHotel.entrySet().stream()
        .map(
            e -> {
              Map<String, Object> first = e.getValue().get(0);
              return new HotelPublico(
                  (Integer) first.get("id_hotel"),
                  (String) first.get("slug"),
                  (String) first.get("nombre"),
                  (String) first.get("descripcion"),
                  (String) first.get("direccion"),
                  (String) first.get("ciudad"),
                  (String) first.get("telefono"),
                  (String) first.get("correo_contacto"),
                  (String) first.get("imagen_url"),
                  (String) first.get("imagen_alt"));
            })
        .collect(Collectors.toList());
  }

  public HotelPublico detalleHotel(String slug) {
    List<HotelPublico> hoteles = listarHoteles(slug);
    if (hoteles.isEmpty()) {
      throw new NotFoundException("sede no encontrada");
    }
    return hoteles.get(0);
  }

  public List<CategoriaPublica> listarCategorias(String slug, Integer idCategoria, Integer capacidad) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT f.id_categoria, f.descripcion, c.detalle, f.capacidad, f.precio_desde, "
                + "f.imagenes::text AS imagenes "
                + "FROM fn_catalogo_publico(?, ?) f "
                + "JOIN categoria c ON c.id = f.id_categoria "
                + "ORDER BY f.id_categoria",
            slug,
            idCategoria);
    if (rows.isEmpty()) {
      throw new NotFoundException("categoria no encontrada");
    }
    List<CategoriaPublica> categorias =
        rows.stream().map(this::toCategoriaPublica).collect(Collectors.toList());
    if (capacidad != null) {
      categorias =
          categorias.stream().filter(c -> c.capacidad().equals(capacidad)).collect(Collectors.toList());
    }
    if (categorias.isEmpty()) {
      throw new NotFoundException("categoria no encontrada");
    }
    return categorias;
  }

  public CategoriaPublica detalleCategoria(String slug, Integer idCategoria) {
    List<CategoriaPublica> cats = listarCategorias(slug, idCategoria, null);
    return cats.get(0);
  }

  private CategoriaPublica toCategoriaPublica(Map<String, Object> r) {
    Number precio = (Number) r.get("precio_desde");
    return new CategoriaPublica(
        (Integer) r.get("id_categoria"),
        (String) r.get("descripcion"),
        (String) r.get("detalle") == null ? "" : (String) r.get("detalle"),
        ((Number) r.get("capacidad")).intValue(),
        precio == null ? java.math.BigDecimal.ZERO : java.math.BigDecimal.valueOf(precio.doubleValue()),
        "PEN",
        leerImagenes(r.get("imagenes")));
  }

  @SuppressWarnings("unchecked")
  private List<ImagenPublica> leerImagenes(Object imagenes) {
    List<ImagenPublica> out = new ArrayList<>();
    if (imagenes == null) {
      return out;
    }
    try {
      JsonNode node =
          imagenes instanceof String s ? MAPPER.readTree(s) : MAPPER.valueToTree(imagenes);
      if (node == null || !node.isArray()) {
        return out;
      }
      for (JsonNode n : node) {
        if (out.size() == MAX_IMAGENES_GALERIA) {
          break;
        }
        out.add(new ImagenPublica(n.get("url").asText(), n.get("alt").asText()));
      }
    } catch (Exception ignored) {
    }
    return out;
  }
}
