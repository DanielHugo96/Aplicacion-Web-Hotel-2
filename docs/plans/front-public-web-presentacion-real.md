# front-public-web-presentacion-real — marca Casa Sillar, fotos de sede y galería (F5)

| Campo | Valor |
|---|---|
| Plan | `front-public-web-presentacion-real` |
| Ámbito | `front` |
| Componente | `public-web` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `front-public-web-landing`, `back-hotel-catalogo`, `back-hotel-presentacion-real` |
| Spec | [`02` §Landing: sí puede](../especificacion/02-landing-y-panel.md#landing-sí-puede), [§Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real), [§Imágenes](../especificacion/02-landing-y-panel.md#imágenes), [§Textos y assets](../especificacion/02-landing-y-panel.md#textos-y-assets-public-web) y [§UX y pruebas mínimas](../especificacion/02-landing-y-panel.md#ux-y-pruebas-mínimas); [`DESIGN-public-web.md` §Navegación](../DESIGN-public-web.md#navegación), [§El hero](../DESIGN-public-web.md#el-hero), [§Fichas de sede](../DESIGN-public-web.md#fichas-de-sede), [§Fichas de categoría](../DESIGN-public-web.md#fichas-de-categoría), [§Página de detalle y galería](../DESIGN-public-web.md#página-de-detalle-y-galería), [§Estados de pantalla](../DESIGN-public-web.md#estados-de-pantalla) y [§Checklist](../DESIGN-public-web.md#checklist-antes-de-dar-por-lista-una-pantalla); [`public-web.yaml` §HotelPublico](../contracts/openapi/public-web.yaml) |
| Casos | `LP01`, `LP02` |
| Change | `—` |

## 1. Objetivo

La landing se presenta como Casa Sillar y muestra las imágenes en pantalla: la foto de cada sede en su ficha y en el detalle, la galería de cada categoría y el fallback neutro cuando falta una imagen, con los textos de [§Textos y assets](../especificacion/02-landing-y-panel.md#textos-y-assets-public-web). Se comprueba con `npm run build`, `npm test -- --watch=false` y un recorrido contra el Gateway.

## 2. Alcance

**Entra:**

- `apps/public-web/src/app/core/catalog.models.ts` y `apps/public-web/src/app/core/catalog.service.ts`: `imagenUrl`/`imagenAlt` opcionales de `HotelPublico`.
- `apps/public-web/src/app/shared/`: componente de imagen con relación fija, `alt` obligatorio, carga diferida o prioritaria y fallback neutro; se ajusta el componente existente y se retira su fallback de `/assets/demo/`.
- `apps/public-web/src/app/shared/site-header.component.ts`, `site-footer.component.ts` e `index.html`: wordmark **Casa** en Marcellus y **Sillar** en Plex 600, título y descripción de la cadena, aviso de presentación en el pie.
- `apps/public-web/src/app/pages/inicio.page.*`, `sedes.page.*`, `sede-detalle.page.*`, `categoria-detalle.page.*`, `contacto.page.*` y `privacidad.page.*`: textos de [§Textos y assets](../especificacion/02-landing-y-panel.md#textos-y-assets-public-web), foto de sede, galería de categoría y estados de imagen.
- `apps/public-web/public/assets/`: fotografías en `/assets/inicio/`, `/assets/sedes/` y `/assets/habitaciones/`, fallback en `/assets/fallback.svg` y el archivo de créditos; se retira `/assets/demo/`.
- Pruebas de la app, este plan, su fila de catálogo y su bitácora de decisión.

**Fuera de alcance:**

- `apps/admin-web/**` y `services/**`: la marca y las imágenes del panel van en `front-admin-web-imagenes-panel`; los datos, en los planes de back.
- `docs/contracts/**`, `docs/especificacion/**` y `docs/DESIGN-public-web.md`: no se modifican.
- Reserva, disponibilidad, pago, cuentas en la landing y mapas: siguen fuera del alcance cerrado.
- Los endpoints públicos: no se añaden rutas ni parámetros; `LP02` sigue exigiendo que el bundle no contenga `?fecha=` ni `?ocupacion=`.

## 3. Contexto

`0c5b0a2` convierte la landing en «presentación real» ([`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real)): marca única Casa Sillar, fuera «Hotel Demo» y «demostración» de las pantallas, foto de sede desde `HotelPublico`, galería curada por categoría, fallback neutro y créditos de cada imagen. [`DESIGN-public-web.md`](../DESIGN-public-web.md) ya fija el wordmark, la foto `3:2` de las fichas, la `21:9` del detalle y la galería `4:3` con miniaturas.

Este plan consume los contratos ya publicados en `public-web.yaml`: `HotelPublico` con `imagenUrl`/`imagenAlt` opcionales y `CategoriaPublica.imagenes[{url,alt}]`. No decide reglas nuevas; decide y registra en [`IA-front-public-web-presentacion-real-01.md`](../implementacion/decisiones/IA-front-public-web-presentacion-real-01.md) la ubicación de los assets (`apps/public-web/public/assets/`), el fallback (`/assets/fallback.svg`) y el archivo de créditos (`apps/public-web/public/assets/CREDITOS.md`), compartidos con el plan del panel.

Las rutas de las imágenes son las que fija `back-hotel-presentacion-real` §5: `/assets/sedes/<slug>-fachada.jpg` y `/assets/habitaciones/<slug>-<individual|doble>-<n>.jpg`. Una imagen sin ficha de créditos no entra.

La fase es `F5` porque `F0`–`F4` están cerradas en `main`: este cambio llega después del incremento y no reabre una fase anterior.

## 4. Pasos

- [x] 1. Añadir `imagenUrl` e `imagenAlt` opcionales al modelo `HotelPublico` de `catalog.models.ts` y comprobar que el servicio los transporta sin transformarlos. Verificación: `npm run build` compila y una prueba con un `HotelPublico` sin imagen sigue compilando.
  - Hecho: `imagenUrl?: string | null` e `imagenAlt?: string | null` en `HotelPublico`; el servicio no los transforma porque devuelve el `data` del sobre tal cual.
  - Evidencia: `npm run build` → `Application bundle generation complete` (main 280.19 kB). `npm test -- --watch=false` → `10 passed (10)` / `29 passed (29)`, incluida la nueva prueba `transporta imagenUrl e imagenAlt sin transformarlos` y los fixtures sin imagen, que siguen compilando.
- [x] 2. Ajustar el componente compartido de imagen: `src` y `alt` obligatorios, relación declarada por el contenedor, `object-fit: cover`, `loading`/`fetchpriority` según prioridad y fallback a `/assets/fallback.svg` en el evento de error, sin alterar el layout. Verificación: prueba unitaria que simula un `error` y comprueba que el `src` pasa al fallback y la relación no cambia.
  - Hecho: `shared/catalog-image.component.ts` (`app-catalog-image`) sustituye a `demo-image.component.ts`. `src`/`alt` son `input.required`, el `:host` no impone `aspect-ratio` (lo declara cada contenedor), la imagen usa `object-fit: cover` y el fallback va a `/assets/fallback.svg`. `prioritaria` decide `loading`/`fetchpriority` y añade `decoding`.
  - Evidencia: `npx ng test --watch=false --include='**/catalog-image.component.spec.ts'` → `Test Files 1 passed (1)` / `Tests 3 passed (3)`: fallback en `error`, relación `3 / 2` intacta tras el fallo y prioridad alta solo en la imagen del hero.
- [x] 3. Mover el fallback a `/assets/fallback.svg`, retirar `apps/public-web/public/assets/demo/` y actualizar todas las referencias. Verificación: el directorio `apps/public-web/public/assets/demo` ya no existe y una búsqueda de `/assets/demo` en `apps/public-web/src` no devuelve resultados.
  - Hecho: `public/assets/demo/` retirado con `git rm -r`; `shared/demo-image.component.*` sustituido por `shared/catalog-image.component.*` con `IMAGEN_FALLBACK = '/assets/fallback.svg'`; sedes, sede-detalle, categoria-detalle e inicio apuntan a las rutas nuevas.
  - Evidencia: `test -d apps/public-web/public/assets/demo` → `no existe`; `grep -rn "/assets/demo" apps/public-web/src` → sin resultados; `grep -rln "/assets/demo" apps/public-web/dist` tras `npm run build` → sin resultados.
  - Incidencia de entorno (no de código): `npx ng test --include='src/app/shared/**'` falla con `TS6054` porque la CLI intenta compilar los `.css` como raíz TS. Se usa el patrón `**/<archivo>.spec.ts`, que es el que acepta esta versión de la CLI.
- [x] 4. Incorporar las tres fotografías de fachada en `/assets/sedes/lima-centro-fachada.jpg`, `miraflores-fachada.jpg` y `arequipa-fachada.jpg`, con su entrada de autor, origen, licencia y fecha en `public/assets/CREDITOS.md`. Verificación: las tres rutas existen y cada una tiene ficha; ninguna es un enlace externo.
  - Hecho: tres JPEG locales (`lima-centro` 1800x1179, `miraflores` 1600x1200, `arequipa` 1800x1012), licencias CC BY 3.0, CC BY-SA 2.0 y CC0 1.0, con autor, obra, origen y fecha 2026-10-22 en `CREDITOS.md`.
  - Evidencia: `ls -l apps/public-web/public/assets/sedes` → las tres rutas existen; `grep -c sedes/<slug>-fachada.jpg CREDITOS.md` → 1 entrada por sede; `grep -o "https\?://..." CREDITOS.md | grep -v commons.wikimedia.org\|creativecommons.org` → ninguno (solo se citan la página de origen y la licencia, no se sirve nada en caliente).
- [x] 5. Incorporar las dieciocho fotografías de habitación en `/assets/habitaciones/` (tres por cada una de las seis habitaciones), con ficha de créditos. Verificación: las dieciocho rutas existen y están registradas; ninguna queda con la misma ruta que otra habitación.
  - Hecho: 18 JPEG en `public/assets/habitaciones/`, nombres exactos de `V6__hotel_imagenes_presentacion.sql` (`<slug>-<individual|doble>-<1..3>.jpg`), todos con ficha en `CREDITOS.md`.
  - Evidencia: `ls public/assets/habitaciones | wc -l` → `18`; `md5sum ... | sort -u | wc -l` → `18` (ninguna repetida); `grep -c "habitaciones/.*\.jpg" CREDITOS.md` → `19` (18 fichas + la mención del encabezado de rutas); script de comprobación contra la migración → `21 rutas en seeds; faltan: ninguna`.
- [x] 6. Sustituir el hero SVG por una fotografía en `/assets/inicio/hero.jpg` con `fetchpriority="high"`, banda full-bleed y el scrim de [§El hero](../DESIGN-public-web.md#el-hero). Verificación: en 1440px mide `min(72vh, 640px)` y en 390px pasa a 4:5 sin desplazamiento horizontal.
  - Hecho: `.hero` conserva `min-height: min(72vh, 640px)` en escritorio y `aspect-ratio: 4 / 5` con `max-height: 60vh` bajo 640px; la foto ocupa la banda con `object-fit: cover` y `fetchpriority="high"`; el scrim es Azul noche al 55 % con refuerzo lineal a la izquierda (vertical en móvil). Se retira el `hero__etiqueta` y se añade `overflow: hidden` al contenedor, que impide el desplazamiento horizontal.
  - Evidencia: `inicio.page.css` líneas del bloque `.hero` y del `@media (max-width: 640px)`; prueba `usa la fotografía del hero con prioridad alta y su alt propio` (`1 passed`) comprueba `src`, `fetchpriority` y `alt`. Recorrido visual a 1440x900 y 390x844 en la evidencia `LP01`.
- [x] 7. Cambiar el wordmark de la cabecera a **Casa** (Marcellus) + **Sillar** (Plex 600), con `aria-label` que nombre la cadena, y actualizar `<title>` y `meta description` de `index.html`. Verificación: la cabecera no contiene «Hotel» ni «Demostración»; la pestaña del navegador dice Casa Sillar.
  - Hecho: `site-header.component.ts` usa `<span class="marca__nombre">Casa</span>` (Marcellus) y `<span class="marca__apellido">Sillar</span>` (Plex 600), con `aria-label="Casa Sillar, inicio"`; `index.html` lleva `<title>Casa Sillar | Hoteles en Lima y Arequipa</title>` y una `meta description` con la marca; los títulos de ruta también dicen «Casa Sillar».
  - Evidencia: `grep -rn "Hotel\|Demostr" apps/public-web/src/app/shared/site-header.component.*` → sin resultados; `grep -n "<title>" apps/public-web/src/index.html` → `Casa Sillar | Hoteles en Lima y Arequipa`; `grep -n "title:" apps/public-web/src/app/app.routes.ts` → seis títulos con la marca.
- [x] 8. Actualizar el pie: nombre de la cadena, aviso de no disponibilidad, declaración de que los datos son de una presentación y enlaces a privacidad y contacto. Verificación: el pie no afirma ser un negocio en operación y conserva el aviso de que no constituye una reserva.
  - Hecho: pie en banda Azul noche con `Casa Sillar`, dos avisos («Cadena hotelera de presentación… no constituyen una reserva; la disponibilidad se consulta con recepción» y «Los datos de esta presentación son ficticios y no corresponden a una empresa real en operación») y los enlaces Sedes / Contacto / Privacidad.
  - Evidencia: `site-footer.component.ts` y `.css`; el texto contiene «no constituyen una reserva» y «no corresponden a una empresa real en operación», y no contiene «Hotel Demo», «demostración» ni ninguna afirmación de operación real.
- [x] 9. Reescribir el inicio con el `h1` «Casa Sillar en Lima y Arequipa», la propuesta de valor «Catálogo, sedes y contacto; sin reservas ni pagos en línea» y las dos acciones «Contactar» y «Ver sedes», sin etiqueta sobre el `h1` ni textos de demostración. Verificación: prueba de la página que compara el `h1` y la propuesta de valor exactos.
  - Hecho: `inicio.page.html` con `h1` único sin etiqueta previa, propuesta de valor literal, acciones `Contactar` (primaria) y `Ver sedes` (sobre foto); el ritmo editorial de `DESIGN-public-web` §Ritmo (`dl`/`dt`/`dd` de tres entradas en dos columnas, bloque tintado de aviso y cierre con enlace) sustituye a las tres tarjetas idénticas.
  - Evidencia: `npx ng test --watch=false --include='**/inicio.page.spec.ts'` → `4 passed (4)`: `h1` y propuesta exactos, `previousElementSibling` del `h1` nulo, acciones `['Contactar','Ver sedes']`, sin «demostración».
- [ ] 10. Mostrar la foto de la sede en las fichas de `sedes.page`: `imagenUrl` y `imagenAlt` del contrato, relación `3:2` fija y fallback cuando falta o falla. Verificación: prueba con imagen nula y con error de carga; ambas conservan las dimensiones y no dejan hueco gris sin explicación.
- [ ] 11. Mostrar la misma foto en la cabecera del detalle de sede, en `21:9` a sangre dentro del contenedor, bajo el nombre y la ciudad y antes de los datos y del filtro. Verificación: la imagen del detalle y la de la ficha usan la misma URL en una prueba de la página.
- [ ] 12. Usar la primera imagen de `imagenes` en las fichas de categoría, en `4:3`, con fallback si la lista viene vacía. Verificación: prueba con `imagenes: []` que muestra el fallback con la misma relación.
- [ ] 13. Construir la galería del detalle de categoría con hasta cuatro imágenes: principal `4:3` con el `alt` original y miniaturas que cambian la principal sin recargar; sin miniaturas si no hay más de una; fallback ocupando el hueco de la principal cuando no hay ninguna. Verificación: prueba de cambio de miniatura y prueba de galería vacía.
- [ ] 14. Ajustar contacto y privacidad: los datos de la cadena se muestran como texto cuando no son operables, sin `mailto:` ni `tel:` hacia el dominio ficticio, y la política declara que la cadena y sus datos son de una presentación. Verificación: el HTML renderizado no contiene `mailto:` con `casasillar.pe` ni promesas de cumplimiento legal.
- [ ] 15. Actualizar las pruebas afectadas (componente de imagen, inicio, sedes, sede-detalle, categoria-detalle, paginas-estaticas) para que usen los textos y el componente nuevos. Verificación: `npm test -- --watch=false` en verde y ningún fixture con «demostración».
- [ ] 16. Probar contra el Gateway con Compose y hotel-service arriba: `GET /api/public/hoteles` y el detalle de sede devuelven `imagenUrl` y la landing la pinta; la galería de categoría no queda vacía. Verificación: `npm run build` y recorrido de las cuatro pantallas con el `imagenUrl` visible en el DOM.

## 5. Contratos afectados

- **OpenAPI:** se consume `docs/contracts/openapi/public-web.yaml` ya publicado (`HotelPublico.imagenUrl`/`imagenAlt` y `CategoriaPublica.imagenes`); no se modifica.
- **Eventos y comandos:** ninguno.
- **Assets:** `apps/public-web/public/assets/inicio/hero.jpg`, `sedes/{lima-centro,miraflores,arequipa}-fachada.jpg`, `habitaciones/<slug>-<individual|doble>-<1..3>.jpg`, `fallback.svg` y `CREDITOS.md`.
- **Errores:** los que ya mapea la app; no se añaden códigos.

## 6. Pruebas

- Caso `LP01`: la landing contra el Gateway consume los cuatro GET públicos y pinta `imagenUrl` de cada sede y la galería de cada categoría; resultado esperado sin token, sin campos privados y sin imágenes rotas. Evidencia: salidas `curl` de los cuatro GET y captura del DOM en el PR.
- Caso `LP02`: búsqueda de `?fecha=` y `?ocupacion=` en el bundle y en las peticiones de la landing; resultado esperado ningún parámetro de disponibilidad y ningún endpoint privado. Evidencia: `grep` sobre `apps/public-web/dist` y registro de red.
- Comandos: `npm ci`, `npm run build`, `npm test -- --watch=false` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Casos de la sección 6 con evidencia.
- [ ] Sin preguntas abiertas.
