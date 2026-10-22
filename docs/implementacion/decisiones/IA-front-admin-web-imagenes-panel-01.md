# IA-front-admin-web-imagenes-panel-01 — alt del producto y assets compartidos con la landing

- **Plan:** `front-admin-web-imagenes-panel`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

[`08` §6](../../especificacion/08-admin-web.md#6-imágenes-en-el-panel) exige que «toda miniatura lleve `alt`» y que el producto muestre su imagen en catálogo, carrito y ventas. El contrato de `inventory` solo tiene `imagenUrl`: no existe `imagenAlt` para producto, a diferencia de sede y habitación, que sí lo llevan en el contrato. La spec tampoco fija la carpeta de los assets del panel.

## Qué se decidió

Componer el `alt` del producto con su nombre (y la cantidad o el importe de la línea no entran en el texto alternativo); marcar `aria-hidden` las miniaturas de adorno; y servir los assets del panel desde `apps/admin-web/public/assets/{sedes,habitaciones,productos}/`, con las rutas que fijan `back-hotel-presentacion-real` §5 y `back-inventory-imagenes-producto` §5 y una ficha `CREDITOS.md` propia.

## Por qué

Sin campo en el contrato, el nombre del producto es la descripción disponible y suficiente para un lector de pantalla. Duplicar los archivos en el `public/` de cada app es la única forma de que los dos builds los sirvan, y mantener los mismos nombres evita que un seed apunte a una ruta inexistente.

## Alternativas descartadas

- **Añadir `imagenAlt` a `producto`.** Es un cambio de contrato de otro servicio y la spec no lo pide; queda fuera del plan.
- **Dejar el `alt` vacío.** Incumple [`08` §6](../../especificacion/08-admin-web.md#6-imágenes-en-el-panel) y el checklist del sistema de diseño.
- **Descargar la imagen o subir archivos desde el panel.** Prohibido por [`08` §6](../../especificacion/08-admin-web.md#6-imágenes-en-el-panel): el panel solo muestra la URL que acepta el contrato.

## A quién afecta

- `front-admin-web-imagenes-panel`: es su regla de UI.
- `front-public-web-presentacion-real`: comparte las rutas y el formato de ficha de créditos.
- Contratos: ninguno.
