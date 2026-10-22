-- Imágenes propias de los productos sembrados de inventory_db.
-- Seeds: docs/especificacion/microservicios/inventory/base-de-datos.md#seeds
--
-- La migración es correctiva sobre las filas que ya sembró V4__seeds_inventario.sql. No se
-- edita V4 porque su checksum ya está aplicado en las bases migradas y Flyway rechazaría el
-- cambio (docs/implementacion/decisiones/IA-back-inventory-imagenes-producto-01.md).
--
-- Cada UPDATE reconoce la fila semilla por su contenido anterior (`/assets/demo/…`), así que
-- una imagen cambiada desde el panel no se pisa al reejecutar. Nombre, detalle, sku, estado y
-- version no cambian: solo la URL de la imagen.

UPDATE producto
SET imagen_url = CASE id
                   WHEN 1 THEN '/assets/productos/agua-500ml.png'
                   WHEN 2 THEN '/assets/productos/snack.png'
                   WHEN 3 THEN '/assets/productos/kit-aseo.png'
                 END
WHERE id IN (1, 2, 3)
  AND imagen_url LIKE '/assets/demo/%';
