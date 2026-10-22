# back-inventory-imagenes-producto — imágenes propias de los productos sembrados (F5)

| Campo | Valor |
|---|---|
| Plan | `back-inventory-imagenes-producto` |
| Ámbito | `back` |
| Componente | `inventory-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `back-inventory-stock` |
| Spec | [`inventory/base-de-datos.md` §Seeds](../especificacion/microservicios/inventory/base-de-datos.md#seeds); [`02` §Imágenes](../especificacion/02-landing-y-panel.md#imágenes); [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Los tres productos sembrados apuntan a `/assets/productos/` con imágenes propias y dejan de apuntar a `/assets/demo/`. Se comprueba leyendo `GET /api/producto/listar` y verificando que las rutas servidas existen en el panel.

## 2. Alcance

**Entra:**

- `services/inventory-service/src/main/resources/db/migration/V6__seeds_productos_imagenes.sql`: actualización de `producto.imagen_url` de los productos 1, 2 y 3.
- Pruebas del servicio y este plan con su fila de catálogo y su bitácora de decisión.

**Fuera de alcance:**

- Nombres, SKU, precios, cantidades y umbrales: no cambian.
- Un campo `imagenAlt` en `producto`: el contrato no lo tiene; el `alt` de la miniatura lo pone la UI con el nombre del producto ([`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel)).
- `V4__seeds_inventario.sql`: no se edita; ya está aplicada.
- Los archivos de imagen: los sirve `admin-web` desde `/assets/productos/` y los incorpora `front-admin-web-imagenes-panel`.
- `docs/contracts/**` y `docs/especificacion/**`: no se modifican.

## 3. Contexto

`0c5b0a2` cambia [`inventory/base-de-datos.md` §Seeds](../especificacion/microservicios/inventory/base-de-datos.md#seeds): los productos usan «imágenes locales propias en `/assets/productos/` con `alt` descriptivo y ficha de créditos». El seed actual (`V4__seeds_inventario.sql`) apunta a `/assets/demo/producto-*.png`, y `inventory.yaml` ya publica el ejemplo `/assets/productos/agua-500ml.png`.

No hay cambio de contrato: solo de dato semilla. Este plan decide, y registra en [`IA-back-inventory-imagenes-producto-01.md`](../implementacion/decisiones/IA-back-inventory-imagenes-producto-01.md), la migración `V6` (la `V5` ya existe y está aplicada) y los tres nombres de archivo, que son los mismos que exige `front-admin-web-imagenes-panel`.

La fase es `F5` porque `F0`–`F4` están cerradas en `main`: este cambio llega después del incremento y no reabre una fase anterior.

## 4. Pasos

- [ ] 1. Crear `V6__seeds_productos_imagenes.sql` con un `UPDATE` por `id` que solo toca las filas que aún conservan la URL `/assets/demo/…`, para no pisar una imagen editada desde el panel. Verificación: Flyway aplica `V6` sobre una `inventario_db` sembrada sin error de checksum.
- [ ] 2. Fijar las rutas `/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png` para los productos 1, 2 y 3. Verificación: `GET /api/producto/listar` devuelve las tres rutas y ninguna contiene `/assets/demo/`.
- [ ] 3. Comprobar que no se altera el resto del seed. Verificación: `GET /api/producto/buscar/{id}` del producto 1 devuelve nombre, detalle, SKU, precio y cantidad iguales a los de §Seeds; `version` sigue en 1.
- [ ] 4. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`, `python scripts/validar-contratos.py --base origin/main` no detecta retiradas y `python scripts/validar-planes.py` da 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `docs/contracts/openapi/inventory.yaml` ya publica el ejemplo de `/assets/productos/`.
- **Eventos y comandos:** ninguno. `ProductoStockSnapshot` conserva su forma.
- **Persistencia:** `services/inventory-service/src/main/resources/db/migration/V6__seeds_productos_imagenes.sql` sobre la columna `producto.imagen_url`. Sin tablas ni columnas nuevas.
- **Rutas de imagen que fija este plan:** `/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png`.
- **Errores:** ninguno.

## 6. Pruebas

- Sin caso de la matriz: el cambio es de dato semilla y no define comportamiento. Evidencia en el PR con la respuesta de `GET /api/producto/listar` y la consulta a `producto`.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-contratos.py --base origin/main` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Los tres productos apuntan a `/assets/productos/`.
- [ ] Sin preguntas abiertas.
