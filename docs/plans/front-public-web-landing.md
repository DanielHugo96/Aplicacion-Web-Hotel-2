# Plan `front-public-web-landing`

| Campo | Valor |
|---|---|
| Plan | `front-public-web-landing` |
| Ámbito | `front` |
| Componente | `public-web` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-catalogo` |
| Spec | [02-landing-y-panel.md § Landing: sí puede](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § Landing: no puede](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § Textos y assets](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § UX y pruebas mínimas](../especificacion/02-landing-y-panel.md) · [03-contrato-comun.md § Transporte, notación y tipos](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Respuestas](../especificacion/03-contrato-comun.md) · [errors.md § Genéricos](../contracts/errors.md) · [errors.md § Cómo lo usa el frontend](../contracts/errors.md) · [../DESIGN.md §10 Tokens](../DESIGN.md) · [../DESIGN.md §11 Checklist](../DESIGN.md) · [../DESIGN.md §5 Principios de layout](../DESIGN.md) · [../DESIGN.md §8 Contenido y voz](../DESIGN.md) · [contracts/README.md §1 Reglas transversales](../contracts/README.md) · [contracts/README.md §2 Landing public-web](../contracts/README.md) · [public-web.yaml — 4 rutas públicas](../contracts/openapi/public-web.yaml) · [hotel/api.md § Públicas nuevas](../especificacion/microservicios/hotel/api.md) · [06-migracion-y-pruebas.md §4 Cambios frontend](../especificacion/06-migracion-y-pruebas.md) · [06-migracion-y-pruebas.md §5 LP01–LP02](../especificacion/06-migracion-y-pruebas.md) |
| Casos | `LP01, LP02` |
| Change | `—` |
| Contenido | landing: inicio, sedes, detalle de sede, tipos de habitación, contacto, privacidad, textos y assets demo, tarifa «Desde S/ 70.00», locale es-PE. |

> Nota: este plan sigue la norma vigente de `docs/plans/README.md` (§§4–5, §7–§8), la estructura de `docs/plans/_plantilla.md` y su fila de `docs/plans/catalogo.md` (§§1–4). No toca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 1. Objetivo

Publicar `apps/public-web` como landing informativa en español es-PE que consume solo el Gateway (`http://localhost:8080`) con las 4 rutas públicas del catálogo, con textos, tarifa con disclaimer, assets demo y formatos fijados por la spec.

Es verificable cuando: sin token los 4 GET públicos devuelven 200 con los schemas públicos (`LP01`); el bundle no expone consulta por fecha, ocupación ni dato privado (`LP02`); y cada pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).

## 2. Alcance

**Entra:**

- Rutas Angular (no son endpoints backend): `/`, `/sedes`, `/sedes/:slug`, `/sedes/:slug/habitaciones/:idCategoria`, `/contacto`, `/privacidad`.
- Lectura pública vía Gateway: `GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`, `GET /api/public/hoteles/{slug}/categorias`, `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`.
- Pantallas: inicio con hero, listado de sedes, detalle de sede, tipos de habitación por sede, detalle de tipo/categoría con galería curada y tarifa con disclaimer, contacto demo y privacidad borrador.
- Textos y assets demo de la spec, tarifa con texto acompañante obligatorio, locale es-PE con `registerLocaleData`, pipes de moneda y fecha, caché pública máxima 60 s solo para contenido público.
- Sistema visual con los tokens de [../DESIGN.md §10](../DESIGN.md) y cierre por pantalla con el checklist de [../DESIGN.md §11](../DESIGN.md).

**Fuera de alcance:**

- Reservas, check-in/check-out, cobros, pagos, comprobantes, disponibilidad garantizada o afirmación de disponibilidad en vivo; la landing es informativa y no garantiza disponibilidad para fechas.
- Crear, editar o eliminar personas, sedes, productos, ventas o habitaciones; cualquier `POST`, `PUT` o `DELETE`; rutas administrativas o internas; credenciales incrustadas; subida de archivos; formulario que envíe correo o abra endpoint SMTP público.
- Datos operativos: huéspedes, documentos, correos privados, estadías, consumos, stock, ingresos, reportes, números de habitación, ocupantes, limpieza o lista operativa.
- Mensajería: este plan no produce ni consume topics, comandos ni grupos; el catálogo no le asigna ninguno.
- Alcance cerrado: sin pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.
- Versiones distintas de la fijada para este front: solo Angular 22.2.2; sin `latest`.
- Identidad por cabeceras falsificables: nunca `X-User-Id`, `X-Role` ni `X-Hotel-Id`; la landing pública opera sin login y cualquier identidad futura solo iría por JWT validado según contrato común.

## 3. Contexto

La spec ya fija el qué; este plan solo ordena el cómo:

- Qué muestra y qué no la landing, sus 6 rutas Angular y sus prohibiciones, según [02-landing-y-panel.md § Landing: sí puede](../especificacion/02-landing-y-panel.md) y [§ Landing: no puede](../especificacion/02-landing-y-panel.md).
- Formatos obligatorios de moneda, fecha y etiquetas, según [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md).
- Textos obligatorios de hero, sedes, tarifa con disclaimer, contacto demo, privacidad e imágenes en `/assets/demo/`, según [02-landing-y-panel.md § Textos y assets](../especificacion/02-landing-y-panel.md).
- Mínimos de UX (público 200 en lo público y 401 en lo privado, catálogo independiente de reception, ocultos que no aparecen y detalle a 404, sin botones de reservar o pagar, error de red con reintento, responsive y accesibilidad, solo Gateway, caché 60 s), según [02-landing-y-panel.md § UX y pruebas mínimas](../especificacion/02-landing-y-panel.md).
- Base externa local, paginación de listas, envelope con `success`, `message`, `data` y `meta`, y prohibición de headers falsificables, según [03-contrato-comun.md § Transporte](../especificacion/03-contrato-comun.md), [§ Headers](../especificacion/03-contrato-comun.md) y [§ Respuestas](../especificacion/03-contrato-comun.md).
- Las 4 rutas, sus queries, sus modelos públicos y su caché, según [hotel/api.md § Públicas nuevas](../especificacion/microservicios/hotel/api.md), [contracts/README.md §2](../contracts/README.md) y [public-web.yaml](../contracts/openapi/public-web.yaml); base y reglas comunes según [contracts/README.md §1](../contracts/README.md).
- Mensajes y acciones desde el catálogo único, sin redactar propios, según [errors.md § Genéricos](../contracts/errors.md) y [§ Cómo lo usa el frontend](../contracts/errors.md).
- Visual y voz con tokens, layout de landing, contenido y accesibilidad, según [../DESIGN.md §10](../DESIGN.md), [§11](../DESIGN.md), [§5](../DESIGN.md) y [§8](../DESIGN.md); ante conflicto, manda la spec.
- Cambios frontend aplicables (base Gateway, LP solo rutas públicas, sin campos calculados, 202 como pendiente, ETag, quitar social y reserva futura) y casos de este plan, según [06-migracion-y-pruebas.md §4](../especificacion/06-migracion-y-pruebas.md) y [§5](../especificacion/06-migracion-y-pruebas.md).
- Dependencia: `back-hotel-catalogo` provee el catálogo público, sus seeds de demostración y sus reglas de publicado y activo; este plan no implementa backend y no avanza sin ese contrato estable o sus mocks OpenAPI.

Decisiones de este plan (no son reglas nuevas): avanzar contra los mocks de `public-web.yaml` y cerrar contra Gateway real; textos en la app con i18n preparado para es-PE; assets propios bajo `/assets/demo/`; URL del enlace de acceso privado configurable por entorno sin embeber tokens. Este plan no decide reglas que la spec no fija.

## 4. Pasos

- [x] 1. Crear el esqueleto `apps/public-web` con Angular 22.2.2, las 6 rutas Angular (`/`, `/sedes`, `/sedes/:slug`, `/sedes/:slug/habitaciones/:idCategoria`, `/contacto`, `/privacidad`), locale es-PE con `registerLocaleData` y base Gateway configurable a `http://localhost:8080`. Verificación: existe el enrutador con las 6 rutas, el locale es-PE registrado, y el build de la app termina con código 0.
- [x] 2. Aplicar los tokens de [../DESIGN.md §10](../DESIGN.md) en los estilos globales (paleta piedra y marca, tipografías display, UI y mono, espaciado base 4px, radios y sombras del sistema), sin inventar colores, tipografías, componentes ni movimientos. Verificación: inspección muestra solo tokens del sistema y el build termina con código 0.
- [x] 3. Configurar el cliente HTTP contra el Gateway con `Accept: application/json`, correlación opcional propagada y devuelta, respeto a caché pública máxima de 60 s solo para contenido público, sin secretos en el bundle y sin apuntar a puertos de servicios. Verificación: el código solo referencia la base del Gateway y la inspección del bundle no contiene URLs de servicios, brokers, bases ni secretos.
- [x] 4. Implementar el servicio de catálogo con `GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`, `GET /api/public/hoteles/{slug}/categorias` y `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`, envelope con `data` y `meta` de paginación, y avance con mocks del OpenAPI. Verificación: contra mocks las 4 llamadas devuelven 200 con la forma del contrato y la paginación usa `page` y `size` dentro del máximo permitido.
- [x] 5. Construir inicio `/` con hero, propuesta de valor y llamada a contacto según textos de la spec, con layout de landing de [../DESIGN.md §5](../DESIGN.md) y tokens de §10. Verificación: la pantalla muestra los textos fijados, no muestra reservar, pagar ni disponibilidad, y pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 6. Construir `/sedes` con `GET /api/public/hoteles` paginado, mostrando solo sedes publicadas y activas en tarjetas sin sombra en reposo. Verificación: con sede inactiva u oculta en el backend esta no aparece en el listado y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 7. Construir `/sedes/:slug` con `GET /api/public/hoteles/{slug}`, con nombre, ciudad, descripción y dirección demo, más sus categorías. Verificación: slug inexistente o inactivo devuelve 404 con mensaje del catálogo y enlace de vuelta al listado, y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 8. Construir categorías por sede con `GET /api/public/hoteles/{slug}/categorias` y filtro opcional de capacidad de 1 a 10, mostrando solo categorías con habitaciones publicadas. Verificación: capacidad fuera de rango se rechaza como validación y categoría sin publicadas no aparece, y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 9. Construir `/sedes/:slug/habitaciones/:idCategoria` con `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`, galería curada con relación fija y tarifa con el texto acompañante obligatorio de la spec. Verificación: el importe visible proviene de `precioDesde` del backend sin recálculo y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 10. Construir `/contacto` solo con enlaces externos de negocio y datos demo como texto, sin formulario SMTP ni subida de archivos. Verificación: no existe llamada de red saliente desde el formulario inexistente y el teléfono ficticio demo no es clicable salvo datos reales del equipo.
- [x] 11. Construir `/privacidad` como borrador informativo con contenedor de texto, sin datos reales ni promesas legales, enlazando a contacto. Verificación: la página renderiza el borrador enlazado y pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 12. Añadir placeholders propios bajo `/assets/demo/` con `alt` explícito, sin hotlinks ni imágenes de terceros, con fallback neutro. Verificación: con una imagen rota el layout y la tarifa no se alteran y cada imagen expone su `alt`.
- [x] 13. Aplicar formatos es-PE con pipes (moneda con `S/`, dos decimales y miles con coma; fechas y timestamps del sistema) y mostrar importes del backend sin recalcular. Verificación: prueba unitaria del formato de `S/ 1,234.50` y de fecha del sistema en es-PE pasa en verde.
- [x] 14. Aplicar manejo de errores solo con el catálogo (`RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `RATE_LIMITED`, `DEPENDENCY_UNAVAILABLE`, `UNAUTHENTICATED` en rutas privadas), con mensaje del catálogo, campos resaltados, `correlationId` copiable y reintento en fallo de red sin inventar precios ni usar datos del panel como respaldo. Verificación: simulando 404, 429 y red caída la UI muestra el mensaje del catálogo con su acción esperada y nunca un precio inventado.
- [x] 15. Verificar accesibilidad, responsive y movimiento (teclado, foco visible, etiquetas, contraste, `alt`, 390px y 1024px, `prefers-reduced-motion`) y pasar el checklist de [../DESIGN.md §11](../DESIGN.md) en cada pantalla sin textos prohibidos. Verificación: recorrido por teclado completo, contraste verificado y checklist firmado por pantalla.
- [x] 16. Cerrar contra Gateway real en `http://localhost:8080` con `LP01` y `LP02`, ejecutar `python scripts/validar-planes.py` desde la raíz y dejar evidencia. Verificación: `LP01` con 200 en las 4 rutas y `LP02` sin dato privado en bundle, más validador con 0 errores.

## 5. Contratos afectados

- OpenAPI: `GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`, `GET /api/public/hoteles/{slug}/categorias` y `GET /api/public/hoteles/{slug}/categorias/{idCategoria}` de `contracts/openapi/public-web.yaml`; solo lectura pública sin token y con caché pública máxima de 60 s. Sin altas ni cambios de schemas.
- Eventos y comandos: ninguno; este plan no produce ni consume mensajería.
- Persistencia: ninguna; sin tablas ni migraciones Flyway.
- Errores: `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `RATE_LIMITED`, `DEPENDENCY_UNAVAILABLE` y `UNAUTHENTICATED` (este último solo como 401 de rutas privadas en la prueba de UX) de `errors.md`; la UI usa el mensaje para UI del catálogo y su acción esperada.

## 6. Pruebas

Cada caso anota el comando o endpoint exacto, el resultado obtenido y dónde está la salida. La
ejecución se hizo el 2026-10-20 contra el Gateway real en `http://localhost:8080`, con
`hotel-service` en `8082` y PostgreSQL de `infra/compose.yaml`.

### 6.1 Comandos canónicos de `apps/public-web`

```text
$ cd apps/public-web && npm ci
npm ci exit=0
found 0 vulnerabilities

$ npm run build
Application bundle generation complete. [3.189 seconds] - 2026-10-20T01:23:48.160Z
Output location: D:\...\apps\public-web\dist\public-web
build exit=0

$ npm test -- --watch=false
Test Files  10 passed (10)
Tests       28 passed (28)
Duration    7.13s
test exit=0

$ python scripts/validar-planes.py
27 plan(es) revisado(s); 27 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).
planes exit=0

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
contratos exit=0
```

### 6.2 Caso `LP01` — 4 GET públicos vía Gateway, sin token

Comando: script Python con `urllib.request` contra `http://localhost:8080`, sin cabecera
`Authorization`, que valida status, envelope, `Cache-Control`, `X-Correlation-Id` y el conjunto
exacto de claves de `HotelPublico`, `CategoriaPublica` e `ImagenPublica`. Cuerpos guardados en
`C:\Users\juan2\AppData\Local\Temp\lp01-*.json`.

```text
hoteles: HTTP 200; Cache-Control=public,max-age=60; X-Correlation-Id=192df30a-ab5f-46b7-ba91-e6fdaac468c8; claves OK; registros=3; guardado=...\lp01-hoteles.json
hotel: HTTP 200; Cache-Control=public,max-age=60; X-Correlation-Id=d63873d7-a360-4a0a-ba56-68f62c8694fb; claves OK; registros=1; guardado=...\lp01-hotel.json
categorias: HTTP 200; Cache-Control=public,max-age=60; X-Correlation-Id=cc3c3f97-4e46-401b-986e-146acdda5145; claves OK; registros=2; guardado=...\lp01-categorias.json
categoria: HTTP 200; Cache-Control=public,max-age=60; X-Correlation-Id=5042ed8f-99f6-4733-84c6-d2c8fa8c09fa; claves OK; registros=1; guardado=...\lp01-categoria.json
LP01 OK: 4/4 rutas 200 y solo claves publicas
```

Rutas del contrato ejercitadas con `slug=lima-centro` e `idCategoria=1`:
`GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`,
`GET /api/public/hoteles/{slug}/categorias` y
`GET /api/public/hoteles/{slug}/categorias/{idCategoria}`. Las claves aceptadas fueron exactamente
`{idHotel,slug,nombre,descripcion,direccion,ciudad,telefono,correoContacto}` para sede y
`{idCategoria,descripcion,detalle,capacidad,precioDesde,moneda,imagenes}` con imágenes
`{url,alt}` para categoría; cualquier clave extra habría hecho fallar el script.

### 6.3 Caso `LP02` — bundle sin datos privados y fecha/ocupación ignoradas

Peticiones reales al Gateway con parámetros fuera del contrato:

```text
hoteles+fecha+ocupacion: HTTP 200; {"success":true,"message":"OK","data":[{...HotelPublico...}]}
categorias+fecha+ocupacion: HTTP 200; {"success":true,"message":"OK","data":[{...CategoriaPublica...}]}
```

Los parámetros `fecha`, `ocupacion` y `disponible` se ignoran (200 con el catálogo público), no
crean endpoint nuevo ni alteran el schema. Inspección del bundle de producción
(`apps/public-web/dist/public-web/browser`):

```text
--- endpoints publicos referenciados ---
/api/public/
/api/public/hoteles
/api/public/hoteles/

fecha=: SIN HALLAZGOS
ocupacion: SIN HALLAZGOS
endpoint privado: SIN HALLAZGOS
clave privada: SIN HALLAZGOS
token/localStorage: SIN HALLAZGOS

--- URLs en bundle ---
http://localhost:4300        (enlace configurable al panel)
http://localhost:8080        (Gateway)
(puertos de servicios, brokers y bases: SIN HALLAZGOS)
```

Observación fuera de alcance: `GET /categorias?capacidad=99` respondió `404 RESOURCE_NOT_FOUND`
en el backend; la landing valida `1..10` antes de llamar y muestra `VALIDATION_ERROR`, por lo que
esa petición no se emite desde la UI (paso 8).

### 6.4 Evidencia por paso

- **1–2.** `npm run build` exit 0; `app.routes.ts` declara las 6 rutas, `main.ts` registra
  `localeEsPe` y `styles.css` contiene los tokens de DESIGN §10 (`--color-*`, `--font-*`,
  `--space-*`, `--radius-*`, `--shadow-*`, `--dur-*`). Inspección del bundle: solo
  `http://localhost:8080` y `http://localhost:4300` como URLs de la app; ningún puerto de
  servicio.
- **3.** Interceptores `correlationInterceptor` (genera/propaga `X-Correlation-Id` y `Accept`) y
  `publicCacheInterceptor` (solo `GET` bajo `/api/public/`, TTL 60 s). Inspección final del bundle:
  `puertos de servicios: SIN HALLAZGOS`, `token/localStorage: SIN HALLAZGOS`.
- **4.** `CatalogService` con las 4 rutas y `page`/`size` (máximo 100) y `capacidad` 1..10.
  `catalog.service.spec.ts` (6 pruebas) cubre URL, parámetros, 200 con envelope, rechazo de
  capacidad 11 sin llamar al backend y 404 del catálogo con `correlationId`.
- **5.** `inicio.page.spec.ts` verifica los textos obligatorios del hero, la llamada a contacto y
  que no haya acciones de reservar/pagar ni “disponible ahora”. Captura real del árbol de
  accesibilidad en `http://localhost:4200/`: hero, CTA y footer con
  “Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.”
- **6.** `sedes.page.spec.ts` verifica tarjetas y paginación (`Página 1 de 1 · 2 registros`).
  Contra el backend real se insertó temporalmente `hotel-oculto` (`estado=false`) y la respuesta
  siguió siendo `slugs: ['lima-centro','miraflores','arequipa']; totalElements: 3`; la UI mostró
  `cards: 3; hiddenPresent: false`. La fila se eliminó después (`DELETE 1`) y el catálogo volvió a
  `totalElements: 3`.
- **7.** `sede-detalle.page.spec.ts` verifica sede + categorías y que un 404 real muestre
  “No encontramos lo que buscas.” con enlace `Volver al listado de sedes`. En navegador real,
  `http://localhost:4200/sedes/no-existe` renderizó el mensaje del catálogo, el `correlationId`
  `69fe60ac-e053-4aa9-a0e4-c2b3b45de533` y el enlace a `/sedes`.
- **8.** Filtro real de capacidad: `2` devolvió `categories: 1; names: ['Doble']; price: "Desde
  S/ 100.00 por noche."`. `11` mostró
  `"Revisa los datos marcados. La capacidad debe estar entre 1 y 10."`, `aria-invalid="true"` y no
  emitió la petición. Se insertó temporalmente la categoría `Suite sin publicar` sin habitaciones
  publicadas y el listado siguió siendo `['Individual','Doble']`; se eliminó después.
- **9.** `categoria-detalle.page.spec.ts` verifica galería, `alt`, capacidad, tarifa
  `S/ 70.00` y disclaimer. En navegador real el detalle de `lima-centro/habitaciones/1` mostró
  `price: "Desde S/ 70.00 por noche."` proveniente de `precioDesde: 70.0` del backend, sin
  recálculo.
- **10–11.** `paginas-estaticas.spec.ts` verifica `forms: 0`, ningún `a[href^="tel:"]`, el
  `mailto:contacto@hotel.test` y el teléfono demo como texto. En navegador real:
  `{"forms":0,"telLinks":0,"mailto":"mailto:contacto@hotel.test"}`. Privacidad renderiza el
  borrador y enlaza a `/contacto`.
- **12.** `demo-image.component.spec.ts` simula `error` de imagen y verifica que el `src` cambia a
  `/assets/demo/placeholder.svg`, el `alt` se conserva y se aplica `imagen--fallback`; la tarifa
  queda fuera del componente y no se toca. Assets propios: `hero.svg`, `sede.svg`,
  `placeholder.svg`, `hab-101-1.jpg` y `hab-102-1.jpg`.
- **13.** `formatos.spec.ts`: `1234.5 → "S/ 1,234.50"`, `70 → "S/ 70.00"`,
  `new Date(2026,9,4,15,30) → "04/10/2026"` y `"04/10/2026 15:30"`. La implementación usa
  `MonedaPePipe` sobre `CurrencyPipe('es-PE')` porque ICU inserta un espacio duro; se normaliza a
  espacio simple para cumplir el texto exacto de la spec. El footer usa `DatePipe` con la fecha
  del sistema.
- **14.** `api-error.spec.ts` y `sedes.page.spec.ts` cubren `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`,
  `RATE_LIMITED` (429 con `corr-429`), red caída (`No se pudo conectar con el servicio...` con
  `Reintentar`) y `DEPENDENCY_UNAVAILABLE` (503). En error no se renderiza ninguna tarjeta ni
  importe (`expect(textContent).not.toContain('S/')`).
- **15.** Ver checklist de 6.5.
- **16.** `LP01`, `LP02`, `validar-planes.py` y `validar-contratos.py` anteriores, ejecutados con
  el Gateway y `hotel-service` reales.

### 6.5 Checklist de DESIGN §11 por pantalla

Verificado en Chrome con el árbol de accesibilidad y `Lighthouse` (Accessibility 100, Best
Practices 100, SEO 100) sobre `/` y `/sedes`; el resto de pantallas se recorrió en 390 y 1024 px.

| Pantalla | Formatos es-PE | Estados vacío/carga/error | Sin textos prohibidos | Accesibilidad/alt | 390/1024 sin scroll horizontal |
|---|---|---|---|---|---|
| `/` | Sí (tarifa `S/ 70.00`, fecha del footer) | Estática; sin estados de datos | Sí (`actionsConReservar: []`, `disponibleAhora: false`) | H1, landmarks, 0 imágenes sin `alt` | Sí |
| `/sedes` | Sí (importes de tarjetas) | Skeleton, vacío `El catálogo está vacío.`, 429/red con Reintentar | Sí | 3 tarjetas con `alt` de sede | Sí |
| `/sedes/:slug` | Sí (`S/ 70.00`, `S/ 100.00`) | Skeleton, 404 con enlace, error de categorías con Reintentar | Sí | Formulario con label/`aria-describedby`, imágenes con `alt` | Sí |
| `/sedes/:slug/habitaciones/:idCategoria` | Sí (`S/ 70.00`) | Skeleton, 404 con enlace | Sí | Galería con `alt`, H1, región de galería | Sí |
| `/contacto` | Fecha del footer | Sin estados de datos | Sí | Sin formulario, teléfono como texto, mailto | Sí |
| `/privacidad` | Fecha del footer | Sin estados de datos | Sí | H1, enlace a contacto | Sí |

Recorrido de teclado en `/` (10 paradas, todas con `outline: solid 2px`): `Saltar al contenido`,
wordmark, `Sedes`, `Contacto`, `Privacidad`, `Acceso al panel`, `Contactar`, `Ver sedes`,
`Ver el listado de sedes`, `Explorar categorías`. Contraste calculado sobre los tokens:
tinta/sillar-blanco 14.60:1, garúa/sillar-blanco 5.03:1, cochinilla/sillar-blanco 8.47:1,
blanco/cochinilla 9.07:1, texto claro/azul noche 12.54:1 (todos ≥ AA 4.5:1). `prefers-reduced-motion`
está implementado en `src/styles.css:288` y desactiva animaciones y transiciones.

## 7. Preguntas abiertas

Ninguna (resuelto por IA: puerto de desarrollo de `public-web` en `4200` y enlace al panel por defecto en `4300`, ambos configurables; ver `docs/implementacion/decisiones/IA-front-public-web-landing-01.md`).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación observable.
- [x] Casos `LP01` y `LP02` con evidencia de respuestas y de bundle.
- [x] Sin preguntas abiertas.
- [x] Sin reservas, pagos, disponibilidad garantizada, rutas admin o internas, mensajería, ni alcance cerrado; sin headers falsificables; solo Angular 22.2.2 y Gateway `http://localhost:8080`.
- [x] Cada pantalla usa tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md), sin colores, tipografías, componentes ni movimientos inventados.
- [x] `python scripts/validar-planes.py` con 0 errores desde la raíz; avisos resueltos o justificados.
- [x] Archivo entregado en `docs/plans/front-public-web-landing.md`; rama `front-public-web-landing` desde `main` actualizado, commits de implementación y plan `cerrado` listos para el PR a `main` (sin auto-merge).
