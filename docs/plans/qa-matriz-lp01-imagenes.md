# qa-matriz-lp01-imagenes — LP01 con la aserción ampliada de imagen y galería (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-matriz-lp01-imagenes` |
| Ámbito | `qa` |
| Componente | `tests` y `scripts` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `qa-matriz-aceptacion`, `back-hotel-presentacion-real`, `front-public-web-presentacion-real` |
| Spec | [`06` §5 Matriz de aceptación funcional](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional); [`hotel/api.md` §Públicas nuevas: solo lectura](../especificacion/microservicios/hotel/api.md#públicas-nuevas-solo-lectura); [`hotel/base-de-datos.md` §Verificación](../especificacion/microservicios/hotel/base-de-datos.md#verificación); [`public-web.yaml` §HotelPublico](../contracts/openapi/public-web.yaml) |
| Casos | `LP01` |
| Change | `—` |

## 1. Objetivo

`scripts/qa/matriz.py` ejecuta `LP01` con la aserción que fija la spec: las claves de `HotelPublico` admiten `imagenUrl`/`imagenAlt` opcionales, el par es coherente y cada categoría pública trae `imagenes` no vacío. Se comprueba con `python scripts/qa/matriz.py lp-crud` y su evidencia regenerada.

## 2. Alcance

**Entra:**

- `scripts/qa/matriz.py`: conjunto de claves permitidas de `HotelPublico` y comprobaciones nuevas dentro del caso `LP01`.
- `tests/evidencia/qa-matriz-aceptacion/LP01.json` y la fila `LP01` de `tests/evidencia/qa-matriz-aceptacion/INFORME.md`: evidencia regenerada.
- Este plan, su fila de catálogo y este PR.

**Fuera de alcance:**

- Casos nuevos: no se crean; la matriz sigue con los 41 casos de [§5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) y este plan solo amplía la aserción de `LP01`.
- `docs/plans/catalogo.md` §4: `LP01` ya tiene plan dueño y no se reasigna.
- `LP02`, `CRUD01` y el resto de pasos de `lp-crud`: no cambian.
- `apps/**` y `services/**`: no se tocan.

## 3. Contexto

`0c5b0a2` amplía la fila `LP01` de [`06` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional): «solo campos de HotelPublico/CategoriaPublica (assert de claves), incluidas `imagenUrl`/`imagenAlt` opcionales y `imagenes` no vacío por categoría».

El script cerrado por `qa-matriz-aceptacion` compara las claves de cada respuesta contra un conjunto cerrado (CLAVES_HOTEL_PUBLICO) que no incluye los dos campos nuevos, así que con `back-hotel-presentacion-real` mergeado `LP01` fallaría por «claves sobrantes». Este plan corrige la aserción, no amplía la matriz. No hay decisión de negocio: la spec fija el resultado esperado.

## 4. Pasos

- [x] 1. Añadir `imagenUrl` e `imagenAlt` al conjunto de claves permitidas (CLAVES_HOTEL_PUBLICO) como opcionales. Verificación: una respuesta de sede con el par no marca claves sobrantes.
- [x] 2. Comprobar la coherencia del par en cada sede del listado y del detalle: si `imagenUrl` no es nula, `imagenAlt` es una cadena no vacía; si es nula, `imagenAlt` también. Verificación: `LP01.json` incluye, por sede, si el par viaja y si es coherente, y la corrida deja `cumple: true` con las tres sedes sembradas.
- [x] 3. Comprobar que cada categoría de `GET /api/public/hoteles/{slug}/categorias` trae `imagenes` con al menos un elemento, cada uno con `url` y `alt` no vacíos, y como máximo diez. Verificación: `LP01.json` incluye `galeriaNoVacia` y `galeriaAcotada` por categoría, y las dos categorías de `lima-centro` salen con imágenes.
- [x] 4. Conservar las cuatro peticiones sin token y el formato de evidencia de `LP01`. Verificación: `tests/evidencia/qa-matriz-aceptacion/LP01.json` mantiene `endpoints`, `sinToken`, `respuestas` y `cumple`.
- [x] 5. Ejecutar la matriz con el stack real. Verificación: `python scripts/qa/matriz.py lp-crud` deja `LP01`, `LP02` y `CRUD01` en verde y `LP01.json` con `"cumple": true`.
- [x] 6. Actualizar la fila `LP01` de `INFORME.md` con la aserción ampliada y la fecha de la corrida. Verificación: la tabla del informe cita `imagenUrl`/`imagenAlt` y la galería no vacía.
- [x] 7. Ejecutar la verificación canónica. Verificación: `python scripts/validar-planes.py` con 0 errores y `python scripts/validar-contratos.py --base origin/main` sin retiradas.

## 5. Contratos afectados

- **OpenAPI:** ninguno; se valida `HotelPublico`/`CategoriaPublica` de `public-web.yaml` y `hotel.yaml`.
- **Eventos y comandos:** ninguno.
- **Persistencia:** ninguna.
- **Casos:** `LP01` conserva su fila en `catalogo.md` §4 y en `06`; no se añaden IDs.

## 6. Pruebas

- Caso `LP01`: `python scripts/qa/matriz.py lp-crud` con Compose, los servicios y `apps/public-web/dist` construido; resultado esperado 200 en los cuatro GET sin token, claves permitidas, par de imagen coherente en las tres sedes y `imagenes` no vacío y ≤ 10 por categoría. Evidencia: `tests/evidencia/qa-matriz-aceptacion/LP01.json` y `INFORME.md`.

  Ejecutado el 2026-10-22 (`ejecutadoEn` = `2026-10-22T07:52:38.025932Z`) con el Compose de
  `infra/compose.yaml` arriba (postgres, kafka, rabbitmq y mailpit `running/healthy`) y los
  servicios reales tras el Gateway `localhost:8080`. Al empezar estaban caídos
  `reception-service`, `reporting-service` y `notification-service`; se arrancó
  `reception-service` antes de la corrida (`scripts/qa/common.start_service`, readiness 200)
  porque `CRUD01` espera el `room_gate` de la habitación nueva, que crea reception al consumir el
  evento de hotel (`gate()` lee `reception_db.room_gate`). `reporting` y `notification` no
  intervienen en este grupo y siguen caídos: eso es entorno, no un fallo del plan.

  - `python scripts/qa/matriz.py lp-crud` → exit 0, `{"fallos": []}`, y `sede`, `piso`,
    `categoria`, `habitacion` y `producto` de `CRUD01` con `cumple: true`.
  - `LP01.json` con `"cumple": true`, `sinToken: true`, `clavesSobrantes: {}` y los cuatro
    endpoints en 200 (las cuatro rutas de `hotel/api.md` §Públicas nuevas, pedidas con el slug
    `lima-centro` y con el primer `idCategoria` que devuelve su listado, que en esta corrida fue
    `1`): `GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`,
    `GET /api/public/hoteles/{slug}/categorias` y
    `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`.
  - Claves del listado y del detalle de sede: `ciudad, correoContacto, descripcion, direccion,
    idHotel, imagenAlt, imagenUrl, nombre, slug, telefono` — las dos nuevas dentro del conjunto
    permitido, ninguna sobrante.
  - `imagenSede.coherente: true` con las tres sedes del listado (`lima-centro`, `miraflores`,
    `arequipa`, `viaja: true` y `coherente: true` en cada una) y el detalle
    (`lima-centro-detalle`, `coherente: true`).
  - `galeriaCategoria.completas: true`, `tope: 10`, ocho entradas todas con
    `galeriaNoVacia: true`, `galeriaAcotada: true`, `elementosCompletos: true` y
    `elementos: 3`: las dos categorías de cada una de las tres sedes más el detalle de las dos
    categorías de `lima-centro`.
- Comandos: `python scripts/qa/matriz.py lp-crud`, `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`.

  - `python scripts/validar-planes.py` → `39 plan(es) revisado(s); 39 previsto(s) en el
    catálogo; 0 error(es), 0 aviso(s).`
  - `python scripts/validar-contratos.py --base origin/main` → `Contratos: 8 OpenAPI, 20 schemas
    de evento. Regla: aditivos frente a origin/main.` / `Los contratos solo se han ampliado.
    Correcto.` (este plan no toca contratos; la comprobación es la de §6.7).

## 7. Preguntas abiertas

Ninguna (resuelto por IA: la fecha de la fila `LP01` del informe va en la celda `Resultado` y la
genera `matriz.py informe`, para no cambiar el formato de tabla que fija `qa-matriz-aceptacion`;
se conservan `LP02.json` y `CRUD01.json` regenerados por la misma corrida, ambos en verde — ver
[`IA-qa-matriz-lp01-imagenes-01`](../implementacion/decisiones/IA-qa-matriz-lp01-imagenes-01.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] `LP01` con evidencia regenerada y el resto de `lp-crud` intacto.
- [x] Sin preguntas abiertas.
