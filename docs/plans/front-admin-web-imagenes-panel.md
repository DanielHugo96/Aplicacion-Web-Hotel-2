# front-admin-web-imagenes-panel — visor de imágenes en las nueve pantallas y wordmark Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `front-admin-web-imagenes-panel` |
| Ámbito | `front` |
| Componente | `admin-web` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `front-admin-web-base`, `front-admin-web-catalogo`, `front-admin-web-area-cliente`, `back-hotel-presentacion-real`, `back-inventory-imagenes-producto` |
| Spec | [`08` §3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes) y [§6 Imágenes en el panel](../especificacion/08-admin-web.md#6-imágenes-en-el-panel); [`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real); [`DESIGN-admin-web.md` §Navegación por rol](../DESIGN-admin-web.md#navegación-por-rol), [§Piezas de dominio](../DESIGN-admin-web.md#piezas-de-dominio), [§Estados de pantalla](../DESIGN-admin-web.md#estados-de-pantalla) y [§Formularios](../DESIGN-admin-web.md#formularios); [`admin-web.yaml` §Hotel](../contracts/openapi/admin-web.yaml) |
| Casos | `CRUD01` |
| Change | `—` |

## 1. Objetivo

El panel muestra la imagen donde el contrato la expone —sede, habitación, producto y perfil— en lugar de enseñar solo la URL: miniatura en listados, vista previa en formularios y galería en el detalle, con `alt`, fallback neutro y el mensaje «No se pudo cargar la imagen». Además, el wordmark es Casa Sillar. Se comprueba con `npm run build`, `npm test -- --watch=false` y un recorrido contra el Gateway.

## 2. Alcance

**Entra:**

- `apps/admin-web/src/app/core/models/catalogo.models.ts`: `imagenUrl`/`imagenAlt` opcionales en `Hotel` y `HotelInput`.
- Componente compartido de imagen y visor (nuevo, en `apps/admin-web/src/app/shared/`): miniatura con relación reservada, `alt` obligatorio, `aria-hidden` para la de adorno, carga diferida y fallback con el texto «No se pudo cargar la imagen» cuando el campo es editable.
- `apps/admin-web/src/app/features/catalogo/sedes/`: miniatura en el listado y vista previa más campo `imagenAlt` en el formulario.
- `apps/admin-web/src/app/features/catalogo/habitaciones/`: miniatura en el listado, vista previa por fila de `urlsImagenes` en el formulario y vista de detalle con la galería completa.
- `apps/admin-web/src/app/features/ventas/productos/`: miniatura en el listado y vista previa en el formulario.
- `apps/admin-web/src/app/features/cliente/catalogo/`, `apps/admin-web/src/app/features/cliente/carrito/`, `apps/admin-web/src/app/features/cliente/mis-ventas/` y `apps/admin-web/src/app/features/ventas/ventas/`: miniatura del producto en cada línea, con fallback si `imagenUrl` es nula.
- `apps/admin-web/src/app/features/perfil/` y `apps/admin-web/src/app/features/cliente/mi-perfil/`: foto actual y vista previa de la nueva.
- `apps/admin-web/src/app/features/shell/shell.component.html` y `.css`: wordmark **Casa** en Marcellus y **Sillar** en Plex 600, reducido a la inicial al colapsar.
- `apps/admin-web/public/assets/`: fotografías de sede, habitación y producto con las rutas de los seeds, y el archivo de créditos.
- Pruebas de la app, este plan, su fila de catálogo y su bitácora de decisión.

**Fuera de alcance:**

- Subida o descarga de archivos: el panel solo muestra la URL que acepta el contrato ([`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel)).
- `apps/public-web/**`, `services/**` y `docs/contracts/**`.
- Validación de la URL como error de formulario más allá de lo que fija [`08` §3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes): un fallo de carga no bloquea el guardado.
- Los formularios y validaciones que ya están cerrados en `front-admin-web-catalogo` y `front-admin-web-area-cliente`: solo se les añade la imagen.

## 3. Contexto

`0c5b0a2` añade [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel): nueve pantallas donde una URL de imagen nunca se muestra sola; la sede gana `imagenUrl`/`imagenAlt` en [`08` §3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes); [`DESIGN-admin-web.md`](../DESIGN-admin-web.md) fija el wordmark de la cadena y la regla de que «donde el contrato expone una imagen, se muestra». `admin-web.yaml` ya publica los campos en `Hotel` y `HotelInput`.

El plan no decide reglas nuevas: consume los contratos y el sistema de diseño. Decide y registra en [`IA-front-admin-web-imagenes-panel-01.md`](../implementacion/decisiones/IA-front-admin-web-imagenes-panel-01.md) que el `alt` de un producto se compone con su nombre —el contrato de inventory no tiene `imagenAlt`— y que las imágenes se sirven desde `apps/admin-web/public/assets/`, con las mismas rutas y el mismo archivo de créditos que `front-public-web-presentacion-real`.

La fase es `F5` porque `F0`–`F4` están cerradas en `main`: este cambio llega después del incremento y no reabre una fase anterior.

## 4. Pasos

- [x] 1. Añadir `imagenUrl` e `imagenAlt` opcionales a `Hotel` y `HotelInput` y comprobar que los formularios existentes siguen compilando. Verificación: `npm run build` sin errores.
  - Hecho: `imagenUrl?: string | null` e `imagenAlt?: string | null` en `Hotel` y `HotelInput` de `core/models/catalogo.models.ts`, con los límites del contrato (500/150) anotados.
  - Evidencia: `npm run build` → `Application bundle generation complete` (7.014 s); el único aviso es el de presupuesto de bundle que ya existía antes del cambio (512.51 kB frente a 500 kB), no un error. `npm test -- --watch=false` sigue en verde (49 archivos, 305 pruebas) con los formularios existentes compilando.
- [x] 2. Crear el componente compartido de imagen: `src`, `alt`, relación y prioridad como entradas, `object-fit: cover`, espacio reservado, `loading="lazy"` salvo en la imagen principal, `aria-hidden` cuando es de adorno y fallback ante `error`. Verificación: prueba unitaria del `error` y de la relación.
  - Hecho: `src/app/shared/imagen-panel.component.ts` (`app-imagen-panel`) con entradas `src`, `alt`, `relacion`, `prioritaria` y `decorativa`; `object-fit: cover`, `aspect-ratio` reservado en el contenedor, esqueleto con la misma relación durante la carga, `loading`/`fetchpriority` según prioridad, `aria-hidden` cuando es de adorno, y salida `fallo` para que el formulario muestre el texto. Fallback propio en `public/assets/fallback.svg` y constante `TEXTO_IMAGEN_ROTA`.
  - Evidencia: `npx ng test --watch=false --include='**/imagen-panel.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 7 passed (7)`: fallback en `error` con la relación `3 / 2` intacta, `fallo` emitido una sola vez, fallback sin URL, `aria-hidden`/`alt` vacío en la de adorno, `lazy` frente a `eager`/`high`, esqueleto con relación `21 / 9` que desaparece en `load`, y reintento al cambiar la URL.
- [x] 3. Mostrar la miniatura de la sede en `sedes-list`, junto al nombre, con fallback cuando no hay `imagenUrl`. Verificación: prueba con sede con imagen y sin imagen; ninguna pinta un icono roto.
  - Hecho: columna «Nombre» con `app-imagen-panel` de `1 / 1` a 44 px y el nombre al lado; `alt` del contrato (`imagenAlt`) con respaldo «Fachada de la sede <nombre>» cuando falta; sin `imagenUrl`, fallback de adorno (`aria-hidden`).
  - Evidencia: `npx ng test --watch=false --include='**/sedes-list.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 3 passed (3)`: miniatura con su `alt` del contrato, fallback neutro de adorno sin `imagenUrl` y caída al fallback en `error` conservando el nombre. Ningún caso acaba con un `<img>` roto porque el `src` siempre es una URL válida o `/assets/fallback.svg`.
- [x] 4. Añadir al formulario de sede los campos `imagenUrl` y `imagenAlt`, la vista previa junto al campo y los mensajes «URL no permitida» y «Describe la imagen para accesibilidad» de [`08` §3](../especificacion/08-admin-web.md#3-formularios-campos-validación-y-mensajes). Verificación: una URL `http://` marca el campo con «URL no permitida»; una URL válida sin `alt` marca `imagenAlt`; un fallo de carga no impide guardar.
  - Hecho: `sede-form` gana `imagenUrl`/`imagenAlt` (500/150), la vista previa `3 / 2` a la derecha del campo, el contador `n/150`, y valida con la misma regla que el formulario de habitación (`/^(https:\/\/|\/assets\/)/`). El `body` envía el par o `null`; el fallo de carga nunca entra en `errores`, así que no bloquea el guardado.
  - Evidencia: `npx ng test --watch=false --include='**/sede-form.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 6 passed (6)`: URL `http://` → «URL no permitida» sin `POST`; URL válida sin `alt` → «Describe la imagen para accesibilidad» sin `POST`; URL rota → texto «No se pudo cargar la imagen» junto al campo, valor conservado y `POST` con el par intacto; sin URL → `imagenUrl`/`imagenAlt` nulos; edición → los dos campos y la vista previa se rellenan del contrato.
- [x] 5. Mostrar en `habitaciones-list` la miniatura de la primera imagen de `urlsImagenes`, con fallback si la galería está vacía. Verificación: prueba con galería de tres y con galería vacía.
  - Hecho: columna «Imagen» en `habitaciones-list` con `app-imagen-panel` de `4 / 3` a 64 px; `primeraImagen()` ordena por `orden` antes de tomar la primera (`GALERIA` de prueba llega desordenada a propósito). Sin galería, fallback de adorno. Se añade la acción «Ver detalle» que enlaza `habitaciones/:id` (paso 8).
  - Evidencia: `npx ng test --watch=false --include='**/habitaciones-imagenes.spec.ts'` → `Test Files 1 passed (1)` / `Tests 3 passed (3)`: galería de tres → `lima-centro-doble-1.jpg` (la de `orden: 1`, no la primera del array) con su `alt`, galería vacía → `/assets/fallback.svg` con `aria-hidden`, y el botón «Ver detalle» presente.
- [x] 6. Añadir la miniatura de cada fila de `urlsImagenes` en el formulario de habitación, junto a `url`, `alt` y `orden`, de forma que reordenar cambia el orden visual. Verificación: prueba que mueve una fila y compara el orden de las miniaturas.
  - Hecho: `habitacion-form` pinta una rejilla por fila con miniatura `4 / 3` de 96 px (o hueco «Sin vista previa» si la URL no es válida), `url`, `alt`, `orden` y acciones «Subir»/«Bajar»/«Quitar». Las filas se recorren con `imagenesOrdenadas()`, que ordena por `orden`, y `moverImagen()` intercambia los valores de `orden` para que el orden visual y el guardado coincidan.
  - Evidencia: `npx ng test --watch=false --include='**/habitacion-form.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 5 passed (5)`: tres miniaturas en el orden de `orden` con su `alt`, «Subir» en la segunda fila intercambia la primera y la segunda miniatura, URL inválida sin miniatura, galería vacía con «Sin imágenes».
- [x] 7. Mantener el valor escrito cuando una URL no carga: el fallback se pinta en la miniatura y el texto «No se pudo cargar la imagen» aparece junto al campo, sin borrar `url`. Verificación: prueba con una URL rota.
  - Hecho: `imagenesRotas` guarda los índices cuya vista previa falló; el `field-msg` «No se pudo cargar la imagen» se pinta junto al campo de esa fila, la miniatura cae a `/assets/fallback.svg` y el `input` conserva el valor. `alEscribirUrl()` limpia la marca al corregir la URL.
  - Evidencia: `habitacion-form.component.spec.ts` caso `una URL rota muestra el fallback, el texto junto al campo y conserva el valor` en verde: `src` = `/assets/fallback.svg`, el texto aparece y `#imagen-url-0` sigue valiendo `/assets/habitaciones/inexistente.jpg`.
- [x] 8. Añadir la vista de detalle de habitación con la galería completa en el orden guardado, relación fija, `alt` propio y la imagen principal con prioridad alta. Verificación: ruta `habitaciones/:id` enlazada desde el listado y prueba que respeta `orden`.
  - Hecho: `habitacion-detalle.component.{ts,html,css}` (`app-habitacion-detalle`) con la galería ordenada por `orden` (`galeria()`), principal `4 / 3` con `prioritaria` y tira del resto con carga diferida, `alt` de cada `urlsImagenes[].alt`, vacío explicado y `h1`/`#galeria-titulo` con el nombre accesible. Ruta `app/habitaciones/:id` en `app.routes.ts` (tras `:id/editar`, para no capturarla) y acción «Ver detalle» en el listado.
  - Evidencia: `npx ng test --watch=false --include='**/habitacion-detalle.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 6 passed (6)`: galería desordenada en origen pintada `1, 2, 3` con sus `alt`, principal `eager`/`high` y resto `lazy`/`auto`, `aspect-ratio` `4 / 3` en las tres, vacío con su frase, fallback ante `error` conservando el `alt` y `#galeria-titulo` = «Galería de Habitación 101». `habitaciones-list.component.spec.ts` sigue en verde (8 pruebas) con la acción nueva.
- [x] 9. Mostrar la miniatura del producto en `productos-list` y la vista previa en `producto-form`, con el mismo fallback. Verificación: pruebas de listado y formulario con imagen y sin ella.
  - Hecho: `productos-list` gana columna «Imagen» con `app-imagen-panel` `1 / 1` de 44 px y `altProducto()` = «Producto <nombre>» (el contrato de inventory no tiene `imagenAlt`, ver `IA-front-admin-web-imagenes-panel-01.md`); sin URL, fallback de adorno. `producto-form` cambia el `input` suelto por el bloque con vista previa `1 / 1`, el contador y el texto «No se pudo cargar la imagen» junto al campo cuando falla, sin borrar el valor ni bloquear el guardado.
  - Evidencia: `npx ng test --watch=false --include='**/productos-imagenes.spec.ts'` → `3 passed (3)`: miniatura con `alt` «Producto Agua 500ml», fallback de adorno con `imagenUrl: null` y caída al fallback en `error` conservando SKU y nombre. `npx ng test --watch=false --include='**/producto-form-imagen.spec.ts'` → `4 passed (4)`: vista previa al escribir la URL con el nombre como `alt`, hueco «Sin vista previa» con URL inválida, URL rota con el texto y el valor conservado, y edición con la vista previa del producto cargado. `producto-form.component.spec.ts` sigue en verde (5 pruebas) con el `input` nuevo.
- [x] 10. Mostrar la miniatura del producto en las líneas de catálogo del cliente, carrito, venta nueva, listado de ventas y detalle de venta, tomando `imagenUrl` del contrato de la venta y usando el nombre como `alt`. Verificación: prueba con línea con imagen y con `imagenUrl: null`.
  - Decisión de nivel 2: el `VentaDetalle` del contrato (`admin-web.yaml` y `sales.yaml`) **no publica** `imagenUrl`; se declara opcional en el modelo del front y la línea sin campo pinta el fallback neutro, que es lo que §6 fija para el valor nulo. Bitácora: [`IA-front-admin-web-imagenes-panel-02.md`](../implementacion/decisiones/IA-front-admin-web-imagenes-panel-02.md). No se toca ningún contrato ni servicio.
  - Hecho: miniatura `1 / 1` en catálogo del cliente (sustituye el `<img>` suelto y el hueco `producto-sin-imagen`), carrito, venta nueva (carrito y catálogo de la sede), detalle de venta de A/E, mis ventas y su detalle. `VentaDetalle.imagenUrl?` en `ventas.models.ts` y `cliente.models.ts`; `alt` siempre «Producto <nombre>». El listado de ventas pinta hasta tres miniaturas por venta y «+N» para el resto.
  - Evidencia: `npx ng test --watch=false --include='**/lineas-imagen.spec.ts'` → `Test Files 1 passed (1)` / `Tests 3 passed (3)`: catálogo, carrito y detalle de venta del cliente con una línea con `imagenUrl` real y otra con `imagenUrl: null` → `src` de la primera es la URL del contrato y de la segunda `/assets/fallback.svg`, con el nombre como `alt` en ambas. `npm test -- --watch=false` → `Test Files 58 passed (58)` / `Tests 345 passed (345)`, incluidas las suites previas de ventas (`ventas-list`, `venta-detalle`, `venta-nueva`) y mis ventas, sin cambios en sus aserciones.
- [ ] 11. Añadir a Mi perfil (A/E/C) la foto actual y la vista previa de la nueva, con el mensaje de error junto al campo y el valor conservado. Verificación: prueba del formulario compartido con `fotoUrl` válida, nula y rota.
- [ ] 12. Aplicar los estados de [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel): esqueleto con la misma relación durante la carga, fallback en error y galería anunciada con su nombre accesible. Verificación: prueba de estado de carga y revisión de `aria-label`/`alt` en las miniaturas.
- [ ] 13. Cambiar el wordmark del `shell` a **Casa** (Marcellus) + **Sillar** (Plex 600) y, al colapsar, a la inicial de la marca. Verificación: con la barra colapsada se lee «C» con nombre accesible; no queda «Hotel Demo» en ninguna plantilla.
- [ ] 14. Incorporar en `apps/admin-web/public/assets/` las fotografías de `/assets/sedes/`, `/assets/habitaciones/` y `/assets/productos/` con las rutas exactas de los seeds y su ficha en `CREDITOS.md`. Verificación: cada ruta devuelta por el Gateway existe en `public/assets/` y tiene ficha; ninguna es un hotlink.
- [ ] 15. Actualizar las pruebas de catálogo, ventas, cliente, perfil y shell para cubrir las miniaturas, las vistas previas y el wordmark. Verificación: `npm test -- --watch=false` en verde y ningún fixture con «Hotel Demo».
- [ ] 16. Probar contra el Gateway: alta de sede con `imagenUrl`/`imagenAlt`, listado con miniatura, producto sembrado con su imagen y perfil con vista previa. Verificación: `npm run build` y recorrido de las nueve pantallas de [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel).

## 5. Contratos afectados

- **OpenAPI:** se consume `docs/contracts/openapi/admin-web.yaml` ya publicado (`Hotel`/`HotelInput` con `imagenUrl`/`imagenAlt`, `Producto` con `imagenUrl`, `Venta` con `imagenUrl` por línea, `Persona` con `fotoUrl`); no se modifica.
- **Eventos y comandos:** ninguno.
- **Assets:** `apps/admin-web/public/assets/sedes/*.jpg`, `habitaciones/*.jpg`, `productos/*.png` y `CREDITOS.md`, con las rutas que fijan `back-hotel-presentacion-real` §5 y `back-inventory-imagenes-producto` §5.
- **Errores:** los que ya muestra el panel; `VALIDATION_ERROR` para los campos de imagen. No se añaden códigos.

## 6. Pruebas

- Caso `CRUD01`: alta y edición de una sede con el par de imagen desde el panel, y `GET` posterior; resultado esperado fila persistida con `imagenUrl`/`imagenAlt`, miniatura en el listado y `version` incrementada. Evidencia: respuestas y captura del listado en el PR.
- Recorrido de las nueve pantallas de [`08` §6](../especificacion/08-admin-web.md#6-imágenes-en-el-panel) contra el Gateway, con imagen presente, ausente y rota. Evidencia: capturas y registro de red en el PR.
- Comandos: `npm ci`, `npm run build`, `npm test -- --watch=false` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Caso y recorrido de la sección 6 con evidencia.
- [ ] Sin preguntas abiertas.
