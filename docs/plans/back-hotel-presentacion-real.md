# back-hotel-presentacion-real — foto de sede, galería sembrada y datos de Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `back-hotel-presentacion-real` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
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

- [ ] 1. Crear `V6__hotel_imagenes_presentacion.sql` con `imagen_url varchar(500)` e `imagen_alt varchar(150)` nulas y `CONSTRAINT ck_hotel_imagen_alt CHECK (imagen_url IS NULL OR imagen_alt IS NOT NULL)`. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` en verde y, arrancando el servicio sobre una `hotel_db` limpia, `flyway_schema_history` registra la versión 6 y `\d hotel` muestra las dos columnas y el CHECK.
- [ ] 2. En la misma migración, `UPDATE hotel` por `slug` con los nombres, teléfonos, direcciones y correo `contacto@casasillar.pe` de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas), una descripción propia de una o dos frases por sede (≤ 300, sin afirmar disponibilidad), y `imagen_url`/`imagen_alt` con las rutas de §5. Verificación: `GET /api/public/hoteles` devuelve las tres sedes con nombre, dirección y par de imagen nuevos.
- [ ] 3. En la misma migración, actualizar `categoria.detalle` y `habitacion.detalle` de las seis habitaciones con descripciones propias sin la palabra «demostración», conservando `id`, `numero`, `precio`, `id_piso` y `publicada`. Verificación: las respuestas de `GET /api/public/hoteles/{slug}/categorias` y de `GET /api/habitacion/buscar/{id}` no contienen «demostración».
- [ ] 4. En la misma migración, sustituir las dos filas semilla de `imagen_habitacion` reconocibles por su URL `/assets/demo/…` y completar las demás con `ON CONFLICT (id_habitacion, orden) DO NOTHING`, hasta tres imágenes por habitación en `/assets/habitaciones/`, con `alt` que nombre sede y tipo y `orden` 1..3. Verificación: una consulta agrupa por `id_habitacion` y devuelve 6 filas con `count(*) >= 3`; ninguna fila queda con `alt` vacío.
- [ ] 5. Corregir `fn_catalogo_publico` para que el tope de 10 sea efectivo: agregar sobre un subconjunto ordenado por `orden` y limitado a 10, conservando los filtros `estado` y `publicada` de la habitación. Verificación: con 12 imágenes en una categoría de fixture, `GET /api/public/hoteles/{slug}/categorias` devuelve 10; con una habitación `publicada=false`, sus imágenes no aparecen.
- [ ] 6. Mapear `imagen_url`/`imagen_alt` en la entidad `Hotel` con longitud y nulabilidad. Verificación: el contexto arranca con `ddl-auto=validate` sin `Schema-validation` en el log.
- [ ] 7. Añadir `imagenUrl` (≤ 500) e `imagenAlt` (≤ 150) a `HotelInput` con la regla de URL de imagen de [`03` §Límites de campos comunes](../especificacion/03-contrato-comun.md#límites-de-campos-comunes) y la validación «viajan juntos»: URL sin `alt` y `alt` sin URL se rechazan. Verificación: `POST /api/hotel/registrar` con URL `http://` responde 400 `VALIDATION_ERROR` con `imagenUrl` en `error.fields`; con `alt` y sin URL, 400 con `imagenAlt`.
- [ ] 8. Persistir y devolver el par en `HotelService.registrar`, `actualizar` y `toView`, y en `HotelView` con `null` cuando no hay foto. Verificación: alta con el par y `GET /api/hotel/buscar/{id}` devuelve lo enviado; alta sin imagen devuelve `null`/`null`; `version` incrementa en cada edición.
- [ ] 9. Proyectar `h.imagen_url` y `h.imagen_alt` en la consulta de `CatalogoPublicoService` y en el record `HotelPublico`. Verificación: los cuatro GET públicos responden 200 sin token y el detalle de sede trae el par; una sede sin imagen lo devuelve nulo sin cambiar las claves.
- [ ] 10. Añadir `imagenUrl`/`imagenAlt` al `payload` de `SedeSnapshot` en `OutboxService.emitirSedeSnapshot`, presentes cuando la sede tiene imagen. Verificación: tras un `PUT` de sede, la fila de `outbox` contiene ambos campos y el JSON valida contra `contracts/events/hotel.sede.v1.SedeSnapshot.schema.json`.
- [ ] 11. Cubrir con pruebas la validación del par, el mapeo de la entidad, el tope de la galería y el `payload` del snapshot. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`.
- [ ] 12. Ejecutar los validadores y anotar la evidencia en §6. Verificación: `python scripts/validar-contratos.py --base origin/main` sin retiradas de contrato y `python scripts/validar-planes.py` con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** `docs/contracts/openapi/hotel.yaml` ya publica `imagenUrl`/`imagenAlt` en `HotelInput`, `Hotel` y `HotelPublico` (commit `0c5b0a2`). Este plan no modifica el contrato.
- **Eventos y comandos:** `hotel.sede.v1` / `SedeSnapshot` amplía su `payload` con `imagenUrl` e `imagenAlt` opcionales, ya declarados en `docs/contracts/events/hotel.sede.v1.SedeSnapshot.schema.json`. No hay topics, comandos ni grupos nuevos; `catalogo.md` §2 no cambia.
- **Persistencia:** `services/hotel-service/src/main/resources/db/migration/V6__hotel_imagenes_presentacion.sql` con las columnas, el CHECK `ck_hotel_imagen_alt` y la corrección de seeds. No se edita `V4__seeds_hotel.sql`.
- **Datos semilla:** sedes, categorías, habitaciones e imágenes de habitación de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas). Los slugs `lima-centro`, `miraflores` y `arequipa` no cambian.
- **Rutas de imagen que fija este plan:** `/assets/sedes/lima-centro-fachada.jpg`, `/assets/sedes/miraflores-fachada.jpg`, `/assets/sedes/arequipa-fachada.jpg`; `/assets/habitaciones/<slug>-<individual|doble>-<1|2|3>.jpg` para las seis habitaciones.
- **Errores:** `VALIDATION_ERROR` (400) para URL no permitida o `alt` ausente.

## 6. Pruebas

- Caso `LP01`: `GET /api/public/hoteles`, `/api/public/hoteles/{slug}`, `/api/public/hoteles/{slug}/categorias` y `/api/public/hoteles/{slug}/categorias/{idCategoria}` sin token; resultado esperado 200 en los cuatro, claves solo de `HotelPublico`/`CategoriaPublica`, `imagenUrl` e `imagenAlt` presentes en las tres sedes y `imagenes` no vacío y con 10 como máximo por categoría. Evidencia: salidas `curl` y consultas SQL en el PR.
- Caso `CRUD01`: alta y edición de una sede con el par de imagen, `GET` posterior y `PUT` sin `If-Match`; resultado esperado filas persistidas, `version` incrementada y 428. Evidencia: respuestas y fila de `hotel` en el PR.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-contratos.py --base origin/main` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Casos de la sección 6 con evidencia.
- [ ] Sin preguntas abiertas.
