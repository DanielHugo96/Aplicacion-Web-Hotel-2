-- Presentación real de hotel_db: foto principal de la sede y contenido Casa Sillar.
-- DDL de referencia: docs/especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia
-- Seeds: docs/especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas
--
-- La migración es aditiva y correctiva sobre las filas que ya sembró V4. No se edita
-- V4__seeds_hotel.sql porque su checksum ya está aplicado en las bases migradas y Flyway
-- rechazaría el cambio (docs/implementacion/decisiones/IA-back-hotel-presentacion-real-01.md).
-- Cada UPDATE reconoce la fila semilla por su contenido anterior, así que una sede o una
-- habitación editada desde el panel no se pisa al reejecutar.

-- 1) Foto principal de la sede: una sola imagen opcional por sede, con su descripción.
ALTER TABLE hotel
  ADD COLUMN imagen_url varchar(500),
  ADD COLUMN imagen_alt varchar(150);

-- La descripción alternativa acompaña a la imagen, nunca va sola.
ALTER TABLE hotel
  ADD CONSTRAINT ck_hotel_imagen_alt
  CHECK (imagen_url IS NULL OR imagen_alt IS NOT NULL);

-- 2) Sedes de la cadena Casa Sillar: nombre comercial, dirección, teléfono y correo.
--    Se conservan slug, ciudad, estado, publicado y version (los seeds son version 1).
UPDATE hotel h
SET nombre       = v.nombre,
    descripcion  = v.descripcion,
    direccion    = v.direccion,
    telefono     = v.telefono,
    correo_contacto = 'contacto@casasillar.pe',
    imagen_url   = v.imagen_url,
    imagen_alt   = v.imagen_alt
FROM (VALUES
  ('lima-centro', 'Casa Sillar Lima Centro',
   'Casona restaurada a dos cuadras de la Plaza Mayor, con patio de sillar y recepción permanente.',
   'Jr. de la Unión 540, Cercado de Lima', '+51 1 428 6400',
   '/assets/sedes/lima-centro-fachada.jpg',
   'Fachada de piedra de la sede de Lima Centro al atardecer'),
  ('miraflores', 'Casa Sillar Miraflores',
   'Casa de los años cuarenta frente al malecón de Miraflores, con jardín interior y salón de lectura.',
   'Av. José Larco 1105, Miraflores', '+51 1 445 2180',
   '/assets/sedes/miraflores-fachada.jpg',
   'Fachada de la sede de Miraflores con su jardín de entrada'),
  ('arequipa', 'Casa Sillar Arequipa',
   'Casona de sillar en el centro histórico de Arequipa, con galería de arcos y vista al Misti desde la terraza.',
   'Calle Mercaderes 218, Cercado de Arequipa', '+51 54 216 800',
   '/assets/sedes/arequipa-fachada.jpg',
   'Fachada de sillar de la sede de Arequipa con el Misti al fondo')
) AS v(slug, nombre, descripcion, direccion, telefono, imagen_url, imagen_alt)
WHERE h.slug = v.slug
  AND h.nombre LIKE 'Hotel Demo %';

-- 3) Detalle propio de las dos categorías, sin la palabra «demostración».
UPDATE categoria c
SET detalle = v.detalle
FROM (VALUES
  ('Individual', 'Habitación individual con cama de una plaza, escritorio y baño privado.'),
  ('Doble', 'Habitación doble con cama de dos plazas o dos camas, mesa de trabajo y baño privado.')
) AS v(descripcion, detalle)
WHERE c.descripcion = v.descripcion
  AND c.detalle LIKE '%demostracion%';

-- 4) Detalle propio de las seis habitaciones; id, número, precio, id_piso y publicada no cambian.
UPDATE habitacion hab
SET detalle = v.detalle
FROM (VALUES
  (101, 'Habitación individual de la casona, con ventana al patio de sillar y baño privado.'),
  (102, 'Habitación doble con vista a la calle, dos camas y mesa de trabajo.'),
  (201, 'Habitación individual frente al jardín interior, con armario y baño privado.'),
  (202, 'Habitación doble con balcón al jardín y espacio de estar.'),
  (301, 'Habitación individual de sillar, con ventana al patio y baño privado.'),
  (302, 'Habitación doble con techo alto de sillar y vista a la galería.')
) AS v(id, detalle)
WHERE hab.id = v.id
  AND hab.detalle LIKE '%demostracion%';

-- 5) Galería de habitación: se sustituyen las dos filas placeholder de V4 (reconocibles por su
--    URL /assets/demo/) y se completa hasta tres imágenes por habitación.
UPDATE imagen_habitacion i
SET url = v.url,
    alt = v.alt
FROM (VALUES
  (101, '/assets/habitaciones/lima-centro-individual-1.jpg',
   'Habitación individual de la sede Lima Centro con cama de una plaza y cabecera de madera'),
  (102, '/assets/habitaciones/lima-centro-doble-1.jpg',
   'Habitación doble de la sede Lima Centro con dos camas y ventana a la calle')
) AS v(id_habitacion, url, alt)
WHERE i.id_habitacion = v.id_habitacion
  AND i.orden = 1
  AND i.url LIKE '/assets/demo/%';

INSERT INTO imagen_habitacion (id_habitacion, url, alt, orden)
SELECT v.id_habitacion, v.url, v.alt, v.orden
FROM (VALUES
  (101, '/assets/habitaciones/lima-centro-individual-1.jpg',
   'Habitación individual de la sede Lima Centro con cama de una plaza y cabecera de madera', 1),
  (101, '/assets/habitaciones/lima-centro-individual-2.jpg',
   'Escritorio y lámpara de lectura de la habitación individual de Lima Centro', 2),
  (101, '/assets/habitaciones/lima-centro-individual-3.jpg',
   'Baño privado con ducha de la habitación individual de Lima Centro', 3),
  (102, '/assets/habitaciones/lima-centro-doble-1.jpg',
   'Habitación doble de la sede Lima Centro con dos camas y ventana a la calle', 1),
  (102, '/assets/habitaciones/lima-centro-doble-2.jpg',
   'Mesa de trabajo junto a la ventana de la habitación doble de Lima Centro', 2),
  (102, '/assets/habitaciones/lima-centro-doble-3.jpg',
   'Baño privado de la habitación doble de Lima Centro', 3),
  (201, '/assets/habitaciones/miraflores-individual-1.jpg',
   'Habitación individual de la sede Miraflores con vista al jardín interior', 1),
  (201, '/assets/habitaciones/miraflores-individual-2.jpg',
   'Armario y espejo de la habitación individual de Miraflores', 2),
  (201, '/assets/habitaciones/miraflores-individual-3.jpg',
   'Baño privado con ducha de la habitación individual de Miraflores', 3),
  (202, '/assets/habitaciones/miraflores-doble-1.jpg',
   'Habitación doble de la sede Miraflores con balcón al jardín', 1),
  (202, '/assets/habitaciones/miraflores-doble-2.jpg',
   'Espacio de estar de la habitación doble de Miraflores', 2),
  (202, '/assets/habitaciones/miraflores-doble-3.jpg',
   'Baño privado de la habitación doble de Miraflores', 3),
  (301, '/assets/habitaciones/arequipa-individual-1.jpg',
   'Habitación individual de la sede Arequipa con muro de sillar', 1),
  (301, '/assets/habitaciones/arequipa-individual-2.jpg',
   'Ventana al patio de la habitación individual de Arequipa', 2),
  (301, '/assets/habitaciones/arequipa-individual-3.jpg',
   'Baño privado de la habitación individual de Arequipa', 3),
  (302, '/assets/habitaciones/arequipa-doble-1.jpg',
   'Habitación doble de la sede Arequipa con techo alto de sillar', 1),
  (302, '/assets/habitaciones/arequipa-doble-2.jpg',
   'Galería con arcos junto a la habitación doble de Arequipa', 2),
  (302, '/assets/habitaciones/arequipa-doble-3.jpg',
   'Baño privado de la habitación doble de Arequipa', 3)
) AS v(id_habitacion, url, alt, orden)
ON CONFLICT (id_habitacion, orden) DO NOTHING;

-- 6) Galería pública: el tope de 10 tiene que ser efectivo. Antes el LIMIT se aplicaba al único
--    agregado y no a las filas, así que una categoría con más de 10 imágenes devolvía todas.
--    Se agrega sobre un subconjunto ordenado por orden y limitado a 10, conservando los filtros
--    de sede, estado y publicada de la habitación.
CREATE OR REPLACE FUNCTION fn_catalogo_publico(p_slug varchar, p_categoria int)
RETURNS TABLE (
  id_hotel     int,
  slug         varchar,
  nombre       varchar,
  id_categoria int,
  descripcion  varchar,
  capacidad    int,
  precio_desde numeric,
  imagenes     jsonb
) LANGUAGE sql STABLE AS $$
  SELECT
    h.id,
    h.slug,
    h.nombre,
    c.id,
    c.descripcion,
    c.capacidad,
    MIN(hab.precio) AS precio_desde,
    COALESCE(
      (SELECT jsonb_agg(jsonb_build_object('url', g.url, 'alt', g.alt) ORDER BY g.orden)
       FROM (
         SELECT i.url, i.alt, i.orden
         FROM imagen_habitacion i
         JOIN habitacion h2 ON h2.id = i.id_habitacion
         WHERE h2.id_hotel = h.id AND h2.id_categoria = c.id
           AND h2.estado AND h2.publicada
         ORDER BY i.orden, i.id
         LIMIT 10
       ) g),
      '[]'::jsonb) AS imagenes
  FROM hotel h
  JOIN categoria c ON true
  JOIN habitacion hab ON hab.id_hotel = h.id AND hab.id_categoria = c.id
    AND hab.estado AND hab.publicada AND hab.id_estado_habitacion = 1
  WHERE h.estado AND h.publicado
    AND (p_slug IS NULL OR h.slug = p_slug)
    AND (p_categoria IS NULL OR c.id = p_categoria)
  GROUP BY h.id, h.slug, h.nombre, c.id, c.descripcion, c.capacidad
$$;
