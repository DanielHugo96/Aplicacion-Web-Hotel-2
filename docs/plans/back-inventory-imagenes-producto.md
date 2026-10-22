# back-inventory-imagenes-producto — imágenes propias de los productos sembrados (F5)

| Campo | Valor |
|---|---|
| Plan | `back-inventory-imagenes-producto` |
| Ámbito | `back` |
| Componente | `inventory-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
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

- [x] 1. Crear `V6__seeds_productos_imagenes.sql` con un `UPDATE` por `id` que solo toca las filas que aún conservan la URL `/assets/demo/…`, para no pisar una imagen editada desde el panel. Verificación: Flyway aplica `V6` sobre una `inventario_db` sembrada sin error de checksum.
- [x] 2. Fijar las rutas `/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png` para los productos 1, 2 y 3. Verificación: `GET /api/producto/listar` devuelve las tres rutas y ninguna contiene `/assets/demo/`.
- [x] 3. Comprobar que no se altera el resto del seed. Verificación: `GET /api/producto/buscar/{id}` del producto 1 devuelve nombre, detalle, SKU, precio y cantidad iguales a los de §Seeds; `version` sigue en 1.
- [x] 4. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`, `python scripts/validar-contratos.py --base origin/main` no detecta retiradas y `python scripts/validar-planes.py` da 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `docs/contracts/openapi/inventory.yaml` ya publica el ejemplo de `/assets/productos/`.
- **Eventos y comandos:** ninguno. `ProductoStockSnapshot` conserva su forma.
- **Persistencia:** `services/inventory-service/src/main/resources/db/migration/V6__seeds_productos_imagenes.sql` sobre la columna `producto.imagen_url`. Sin tablas ni columnas nuevas.
- **Rutas de imagen que fija este plan:** `/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png`.
- **Errores:** ninguno.

## 6. Pruebas

Sin caso de la matriz: el cambio es de dato semilla y no define comportamiento. Evidencia de ejecución
(2026-10-21) sobre el Compose local (`hotel-postgres-1`, `inventory_db` en `localhost:55432`), con
`identity-service` en `:8081` emitiendo el token real de `admin@hotel.test` e `inventory-service` en
`:8084` arrancados desde este árbol con
`bash scripts/mvn.sh -f services/pom.xml -pl <servicio> spring-boot:run`. Guiones y salidas completas
en `%TEMP%/opencode/inv-img-ev/` (`arrancar.sh`, `arrancar-identity.sh`, `evidencia.sh`,
`listar.py`, `arranque.log`, `pasos-2-3.txt`, `stockdb-it.txt`, `verify.txt`, `contratos.txt`,
`planes.txt`); aquí va la salida literal, sin credenciales ni tokens.

### Paso 1 — Flyway aplica `V6` sin error de checksum

`inventory_db` tenía `V1`–`V5` aplicadas y los seeds con `/assets/demo/…` (el escenario del plan). El
arranque del servicio aplicó la migración y `flyway_schema_history` quedó así:

```
 version |         description         | success
---------+-----------------------------+---------
 1       | api idempotency             | t
 2       | outbox inbox                | t
 3       | inventario tablas           | t
 4       | seeds inventario            | t
 5       | ajusta secuencia producto   | t
 6       | seeds productos imagenes    | t
```

Log del arranque sobre la base ya migrada (`arranque.log`): `Migrating schema "public" to version
"6 - seeds productos imagenes"` y `Successfully applied 1 migration to schema "public"`. Sobre base
limpia (la que recrea `StockDbIT`) Flyway registra `Successfully applied 6 migrations to schema
"public", now at version v6` (`stockdb-it.txt`). `V4__seeds_inventario.sql` no se tocó: sigue en el
árbol con su cuerpo original y con `/assets/demo/…`.

`UPDATE` acotado (paso 1 y salvaguarda del plan): la prueba
`StockDbIT.laCorreccionDeImagenesNoPisaUnaImagenEditada` cambia la imagen del producto 1 a
`/assets/productos/agua-500ml-editorial.png` y reejecuta el cuerpo de `V6` como el migrador; la fila 1
conserva el valor editado y la fila 2, que seguía en `/assets/demo/…`, pasa a
`/assets/productos/snack.png`. En verde en la corrida del IT (18 pruebas, 0 fallos).

### Paso 2 — `GET /api/producto/listar` con las tres rutas nuevas

`curl -s -H "Authorization: Bearer <token admin>" "http://localhost:8084/api/producto/listar?idHotel=1"`
(salida en `pasos-2-3.txt`):

```
1 | AGUA-500 | Agua 500ml  | /assets/productos/agua-500ml.png
2 | SNACK-01 | Snack       | /assets/productos/snack.png
3 | ASEO-01  | Kit de aseo | /assets/productos/kit-aseo.png
rutas con /assets/demo/: 0
HTTP=200
```

Las mismas rutas se sirven en las otras dos sedes (`?idHotel=2` y `?idHotel=3`), porque `imagen_url`
es global del producto:

```
idHotel=2 -> /assets/productos/agua-500ml.png, /assets/productos/snack.png, /assets/productos/kit-aseo.png
idHotel=3 -> /assets/productos/agua-500ml.png, /assets/productos/snack.png, /assets/productos/kit-aseo.png
```

Consulta directa a `producto` (`psql` como `inventory_migrator`):

```
1|AGUA-500|Agua 500ml|Botella de agua de 500 ml|/assets/productos/agua-500ml.png|t|1
2|SNACK-01|Snack|Snack energético de muestra|/assets/productos/snack.png|t|1
3|ASEO-01|Kit de aseo|Kit básico de aseo del huésped|/assets/productos/kit-aseo.png|t|1
filas con /assets/demo/ = 0
filas actualizadas por V6 (UPDATE acotado) = 3
```

Las rutas coinciden con las que exige `front-admin-web-imagenes-panel` (§5) y con el ejemplo ya
publicado en `docs/contracts/openapi/inventory.yaml` (`/assets/productos/agua-500ml.png`).

### Paso 3 — el resto del seed no cambia

`GET /api/producto/buscar/{id}?idHotel=1` con `id=1` responde `HTTP/1.1 200` con `ETag: "1"`:

```
idProducto=1  sku=AGUA-500  nombre=Agua 500ml  detalle=Botella de agua de 500 ml
imagenUrl=/assets/productos/agua-500ml.png  idHotel=1  precio=3.5  cantidad=20  umbralBajo=5
estado=True  version=1  productoVersion=1  fechaCreacion=2026-09-01T10:00:00Z
```

Nombre, detalle, SKU, precio y cantidad son los de §Seeds; `version` sigue en 1 y `updated_at` no se
toca, porque es una corrección del valor semilla y no una mutación de negocio. Las nueve filas de
`producto_sede` (3 productos × 3 sedes) no se modifican: `StockDbIT.seedsTienen9ProductosSedeConValoresEsperados`
y `seedsReejecutadosNoDuplicanNiReponenVentas` siguen en verde.

### Paso 4 — verificación canónica

```
bash scripts/mvn.sh -f services/pom.xml verify
  inventory-service .................................. SUCCESS [ 54.255 s]
  BUILD SUCCESS
  (inventory-service: Tests run: 44, Failures: 0, Errors: 0, Skipped: 0)

python scripts/validar-contratos.py --base origin/main
  Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
  Los contratos solo se han ampliado. Correcto.

python scripts/validar-planes.py
  39 plan(es) revisado(s); 39 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).
```

`StockDbIT` contra la `inventory_db` real (18 pruebas, incluidas las dos nuevas: seeds con
`/assets/productos/` sin restos de `/assets/demo/` y el `UPDATE` acotado que no pisa una imagen
editada):

```
Migrating schema "public" to version "6 - seeds productos imagenes"
Successfully applied 6 migrations to schema "public", now at version v6
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 -- in pe.hotel.inventory.it.StockDbIT
BUILD SUCCESS
```

Salidas: `services/inventory-service/target/surefire-reports/` (por clase) y los ficheros de
`%TEMP%/opencode/inv-img-ev/` citados arriba.

### Entorno

`inventory_db` se recreó dos veces: una la aplicó Flyway al arrancar el servicio y otra el propio
`StockDbIT` (que hace `DROP DATABASE`/`CREATE DATABASE` antes de migrar). En las dos el resultado es
el mismo, y el servicio arrancó con `ddl-auto=validate` sin ningún `Schema-validation` en el log
(`grep -c Schema-validation` da 0). Las advertencias de Eureka de `arranque.log` son ruido de un
`discovery-server` que no está levantado en la sesión; no afectan a la migración ni a la API, y el
servicio responde `liveness=UP`.


## 7. Preguntas abiertas

Ninguna (resuelto por IA: la corrección de las rutas va en la migración `V6` y no en `V4`, ya
aplicada, con un `UPDATE` acotado a las filas que aún tienen `/assets/demo/…`; las tres rutas son
`/assets/productos/agua-500ml.png`, `/assets/productos/snack.png` y `/assets/productos/kit-aseo.png`.
Ver [`IA-back-inventory-imagenes-producto-01.md`](../implementacion/decisiones/IA-back-inventory-imagenes-producto-01.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Los tres productos apuntan a `/assets/productos/`.
- [x] Sin preguntas abiertas.
