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

-- Pisos con ids deterministas: 11 (sede 1), 21 (sede 2), 31 (sede 3), "Primer piso".
INSERT INTO piso (id, id_hotel, descripcion, estado, version)
SELECT v.id, h.id, 'Primer piso', true, 1
FROM (VALUES (11, 'lima-centro'), (21, 'miraflores'), (31, 'arequipa')) AS v(id, slug)
JOIN hotel h ON h.slug = v.slug
ON CONFLICT DO NOTHING;

-- Habitaciones con ids deterministas: todas LISTA (id_estado_habitacion=1),
-- activas/publicadas, version 1. Imagen placeholder local bajo /assets/demo/, alt explícito.
INSERT INTO habitacion (id, id_hotel, id_piso, id_categoria, numero, detalle, precio, id_estado_habitacion, estado, publicada, version)
SELECT v.id, h.id, v.id_piso, c.id, v.numero, v.detalle, v.precio, 1, true, true, 1
FROM (VALUES
  (101, 'lima-centro', 11, 'Individual', '101', 'Habitacion individual de demostracion', 70.00),
  (102, 'lima-centro', 11, 'Doble', '102', 'Habitacion doble de demostracion', 100.00),
  (201, 'miraflores', 21, 'Individual', '101', 'Habitacion individual de demostracion', 80.00),
  (202, 'miraflores', 21, 'Doble', '102', 'Habitacion doble de demostracion', 120.00),
  (301, 'arequipa', 31, 'Individual', '101', 'Habitacion individual de demostracion', 60.00),
  (302, 'arequipa', 31, 'Doble', '102', 'Habitacion doble de demostracion', 90.00)
) AS v(id, slug, id_piso, categoria, numero, detalle, precio)
JOIN hotel h ON h.slug = v.slug
JOIN categoria c ON c.descripcion = v.categoria
ON CONFLICT DO NOTHING;

-- Imagenes placeholder locales de la habitacion (no de la sede), orden unico por habitacion.
INSERT INTO imagen_habitacion (id_habitacion, url, alt, orden)
SELECT hab.id, '/assets/demo/hab-101-1.jpg', 'Habitacion de demostracion', 1
FROM habitacion hab WHERE hab.id = 101
ON CONFLICT (id_habitacion, orden) DO NOTHING;
INSERT INTO imagen_habitacion (id_habitacion, url, alt, orden)
SELECT hab.id, '/assets/demo/hab-102-1.jpg', 'Habitacion de demostracion', 1
FROM habitacion hab WHERE hab.id = 102
ON CONFLICT (id_habitacion, orden) DO NOTHING;

-- Secuencias por encima del maximo insertado: nextval devuelve 32 y 303.
SELECT setval(pg_get_serial_sequence('piso', 'id'), (SELECT MAX(id) FROM piso));
SELECT setval(pg_get_serial_sequence('habitacion', 'id'), (SELECT MAX(id) FROM habitacion));

-- Proyeccion de ocupacion vacia: parte DESCONOCIDA, sin filas ocupadas.
-- (se llena por el consumidor hotel-ocupacion-v1 de reception.recepcion.v1)
