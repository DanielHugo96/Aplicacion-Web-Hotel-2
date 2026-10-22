# IA-back-identity-presentacion-real-02 — el renombrado no toca `version` ni `updated_at`

- **Plan:** `back-identity-presentacion-real`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

El `UPDATE` que corrige el nombre de las filas ya sembradas es una escritura sobre `persona`, y la
tabla tiene `version` y `updated_at` que el resto del servicio incrementa en cada mutación
(`PersonaRepository.actualizar`, `actualizarPerfil`, `desactivar`). La especificación no dice si una
corrección de seed cuenta como mutación: [`identity/base-de-datos.md` §Seeds](../../especificacion/microservicios/identity/base-de-datos.md#seeds)
solo fija «Activos, version 1» para las personas sembradas, y
[`02` §Condición de presentación real](../../especificacion/02-landing-y-panel.md#condición-de-presentación-real)
dice que los seeds son el contenido inicial y no se vuelven a sembrar si ya existen.

## Qué se decidió

El `UPDATE` de renombrado cambia solo `nombre` y `apellido`. Deja `version` y `updated_at` como
estaban.

## Por qué

`version` es la base del `ETag` que el panel usa para el `If-Match` del `PUT`. Subirla al arrancar
invalidaría el `ETag` que el cliente ya tenía y convertiría la siguiente edición en un 412 sin que
nadie hubiera cambiado nada desde su pantalla. Además la spec fija `version 1` para las filas
sembradas, y esto restituye el valor semilla que la spec manda, no una edición de negocio: no es una
mutación que deba auditarse ni versionarse. Aplicar la jerarquía de
[`../README.md`](../README.md) §1, la opción menos invasiva es la que deja el contrato de
concurrencia intacto.

## Alternativas descartadas

- **Incrementar `version` y `updated_at` como el resto de mutaciones.** Rompe el `ETag` en memoria de
  los clientes del panel y deja las personas sembradas en `version 2`, contra «Activos, version 1».
- **Escribir además una fila en `audit_log`.** Los seeds no se auditan hoy (`audit_log` arranca
  vacío según §Seeds) y no hay actor humano ni correlación que registrar.
- **Hacerlo con una migración Flyway.** Descartada en
  [`IA-back-identity-presentacion-real-01.md`](IA-back-identity-presentacion-real-01.md): la
  corrección tiene que reconocer el apellido semilla anterior fila a fila y convive con el seeder de
  Java, que es el único que corre en los perfiles `dev`/`demo`.

## A quién afecta

- `back-identity-presentacion-real`: es su seeder.
- `front-admin-web-imagenes-panel`: el `ETag` de las personas sembradas sigue siendo `"1"`.
- Contratos: ninguno. `PersonaView.version` no cambia de forma ni de valor para estas filas.
