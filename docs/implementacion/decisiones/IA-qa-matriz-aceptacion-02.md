# IA-qa-matriz-aceptacion-02 — Cómo se comprueban los casos cuando el contrato fija una forma distinta a la del plan

- **Plan:** `qa-matriz-aceptacion`
- **Paso:** 8 (LP01–LP02 y CRUD01), 11 (OUT03) y 13 (REP02)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

El plan describe los casos con una forma abreviada, y al ejecutarlos contra el contrato cerrado
aparecen diferencias de detalle que no son fallos del sistema sino del enunciado del plan:

1. **CRUD01 / producto.** El plan dice «`POST /api/producto/registrar`, `PUT
   /api/producto/actualizar/{id}` con GET y DELETE posteriores». El contrato de
   `inventory.yaml` exige `?idHotel=` en `GET`, `PUT` y `DELETE`, y el `PUT` requiere
   `productoVersion` en el cuerpo además de `If-Match`. Sin ellos la respuesta es `500` por
   parámetro faltante, no el `428` que el caso mide.
2. **CRUD01 / piso.** El contrato de `PUT /api/piso/actualizar/{id}` exige
   `{descripcion, estado}`; el plan habla solo de «ediciones». Enviar `{idHotel, descripcion}`
   da `500` (columna `estado` nula), que no es el comportamiento del sistema sino un cuerpo
   inválido.
3. **CRUD01 / habitación.** La baja (`DELETE /api/habitacion/eliminar/{id}`) es una operación
   durable (`BLOQUEO`) y exige el gate `LIBRE`; el gate lo crea reception por evento y tarda
   unos segundos tras el alta.
4. **CRUD01 / piso y categoría.** Su `DELETE` devuelve `409 ACTIVE_DEPENDENCIES` mientras la
   habitación creada siga activa; la baja de los maestros va después de la de la habitación.
5. **LP01.** La respuesta de categorías públicas no repite `idHotel`; sus claves son las de
   `CategoriaPublica` (`idCategoria`, `descripcion`, `detalle`, `capacidad`, `precioDesde`,
   `moneda`, `imagenes`), no las de `HotelPublico`.
6. **REP02.** Crear una base nueva no hereda los privilegios que
   `infra/postgres/init/01-bases-y-roles.sh` deja en las bases del Compose; sin repetirlos,
   Flyway migra pero el bootstrap falla con `bad SQL grammar`.
7. **Inbox de reporting.** El consumidor tarda en alcanzar el offset publicado; el efecto «una
   vez» debe esperarse, no leerse inmediatamente.

## Qué se decidió

Se ejecuta **la forma que fija el contrato** (`docs/contracts/openapi/**`), no la abreviatura
del plan, y la evidencia guarda el cuerpo exacto enviado. En REP02, se replican en la base nueva
los mismos `GRANT` y `ALTER DEFAULT PRIVILEGES` del init del Compose; no se toca el init ni el
servicio. En MSG01 se espera al inbox antes de evaluar el criterio.

## Por qué

La jerarquía de fuentes es **norma > plan > catálogo > registro**. El contrato OpenAPI es la
norma del comportamiento observable y el plan no puede redefinirlo (`06` §5 remite a los
contratos). Ajustar el test al contrato es la opción menos invasiva: no cambia ningún artefacto
publicado.

## Alternativas descartadas

- **Cambiar el contrato** para aceptar los cuerpos del plan: cambiaría planes ya cerrados y
  está prohibido por la regla «ampliar sí, cambiar no».
- **Aceptar el 500 como «no cumple»**: sería un falso hallazgo; el fallo estaba en el test.
- **Añadir privilegios al init del Compose**: fuera de la zona de `qa`.

## A quién afecta

`scripts/qa/matriz.py` y la evidencia de CRUD01, LP01, REP02 y MSG01. No se modifica ningún
contrato, servicio ni app.
