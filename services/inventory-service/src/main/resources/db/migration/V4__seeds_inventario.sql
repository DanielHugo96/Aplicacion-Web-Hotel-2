-- Seeds deterministas de dev de inventory (docs/especificacion/microservicios/inventory/base-de-datos.md #seeds).
-- Idempotentes: reejecutarlas no duplica filas ni repone cantidades ya vendidas.

INSERT INTO producto (id, sku, nombre, detalle, imagen_url, estado, version, created_at)
VALUES
  (1, 'AGUA-500', 'Agua 500ml', 'Botella de agua de 500 ml', '/assets/demo/producto-agua.png', true, 1, '2026-09-01T10:00:00Z'),
  (2, 'SNACK-01', 'Snack', 'Snack energético de muestra', '/assets/demo/producto-snack.png', true, 1, '2026-09-01T10:00:00Z'),
  (3, 'ASEO-01', 'Kit de aseo', 'Kit básico de aseo del huésped', '/assets/demo/producto-aseo.png', true, 1, '2026-09-01T10:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO producto_sede (id_producto, id_hotel, precio, cantidad, umbral_bajo, estado, version)
VALUES
  (1, 1, 3.50, 20, 5, true, 1),
  (2, 1, 8.00, 10, 5, true, 1),
  (3, 1, 5.00, 5, 5, true, 1),
  (1, 2, 3.50, 20, 5, true, 1),
  (2, 2, 8.00, 10, 5, true, 1),
  (3, 2, 5.00, 5, 5, true, 1),
  (1, 3, 3.50, 20, 5, true, 1),
  (2, 3, 8.00, 10, 5, true, 1),
  (3, 3, 5.00, 5, 5, true, 1)
ON CONFLICT (id_producto, id_hotel) DO NOTHING;

-- Los 9 ProductoStockSnapshot de bootstrap se emiten por outbox desde el servicio
-- al arrancar en dev (controlador de bootstrap), con eventId estable para que el
-- inbox de reporting los deduplique ante reenvíos. Esta seed no toca outbox para
-- no publicar duplicados si el servicio ya las encoló.
