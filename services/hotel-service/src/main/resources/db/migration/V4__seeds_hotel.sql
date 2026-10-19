-- Seeds deterministas de dev para hotel_db (idempotentes).
-- 3 estados fisicos, 3 sedes, 2 categorias, 3 pisos, 6 habitaciones LISTA.
-- Nunca restablecer datos de una demo en marcha: INSERT ... ON CONFLICT DO NOTHING.

-- Estados fisicos fijos (códigos no se reutilizan numericamente).
INSERT INTO estado_habitacion (id, codigo, descripcion, estado, version)
VALUES
  (1, 'LISTA', 'Lista', true, 1),
  (2, 'EN_LIMPIEZA', 'En limpieza', true, 1),
  (3, 'MANTENIMIENTO', 'Mantenimiento', true, 1)
ON CONFLICT (id) DO NOTHING;

-- Sedes: activas y publicadas, version 1.
INSERT INTO hotel (slug, nombre, descripcion, direccion, ciudad, telefono, correo_contacto, estado, publicado, version)
VALUES
  ('lima-centro', 'Hotel Demo Lima Centro', 'Sede de demostracion', 'Direccion de demostracion', 'Lima', '+51 900 000 001', 'contacto@hotel.test', true, true, 1),
  ('miraflores', 'Hotel Demo Miraflores', 'Sede de demostracion', 'Direccion de demostracion', 'Lima', '+51 900 000 001', 'contacto@hotel.test', true, true, 1),
  ('arequipa', 'Hotel Demo Arequipa', 'Sede de demostracion', 'Direccion de demostracion', 'Arequipa', '+51 900 000 001', 'contacto@hotel.test', true, true, 1)
ON CONFLICT (slug) DO NOTHING;

-- Categorias globales: 1 Individual/capacidad 1, 2 Doble/capacidad 2.
INSERT INTO categoria (descripcion, detalle, capacidad, estado, version)
VALUES
  ('Individual', 'Habitacion individual de demostracion', 1, true, 1),
  ('Doble', 'Habitacion doble de demostracion', 2, true, 1)
ON CONFLICT (descripcion) DO NOTHING;

-- Pisos: 11 (sede 1), 21 (sede 2), 31 (sede 3), descripcion "Primer piso".
INSERT INTO piso (id_hotel, descripcion, estado, version)
SELECT h.id, 'Primer piso', true, 1 FROM hotel h WHERE h.slug = 'lima-centro'
ON CONFLICT (id_hotel, descripcion) DO NOTHING;
INSERT INTO piso (id_hotel, descripcion, estado, version)
SELECT h.id, 'Primer piso', true, 1 FROM hotel h WHERE h.slug = 'miraflores'
ON CONFLICT (id_hotel, descripcion) DO NOTHING;
INSERT INTO piso (id_hotel, descripcion, estado, version)
SELECT h.id, 'Primer piso', true, 1 FROM hotel h WHERE h.slug = 'arequipa'
ON CONFLICT (id_hotel, descripcion) DO NOTHING;

-- Habitaciones: todas LISTA (id_estado_habitacion=1), activas/publicadas, version 1.
-- Imagen placeholder local bajo /assets/demo/, alt explícito.
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '101', 'Habitacion individual de demostracion', 70.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Individual'
WHERE h.slug = 'lima-centro' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '102', 'Habitacion doble de demostracion', 100.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Doble'
WHERE h.slug = 'lima-centro' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '101', 'Habitacion individual de demostracion', 80.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Individual'
WHERE h.slug = 'miraflores' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '102', 'Habitacion doble de demostracion', 120.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Doble'
WHERE h.slug = 'miraflores' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '101', 'Habitacion individual de demostracion', 60.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Individual'
WHERE h.slug = 'arequipa' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;
INSERT INTO habitacion (id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT h.id, p.id, c.id, '102', 'Habitacion doble de demostracion', 90.00, 1, true, true, 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN categoria c ON c.descripcion = 'Doble'
WHERE h.slug = 'arequipa' AND p.descripcion = 'Primer piso'
ON CONFLICT DO NOTHING;

-- Imagenes placeholder locales (orden unico por habitacion).
INSERT INTO imagen_habitacion (id_habitacion, url, alt, orden)
SELECT h.id, '/assets/demo/hab-101-1.jpg', 'Habitacion de demostracion', 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN habitacion hab ON hab.id_hotel = h.id AND hab.numero = '101'
WHERE h.slug = 'lima-centro' AND p.descripcion = 'Primer piso'
ON CONFLICT (id_habitacion, orden) DO NOTHING;
INSERT INTO imagen_habitacion (id_habitacion, url, alt, orden)
SELECT h.id, '/assets/demo/hab-102-1.jpg', 'Habitacion de demostracion', 1
FROM hotel h JOIN piso p ON p.id_hotel = h.id JOIN habitacion hab ON hab.id_hotel = h.id AND hab.numero = '102'
WHERE h.slug = 'lima-centro' AND p.descripcion = 'Primer piso'
ON CONFLICT (id_habitacion, orden) DO NOTHING;

-- Proyeccion de ocupacion vacia: parte DESCONOCIDA, sin filas ocupadas.
-- (se llena por el consumidor hotel-ocupacion-v1 de reception.recepcion.v1)
