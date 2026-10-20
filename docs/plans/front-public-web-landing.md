# Plan `front-public-web-landing`

| Campo | Valor |
|---|---|
| Plan | `front-public-web-landing` |
| Ámbito | `front` |
| Componente | `public-web` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `en curso` |
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
- [ ] 6. Construir `/sedes` con `GET /api/public/hoteles` paginado, mostrando solo sedes publicadas y activas en tarjetas sin sombra en reposo. Verificación: con sede inactiva u oculta en el backend esta no aparece en el listado y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [ ] 7. Construir `/sedes/:slug` con `GET /api/public/hoteles/{slug}`, con nombre, ciudad, descripción y dirección demo, más sus categorías. Verificación: slug inexistente o inactivo devuelve 404 con mensaje del catálogo y enlace de vuelta al listado, y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [ ] 8. Construir categorías por sede con `GET /api/public/hoteles/{slug}/categorias` y filtro opcional de capacidad de 1 a 10, mostrando solo categorías con habitaciones publicadas. Verificación: capacidad fuera de rango se rechaza como validación y categoría sin publicadas no aparece, y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [ ] 9. Construir `/sedes/:slug/habitaciones/:idCategoria` con `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`, galería curada con relación fija y tarifa con el texto acompañante obligatorio de la spec. Verificación: el importe visible proviene de `precioDesde` del backend sin recálculo y la pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [ ] 10. Construir `/contacto` solo con enlaces externos de negocio y datos demo como texto, sin formulario SMTP ni subida de archivos. Verificación: no existe llamada de red saliente desde el formulario inexistente y el teléfono ficticio demo no es clicable salvo datos reales del equipo.
- [ ] 11. Construir `/privacidad` como borrador informativo con contenedor de texto, sin datos reales ni promesas legales, enlazando a contacto. Verificación: la página renderiza el borrador enlazado y pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [ ] 12. Añadir placeholders propios bajo `/assets/demo/` con `alt` explícito, sin hotlinks ni imágenes de terceros, con fallback neutro. Verificación: con una imagen rota el layout y la tarifa no se alteran y cada imagen expone su `alt`.
- [ ] 13. Aplicar formatos es-PE con pipes (moneda con `S/`, dos decimales y miles con coma; fechas y timestamps del sistema) y mostrar importes del backend sin recalcular. Verificación: prueba unitaria del formato de `S/ 1,234.50` y de fecha del sistema en es-PE pasa en verde.
- [ ] 14. Aplicar manejo de errores solo con el catálogo (`RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `RATE_LIMITED`, `DEPENDENCY_UNAVAILABLE`, `UNAUTHENTICATED` en rutas privadas), con mensaje del catálogo, campos resaltados, `correlationId` copiable y reintento en fallo de red sin inventar precios ni usar datos del panel como respaldo. Verificación: simulando 404, 429 y red caída la UI muestra el mensaje del catálogo con su acción esperada y nunca un precio inventado.
- [ ] 15. Verificar accesibilidad, responsive y movimiento (teclado, foco visible, etiquetas, contraste, `alt`, 390px y 1024px, `prefers-reduced-motion`) y pasar el checklist de [../DESIGN.md §11](../DESIGN.md) en cada pantalla sin textos prohibidos. Verificación: recorrido por teclado completo, contraste verificado y checklist firmado por pantalla.
- [ ] 16. Cerrar contra Gateway real en `http://localhost:8080` con `LP01` y `LP02`, ejecutar `python scripts/validar-planes.py` desde la raíz y dejar evidencia. Verificación: `LP01` con 200 en las 4 rutas y `LP02` sin dato privado en bundle, más validador con 0 errores.

## 5. Contratos afectados

- OpenAPI: `GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`, `GET /api/public/hoteles/{slug}/categorias` y `GET /api/public/hoteles/{slug}/categorias/{idCategoria}` de `contracts/openapi/public-web.yaml`; solo lectura pública sin token y con caché pública máxima de 60 s. Sin altas ni cambios de schemas.
- Eventos y comandos: ninguno; este plan no produce ni consume mensajería.
- Persistencia: ninguna; sin tablas ni migraciones Flyway.
- Errores: `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `RATE_LIMITED`, `DEPENDENCY_UNAVAILABLE` y `UNAUTHENTICATED` (este último solo como 401 de rutas privadas en la prueba de UX) de `errors.md`; la UI usa el mensaje para UI del catálogo y su acción esperada.

## 6. Pruebas

- Caso `LP01`: sin token, invocar los 4 GET públicos vía Gateway; resultado exigido: 200 en los 4 y solo claves de los modelos públicos; evidencia: respuestas HTTP guardadas y validación contra los schemas del OpenAPI.
- Caso `LP02`: inspeccionar el bundle de `public-web` y probar parámetro de fecha u ocupación; resultado exigido: parámetro ignorado o 400, sin dato privado y sin endpoint nuevo; evidencia: respuestas e inspección del bundle guardadas.
- Comandos: los de la sección «Verificación» del `README.md` raíz (creada en `infra-monorepo`) para build y pruebas de `apps/public-web`, más `python scripts/validar-planes.py` desde la raíz con 0 errores; cada aviso resuelto o justificado en el PR.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación observable.
- [ ] Casos `LP01` y `LP02` con evidencia de respuestas y de bundle.
- [ ] Sin preguntas abiertas.
- [ ] Sin reservas, pagos, disponibilidad garantizada, rutas admin o internas, mensajería, ni alcance cerrado; sin headers falsificables; solo Angular 22.2.2 y Gateway `http://localhost:8080`.
- [ ] Cada pantalla usa tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md), sin colores, tipografías, componentes ni movimientos inventados.
- [ ] `python scripts/validar-planes.py` con 0 errores desde la raíz; avisos resueltos o justificados.
- [ ] Archivo entregado en `docs/plans/front-public-web-landing.md`; rama `front-public-web-landing` desde `main` actualizado, commit `docs: agrega el plan front-public-web-landing`, push y PR a `main` sin auto-merge (revisa una persona).
