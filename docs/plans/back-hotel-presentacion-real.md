# back-hotel-presentacion-real — foto de sede, galería sembrada y datos de Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `back-hotel-presentacion-real` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-catalogo`, `back-hotel-operaciones` |
| Spec | [`hotel/api.md` §Públicas nuevas: solo lectura](../especificacion/microservicios/hotel/api.md#públicas-nuevas-solo-lectura) y [§DTO](../especificacion/microservicios/hotel/api.md#dto); [`hotel/base-de-datos.md` §Tablas](../especificacion/microservicios/hotel/base-de-datos.md#tablas), [§DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia), [§Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos), [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas) y [§Verificación](../especificacion/microservicios/hotel/base-de-datos.md#verificación); [`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real) y [§Imágenes](../especificacion/02-landing-y-panel.md#imágenes); [`03` §Límites de campos comunes](../especificacion/03-contrato-comun.md#límites-de-campos-comunes); [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel) |
| Casos | `LP01`, `CRUD01` |
| Change | `—` |

## 1. Objetivo

`hotel-service` persiste y expone la foto principal de la sede y los seeds de la cadena Casa Sillar: `HotelInput`, `Hotel` y `HotelPublico` aceptan `imagenUrl`/`imagenAlt` opcionales, las seis habitaciones sembradas tienen al menos tres imágenes y la galería pública no queda vacía. Se comprueba con los cuatro GET públicos, con el payload de `SedeSnapshot` y con `bash scripts/mvn.sh -f services/pom.xml verify`.

## 2. Alcance

**Entra:**

- `services/hotel-service/src/main/resources/db/migration/V6__hotel_imagenes_presentacion.sql`: columnas `imagen_url`/`imagen_alt` y el CHECK `ck_hotel_imagen_alt` de [§DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia); corrección de los seeds de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas) sobre las filas ya insertadas por `V4__seeds_hotel.sql`.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/model/Hotel.java`: columnas nuevas mapeadas para `ddl-auto=validate`.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/dto/HotelInput.java`: `imagenUrl` y `imagenAlt` opcionales con los límites de `03` y la regla de URL de imagen.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/dto/HotelView.java` y `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/HotelService.java`: devolver y persistir el par; `null` cuando la sede no tiene foto.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/dto/HotelPublico.java` y `CatalogoPublicoService.java`: proyectar `imagenUrl`/`imagenAlt` en los cuatro GET públicos.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/OutboxService.java`: `imagenUrl`/`imagenAlt` en el `payload` de `SedeSnapshot`.
- El tope de 10 de la galería de categoría en `fn_catalogo_publico`: dejarlo efectivo, como fija [§Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos).
- Pruebas del servicio, este plan, su fila de catálogo y la bitácora de decisiones de nivel 2.

**Fuera de alcance:**

- `docs/contracts/**` y `docs/especificacion/**`: ya están publicados en `0c5b0a2`; este plan hace que el código los cumpla y no los modifica.
- Los archivos de imagen: los sirve cada app desde `/assets/`. Este plan fija las rutas y el contenido lo incorporan `front-public-web-presentacion-real` y `front-admin-web-imagenes-panel`.
- Endpoint de subida o almacenamiento de imágenes: el contrato no lo expone y el panel no sube archivos ([`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel)).
- El emisor de bootstrap de seeds que menciona [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas): no existe en `main` y este plan no lo crea (§3).
- `back-reception-*`: el texto de ejemplo de `HabitacionSnapshot` es documental y no cambia comportamiento.
- La selección de categorías publicadas y `precioDesde`: no cambian; la galería solo gana imágenes y tope efectivo.

## 3. Contexto

El cambio viene de `0c5b0a2` (PR #129), que actualiza la especificación a v1.3: [`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real) introduce la marca única Casa Sillar y las reglas de imágenes; [`hotel/api.md` §DTO](../especificacion/microservicios/hotel/api.md#dto) añade `imagenUrl?`/`imagenAlt?` a `HotelInput` y `Hotel`, y [§Públicas nuevas: solo lectura](../especificacion/microservicios/hotel/api.md#públicas-nuevas-solo-lectura) los añade a `HotelPublico`; [`hotel/base-de-datos.md`](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas) cambia los seeds a Casa Sillar, exige una foto por sede y tres imágenes por habitación, y fija el tope de 10 de la galería.

Este plan **no decide** reglas de negocio: el contrato `hotel.yaml` y el schema de `SedeSnapshot` ya están publicados con los campos. Decide tres detalles de ejecución, registrados en [`IA-back-hotel-presentacion-real-01.md`](../implementacion/decisiones/IA-back-hotel-presentacion-real-01.md):

- **Migración `V6`**: `hotel-service` ya tiene aplicadas `V1`–`V5` y la `V5` pertenece a `back-hotel-operaciones`, que por eso aparece en `Depende de`. No se edita `V4__seeds_hotel.sql`: su checksum ya está aplicado y Flyway rechazaría el cambio. La corrección de datos va en `V6`.
- **Rutas de imagen**: `/assets/sedes/<slug>-fachada.jpg`, `/assets/habitaciones/<slug>-<categoria>-<n>.jpg` y las 18 combinaciones de las seis habitaciones; los dos planes de front usan exactamente esas rutas.
- **Fase `F5`**: `F0`–`F4` están cerradas y el cambio es posterior al incremento; la norma no abre una fase anterior ya cerrada.

El `SedeSnapshot` se emite hoy en alta, edición y baja de sede (`HotelService`); el emisor de bootstrap de los seeds no existe en `main`. El plan añade los campos al `payload` que ya se emite y deja constancia de que el bootstrap es un hueco previo, no parte de este delta: la spec ya decía «Bootstrap emite SedeSnapshot» antes de v1.3.

## 4. Pasos

- [x] 1. Crear `V6__hotel_imagenes_presentacion.sql` con `imagen_url varchar(500)` e `imagen_alt varchar(150)` nulas y `CONSTRAINT ck_hotel_imagen_alt CHECK (imagen_url IS NULL OR imagen_alt IS NOT NULL)`. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` en verde y, arrancando el servicio sobre una `hotel_db` limpia, `flyway_schema_history` registra la versión 6 y `\d hotel` muestra las dos columnas y el CHECK.
- [x] 2. En la misma migración, `UPDATE hotel` por `slug` con los nombres, teléfonos, direcciones y correo `contacto@casasillar.pe` de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas), una descripción propia de una o dos frases por sede (≤ 300, sin afirmar disponibilidad), y `imagen_url`/`imagen_alt` con las rutas de §5. Verificación: `GET /api/public/hoteles` devuelve las tres sedes con nombre, dirección y par de imagen nuevos.
- [x] 3. En la misma migración, actualizar `categoria.detalle` y `habitacion.detalle` de las seis habitaciones con descripciones propias sin la palabra «demostración», conservando `id`, `numero`, `precio`, `id_piso` y `publicada`. Verificación: las respuestas de `GET /api/public/hoteles/{slug}/categorias` y de `GET /api/habitacion/buscar/{id}` no contienen «demostración».
- [x] 4. En la misma migración, sustituir las dos filas semilla de `imagen_habitacion` reconocibles por su URL `/assets/demo/…` y completar las demás con `ON CONFLICT (id_habitacion, orden) DO NOTHING`, hasta tres imágenes por habitación en `/assets/habitaciones/`, con `alt` que nombre sede y tipo y `orden` 1..3. Verificación: una consulta agrupa por `id_habitacion` y devuelve 6 filas con `count(*) >= 3`; ninguna fila queda con `alt` vacío.
- [x] 5. Corregir `fn_catalogo_publico` para que el tope de 10 sea efectivo: agregar sobre un subconjunto ordenado por `orden` y limitado a 10, conservando los filtros `estado` y `publicada` de la habitación. Verificación: con 12 imágenes en una categoría de fixture, `GET /api/public/hoteles/{slug}/categorias` devuelve 10; con una habitación `publicada=false`, sus imágenes no aparecen.
- [x] 6. Mapear `imagen_url`/`imagen_alt` en la entidad `Hotel` con longitud y nulabilidad. Verificación: el contexto arranca con `ddl-auto=validate` sin `Schema-validation` en el log.
- [x] 7. Añadir `imagenUrl` (≤ 500) e `imagenAlt` (≤ 150) a `HotelInput` con la regla de URL de imagen de [`03` §Límites de campos comunes](../especificacion/03-contrato-comun.md#límites-de-campos-comunes) y la validación «viajan juntos»: URL sin `alt` y `alt` sin URL se rechazan. Verificación: `POST /api/hotel/registrar` con URL `http://` responde 400 `VALIDATION_ERROR` con `imagenUrl` en `error.fields`; con `alt` y sin URL, 400 con `imagenAlt`.
- [x] 8. Persistir y devolver el par en `HotelService.registrar`, `actualizar` y `toView`, y en `HotelView` con `null` cuando no hay foto. Verificación: alta con el par y `GET /api/hotel/buscar/{id}` devuelve lo enviado; alta sin imagen devuelve `null`/`null`; `version` incrementa en cada edición.
- [x] 9. Proyectar `h.imagen_url` y `h.imagen_alt` en la consulta de `CatalogoPublicoService` y en el record `HotelPublico`. Verificación: los cuatro GET públicos responden 200 sin token y el detalle de sede trae el par; una sede sin imagen lo devuelve nulo sin cambiar las claves.
- [x] 10. Añadir `imagenUrl`/`imagenAlt` al `payload` de `SedeSnapshot` en `OutboxService.emitirSedeSnapshot`, presentes cuando la sede tiene imagen. Verificación: tras un `PUT` de sede, la fila de `outbox` contiene ambos campos y el JSON valida contra `contracts/events/hotel.sede.v1.SedeSnapshot.schema.json`.
- [x] 11. Cubrir con pruebas la validación del par, el mapeo de la entidad, el tope de la galería y el `payload` del snapshot. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`.
- [x] 12. Ejecutar los validadores y anotar la evidencia en §6. Verificación: `python scripts/validar-contratos.py --base origin/main` sin retiradas de contrato y `python scripts/validar-planes.py` con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** `docs/contracts/openapi/hotel.yaml` ya publica `imagenUrl`/`imagenAlt` en `HotelInput`, `Hotel` y `HotelPublico` (commit `0c5b0a2`). Este plan no modifica el contrato.
- **Eventos y comandos:** `hotel.sede.v1` / `SedeSnapshot` amplía su `payload` con `imagenUrl` e `imagenAlt` opcionales, ya declarados en `docs/contracts/events/hotel.sede.v1.SedeSnapshot.schema.json`. No hay topics, comandos ni grupos nuevos; `catalogo.md` §2 no cambia.
- **Persistencia:** `services/hotel-service/src/main/resources/db/migration/V6__hotel_imagenes_presentacion.sql` con las columnas, el CHECK `ck_hotel_imagen_alt` y la corrección de seeds. No se edita `V4__seeds_hotel.sql`.
- **Datos semilla:** sedes, categorías, habitaciones e imágenes de habitación de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas). Los slugs `lima-centro`, `miraflores` y `arequipa` no cambian.
- **Rutas de imagen que fija este plan:** `/assets/sedes/lima-centro-fachada.jpg`, `/assets/sedes/miraflores-fachada.jpg`, `/assets/sedes/arequipa-fachada.jpg`; `/assets/habitaciones/<slug>-<individual|doble>-<1|2|3>.jpg` para las seis habitaciones.
- **Errores:** `VALIDATION_ERROR` (400) para URL no permitida o `alt` ausente.

## 6. Pruebas

Evidencia de ejecución (2026-10-21). Todo se ejecutó contra el Compose local (`hotel-postgres-1`,
`hotel_db` en `localhost:55432`), `identity-service` en `:8081` y `hotel-service` en `:8082`, ambos
arrancados desde este árbol con `bash scripts/mvn.sh -f services/pom.xml -pl <servicio> spring-boot:run`.
Los guiones y sus salidas completas quedaron en `%TEMP%/opencode/hotel-ev/` (`crud.sh`, `publicos.sh`,
`tope.sh`, `demostracion.sh` y los ficheros `ev/*.txt`); aquí va la salida literal, sin credenciales
ni tokens.

### Preparación y migración

`hotel_db` tenía `V1`–`V5` aplicadas y los seeds de demostración; la V6 se aplicó en el arranque y
sobre una base limpia en otro arranque. En ambos casos `flyway_schema_history` acaba igual:

```
 version |         description         | success
---------+-----------------------------+---------
 1       | api idempotency             | t
 2       | outbox inbox                | t
 3       | hotel tablas                | t
 4       | seeds hotel                 | t
 5       | hotel operaciones           | t
 6       | hotel imagenes presentacion | t
```

`\d hotel` sobre la base limpia muestra las columnas y el CHECK del paso 1:

```
 imagen_url      | character varying(500)   |           |          |
 imagen_alt      | character varying(150)   |           |          |
Check constraints:
    "ck_hotel_imagen_alt" CHECK (imagen_url IS NULL OR imagen_alt IS NOT NULL)
```

La base limpia arrancó con `Successfully applied 6 migration` y la que tenía V1–V5 con
`Successfully applied 1 migration`; en las dos, `grep -c Schema-validation` sobre el log da **0** y el
log dice `Started HotelServiceApplication in 11.8 s` / `11.2 s` (paso 6). Contraste que demuestra que
la validación es real: arrancando con `SPRING_FLYWAY_TARGET=5` (sin V6) el contexto falla con
`SchemaManagementException: Schema validation: missing column [imagen_alt] in table [hotel]`.

Seeds tras la migración (pasos 2, 3 y 4):

```
 id |    slug     |         nombre          |    telefono    |                 direccion                 |              imagen_url               |                           imagen_alt
----+-------------+-------------------------+----------------+-------------------------------------------+---------------------------------------+----------------------------------------------------------------
  1 | lima-centro | Casa Sillar Lima Centro | +51 1 428 6400 | Jr. de la Unión 540, Cercado de Lima      | /assets/sedes/lima-centro-fachada.jpg | Fachada de piedra de la sede de Lima Centro al atardecer
  2 | miraflores  | Casa Sillar Miraflores  | +51 1 445 2180 | Av. José Larco 1105, Miraflores           | /assets/sedes/miraflores-fachada.jpg  | Fachada de la sede de Miraflores con su jardín de entrada
  3 | arequipa    | Casa Sillar Arequipa    | +51 54 216 800 | Calle Mercaderes 218, Cercado de Arequipa | /assets/sedes/arequipa-fachada.jpg    | Fachada de sillar de la sede de Arequipa con el Misti al fondo
```

```
 id_habitacion | imagenes | alt_vacio | orden_min | orden_max | rutas_ok
---------------+----------+-----------+-----------+-----------+----------
           101 |        3 |         0 |         1 |         3 | t
           102 |        3 |         0 |         1 |         3 | t
           201 |        3 |         0 |         1 |         3 | t
           202 |        3 |         0 |         1 |         3 | t
           301 |        3 |         0 |         1 |         3 | t
           302 |        3 |         0 |         1 |         3 | t
 filas_demo = 0
```

Los seis `id`, `numero`, `precio`, `id_piso` y `publicada` se conservan (101/11, 102/11, 201/21,
202/21, 301/31, 302/31, todas publicadas). «demostración» ya no aparece en ninguna respuesta ni en la
base (paso 3): `habitacion_demo=0`, `categoria_demo=0`, `hotel_demo=0`, `imagen_demo=0`, y
`contiene_demostracion = False` en `GET /api/public/hoteles`, en `.../categorias` y en
`GET /api/habitacion/buscar/{id}` de las seis habitaciones.

Los `UPDATE` de la migración reconocen la fila semilla por su contenido anterior: sobre una base
desechable, tras editar la habitación 101 y la sede 1 desde el panel y reejecutar el mismo SQL, ambos
`UPDATE` devuelven `UPDATE 0` y los textos editados se conservan.

### Caso `LP01` — los cuatro GET públicos sin token

Comando: `curl -s http://localhost:8082/api/public/hoteles`,
`/api/public/hoteles/{slug}`, `/api/public/hoteles/{slug}/categorias` y
`/api/public/hoteles/{slug}/categorias/{idCategoria}`, sin cabecera `Authorization`.
Salida completa en `ev/pasos-5-9.txt` y `ev/paso-3.txt`.

```
GET /api/public/hoteles -> HTTP 200
lima-centro | Casa Sillar Lima Centro | Jr. de la Unión 540, Cercado de Lima | /assets/sedes/lima-centro-fachada.jpg | Fachada de piedra de la sede de Lima Centro al atardecer
miraflores  | Casa Sillar Miraflores  | Av. José Larco 1105, Miraflores      | /assets/sedes/miraflores-fachada.jpg  | Fachada de la sede de Miraflores con su jardín de entrada
arequipa    | Casa Sillar Arequipa    | Calle Mercaderes 218, Cercado de Arequipa | /assets/sedes/arequipa-fachada.jpg | Fachada de sillar de la sede de Arequipa con el Misti al fondo

GET /api/public/hoteles/lima-centro -> HTTP 200
{"idHotel":1,"slug":"lima-centro","nombre":"Casa Sillar Lima Centro",...,"imagenUrl":"/assets/sedes/lima-centro-fachada.jpg","imagenAlt":"Fachada de piedra de la sede de Lima Centro al atardecer"}

GET /api/public/hoteles/lima-centro/categorias -> HTTP 200
cat 1 Individual | claves = ['capacidad','descripcion','detalle','idCategoria','imagenes','moneda','precioDesde'] | imagenes = 3
cat 2 Doble      | claves = ['capacidad','descripcion','detalle','idCategoria','imagenes','moneda','precioDesde'] | imagenes = 3

GET /api/public/hoteles/miraflores/categorias/2 -> HTTP 200, imagenes = 3
```

Las claves son solo las de `HotelPublico`/`CategoriaPublica` (las ocho del schema más `imagenUrl` y
`imagenAlt`, que el contrato declara opcionales) y `imagenes` nunca está vacío. El tope de 10 es
efectivo (paso 5, `ev/pasos-5-9.txt`): con un fixture de 12 imágenes en la categoría 1 de
`lima-centro`, `GET .../categorias/1` devuelve exactamente **10**; cuando esa habitación pasa a
`publicada=false`, vuelve a 3 y ninguna URL del fixture aparece.

Una sede sin foto se sirve con el par nulo sin cambiar las claves (paso 9):

```
GET /api/public/hoteles/sin-foto -> HTTP 200
claves = ['ciudad','correoContacto','descripcion','direccion','idHotel','imagenAlt','imagenUrl','nombre','slug','telefono']
imagenUrl = None | imagenAlt = None
```

### Caso `CRUD01` — alta, edición y `PUT` sin `If-Match`

Comando: `bash %TEMP%/opencode/hotel-ev/crud.sh` (login de `admin@hotel.test` con la clave de demo del
`.env`, que no se imprime). Salida completa en `ev/crud01.txt`.

```
POST /api/hotel/registrar (par de imagen) -> HTTP 201, idHotel=4, version=1
  {"imagenUrl":"/assets/sedes/cusco-plaza-fachada.jpg","imagenAlt":"Fachada de piedra de la sede del Cusco",...}
GET /api/hotel/buscar/4 -> HTTP 200, ETag: "1", devuelve el mismo par
PUT /api/hotel/actualizar/4 sin If-Match -> HTTP 428
PUT /api/hotel/actualizar/4 con If-Match "1" -> HTTP 200, version=2,
  {"imagenUrl":"/assets/sedes/cusco-plaza-fachada-v2.jpg",...}
PUT /api/hotel/actualizar/4 con If-Match "1" obsoleto -> HTTP 412 PRECONDITION_FAILED
POST /api/hotel/registrar sin imagen -> HTTP 201, {"imagenUrl":null,"imagenAlt":null}
```

Validaciones del par (paso 7), todas 400 `VALIDATION_ERROR` con el campo en `error.fields`:

```
POST con imagenUrl "http://cdn.ejemplo.test/foto.jpg"
  {"code":"VALIDATION_ERROR","fields":{"imagenUrl":"url de imagen no permitida (https o /assets/)"}}
POST con imagenAlt y sin imagenUrl
  {"code":"VALIDATION_ERROR","fields":{"imagenAlt":"se rechaza una descripción sin URL de imagen"}}
POST con imagenUrl y imagenAlt "   "
  {"code":"VALIDATION_ERROR","fields":{"imagenAlt":"obligatorio cuando hay imagenUrl"}}
```

Fila persistida al final del caso:

```
 id |    slug     |                imagen_url                |                           imagen_alt                           | publicado | version
----+-------------+------------------------------------------+----------------------------------------------------------------+-----------+---------
  4 | cusco-plaza | /assets/sedes/cusco-plaza-fachada-v2.jpg | Fachada de piedra de la sede del Cusco                         | t         |       2
  5 | sin-foto    |                                          |                                                                | f         |       1
```

### `SedeSnapshot` (paso 10)

Tras el `PUT` de la sede 4, la fila de `outbox` lleva el par (salida en `ev/paso-10-outbox.txt`):

```
 aggregate_id | aggregate_version |     type     |                imagen_url                |                imagen_alt
--------------+-------------------+--------------+------------------------------------------+----------------------------------------
 4            |                 2 | SedeSnapshot | /assets/sedes/cusco-plaza-fachada-v2.jpg | Fachada de piedra de la sede del Cusco
 5            |                 1 | SedeSnapshot |                                          |
```

Los dos `payload` literales validan contra `docs/contracts/events/hotel.sede.v1.SedeSnapshot.schema.json`
con `jsonschema` (draft 2020-12, registrando también `envelope.v1.schema.json`): **0 errores** el del
sede con imagen y **0 errores** el de la sede sin imagen (`imagenUrl`/`imagenAlt` a `null`, que el
schema admite). Salida en `ev/schema-snapshot.txt`.

### Pruebas automatizadas

```
bash scripts/mvn.sh -f services/pom.xml verify
  hotel-service ...................................... SUCCESS
  ... (los nueve módulos) ... BUILD SUCCESS
  hotel-service: Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
```

Cubren lo que pide el paso 11: `HotelImagenPresentacionTests` (8 casos: par guardado y devuelto, alta
sin imagen con par nulo, URL no permitida, `alt` sin URL, URL sin `alt`, límites de 500/150, mapeo de
la entidad y reemplazo del par en la edición), `SedeSnapshotImagenTests` (2 casos: payload con par y
payload nulo, los dos validados contra el JSON Schema real) y `GaleriaTopeTests` (2 casos: 12 imágenes
devuelven 10, 3 devuelven 3).

### Validadores (paso 12)

```
python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.

python scripts/validar-planes.py
39 plan(es) revisado(s); 39 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).
```

### Comandos

`bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-contratos.py --base origin/main`
y `python scripts/validar-planes.py`.


## 7. Preguntas abiertas

Ninguna (resuelto por IA: la regla del par `imagenUrl`/`imagenAlt` se valida en `HotelService` y no
con `@Valid` en el controlador, para no ampliar la validación de los campos que cerró
`back-hotel-catalogo`; ver [`IA-back-hotel-presentacion-real-02.md`](../implementacion/decisiones/IA-back-hotel-presentacion-real-02.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Casos de la sección 6 con evidencia.
- [x] Sin preguntas abiertas.
