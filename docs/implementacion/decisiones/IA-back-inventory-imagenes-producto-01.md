# IA-back-inventory-imagenes-producto-01 — migración V6 y rutas de las imágenes de producto

- **Plan:** `back-inventory-imagenes-producto`
- **Paso:** 1 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

[`inventory/base-de-datos.md` §Seeds](../../especificacion/microservicios/inventory/base-de-datos.md#seeds) exige imágenes propias en `/assets/productos/` con `alt` descriptivo y ficha de créditos. El seed actual, en `V4__seeds_inventario.sql`, apunta a `/assets/demo/producto-*.png`; `inventory-service` ya tiene aplicadas `V1`–`V5` y `inventory.yaml` publica un único ejemplo de nombre: `/assets/productos/agua-500ml.png`.

## Qué se decidió

Corregir el dato en `V6__seeds_productos_imagenes.sql`, con `UPDATE` limitado a las filas que aún tienen la URL `/assets/demo/…`, y fijar las rutas `/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png`. Son las mismas que exige `front-admin-web-imagenes-panel` para sus assets.

## Por qué

La migración nueva evita el checksum roto de `V4`; el `UPDATE` condicionado no pisa una imagen que alguien haya cambiado desde el panel. El nombre del primer archivo ya está fijado por el contrato; los otros dos siguen el mismo patrón y el de su SKU, y quedan declarados en los dos planes para que back y front no se desalineen.

## Alternativas descartadas

- **Editar `V4__seeds_inventario.sql`.** Checksum ya aplicado.
- **Mantener `/assets/demo/`.** La spec retira esa ruta de la presentación y `08` §6 exige que la imagen se vea en el panel.
- **Añadir `imagenAlt` a `producto`.** El contrato de inventory no lo tiene; el `alt` lo compone la UI con el nombre del producto ([`08` §6](../../especificacion/08-admin-web.md#6-imágenes-en-el-panel)).

## A quién afecta

- `back-inventory-imagenes-producto`: es su migración.
- `front-admin-web-imagenes-panel`: usa exactamente esas tres rutas.
- Contratos: ninguno; el ejemplo de `inventory.yaml` ya es `/assets/productos/agua-500ml.png`.
