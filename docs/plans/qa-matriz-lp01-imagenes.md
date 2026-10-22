# qa-matriz-lp01-imagenes — LP01 con la aserción ampliada de imagen y galería (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-matriz-lp01-imagenes` |
| Ámbito | `qa` |
| Componente | `tests` y `scripts` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `propuesto` |
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

- [ ] 1. Añadir `imagenUrl` e `imagenAlt` al conjunto de claves permitidas (CLAVES_HOTEL_PUBLICO) como opcionales. Verificación: una respuesta de sede con el par no marca claves sobrantes.
- [ ] 2. Comprobar la coherencia del par en cada sede del listado y del detalle: si `imagenUrl` no es nula, `imagenAlt` es una cadena no vacía; si es nula, `imagenAlt` también. Verificación: `LP01.json` incluye, por sede, si el par viaja y si es coherente, y la corrida deja `cumple: true` con las tres sedes sembradas.
- [ ] 3. Comprobar que cada categoría de `GET /api/public/hoteles/{slug}/categorias` trae `imagenes` con al menos un elemento, cada uno con `url` y `alt` no vacíos, y como máximo diez. Verificación: `LP01.json` incluye `galeriaNoVacia` y `galeriaAcotada` por categoría, y las dos categorías de `lima-centro` salen con imágenes.
- [ ] 4. Conservar las cuatro peticiones sin token y el formato de evidencia de `LP01`. Verificación: `tests/evidencia/qa-matriz-aceptacion/LP01.json` mantiene `endpoints`, `sinToken`, `respuestas` y `cumple`.
- [ ] 5. Ejecutar la matriz con el stack real. Verificación: `python scripts/qa/matriz.py lp-crud` deja `LP01`, `LP02` y `CRUD01` en verde y `LP01.json` con `"cumple": true`.
- [ ] 6. Actualizar la fila `LP01` de `INFORME.md` con la aserción ampliada y la fecha de la corrida. Verificación: la tabla del informe cita `imagenUrl`/`imagenAlt` y la galería no vacía.
- [ ] 7. Ejecutar la verificación canónica. Verificación: `python scripts/validar-planes.py` con 0 errores y `python scripts/validar-contratos.py --base origin/main` sin retiradas.

## 5. Contratos afectados

- **OpenAPI:** ninguno; se valida `HotelPublico`/`CategoriaPublica` de `public-web.yaml` y `hotel.yaml`.
- **Eventos y comandos:** ninguno.
- **Persistencia:** ninguna.
- **Casos:** `LP01` conserva su fila en `catalogo.md` §4 y en `06`; no se añaden IDs.

## 6. Pruebas

- Caso `LP01`: `python scripts/qa/matriz.py lp-crud` con Compose, los servicios y `apps/public-web/dist` construido; resultado esperado 200 en los cuatro GET sin token, claves permitidas, par de imagen coherente en las tres sedes y `imagenes` no vacío y ≤ 10 por categoría. Evidencia: `tests/evidencia/qa-matriz-aceptacion/LP01.json` y `INFORME.md`.
- Comandos: `python scripts/qa/matriz.py lp-crud`, `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] `LP01` con evidencia regenerada y el resto de `lp-crud` intacto.
- [ ] Sin preguntas abiertas.
