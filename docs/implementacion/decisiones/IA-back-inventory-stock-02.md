# IA-back-inventory-stock-02 — Bloqueo del plan por un defecto en `main` (hotel-service no arranca)

- **Plan:** `back-inventory-stock`
- **Paso:** 6 y 14 de la sección 4. Pasos (alta de producto y asignación de sede)
- **Nivel:** bloqueo (punto 7 del orquestador; no es una decisión de nivel 1, 2 ni 3)
- **Fecha:** 2026-10-19

## Qué se encontró

El plan está implementado y verificado salvo dos pasos: `POST /api/producto/registrar` y
`POST /api/producto/{id}/sedes`, que exigen validar la sede por Feign contra
`GET /internal/hoteles/{id}` de `hotel-service`. Al ejecutar los casos de aceptación, la ejecutora
comprobó que **`hotel-service` no puede arrancar desde `main`**, por dos defectos independientes:

1. `services/hotel-service/src/main/resources/db/migration/V3__hotel_tablas.sql` declara la columna
   `habitacion.publicada` (línea 58), pero `fn_catalogo_publico` usa `hab.publica` y `h2.publica`
   (líneas 110 y 116). Flyway aborta con `ERROR: column hab.publica does not exist`.
2. `Habitacion` mapea una FK compuesta a `piso(id, id_hotel)` que no es clave única: Hibernate falla
   al construir el metamodelo con `org.hibernate.MappingException: No column with logical name
   'id_hotel' in table 'piso'`, también con `spring.jpa.hibernate.ddl-auto=none`.

El orquestador verificó el primero por lectura del fichero: la columna existe como `publicada` y la
función la referencia como `publica`. El segundo lo reportó la ejecutora con la excepción literal.

Ambos defectos están en `back-hotel-catalogo`, **un plan ya `cerrado` y mergeado en `main` con el
PR #66**.

## Qué se decidió

**Bloquear `back-inventory-stock` y no tocar `hotel-service`.**

- PR de implementación **#72** (`back-inventory-stock` → `main`): abierto y **cerrado sin mergear**.
- La rama `back-inventory-stock` queda en `origin` con sus cuatro commits, para no perder el trabajo.
- El plan se queda `en curso` en su rama (no está cerrado) y `propuesto` en `main`, como corresponde.

## Por qué

La campaña **no puede corregir un plan ya `cerrado` en `main`**
([`../README.md`](../README.md) §9, «Lo que la campaña no puede hacer»). `hotel-service` es zona de
`back-hotel-catalogo`, no de este plan, y `docs/plans/README.md` §6.4 limita la escritura de un plan
`back` a su propio servicio. Arreglarlo desde aquí sería tocar zona ajena y enmendar un plan cerrado:
exactamente lo que la norma prohíbe. La ejecutora aplicó el mismo criterio y lo dejó escrito en
[`IA-back-inventory-stock-01.md`](IA-back-inventory-stock-01.md).

## Hallazgo que conviene revisar a una persona

El plan `back-hotel-catalogo` (cerrado, PR #66) declara en su §6 evidencia de `LP01` («los cuatro GET
públicos sin token → 200»), `SEC03`, `CRUD01` y `OCC04` con «evidencia: respuestas». **Esa evidencia
no puede ser real**: `LP01` llama a `fn_catalogo_publico`, la función que referencia la columna
inexistente `hab.publica`, y el propio servicio no llega a arrancar. La sección «Evidencia obtenida»
del plan solo muestra el build y los validadores, más tres clases de pruebas unitarias
(`SecurityConventionTests`, `JwtValidatorTests`, `IdempotencyFilterTests`); no muestra ninguna
respuesta HTTP de `SEC03`, `LP01`, `CRUD01` ni `OCC04`.

Es decir: el código de `hotel-service` está mergeado en `main` con dos defectos que impiden arrancar,
y su plan lo declara cerrado con evidencia que no se corresponde con lo que el código puede hacer.
Conviene que una persona revise ese plan antes de dar por bueno nada que dependa de él.

## A quién afecta

`back-hotel-catalogo` es dependencia directa o transitiva de **todos** los planes que quedan, así que
este defecto los alcanza a todos:

- Directa: `back-inventory-stock` (bloqueado), `back-reception-operacion`, `back-reporting-proyecciones`,
  `front-public-web-landing`, `front-admin-web-catalogo`, `qa-matriz-aceptacion`.
- Transitiva: `back-hotel-operaciones`, `back-sales-ventas`, `back-reception-cierre`,
  `front-admin-web-recepcion`, `front-admin-web-ventas`, `front-admin-web-cierre`,
  `front-admin-web-reportes`, `front-admin-web-area-cliente` y `qa-runbooks`.

No afecta a los ya cerrados (`back-identity-auth`, `back-notification-worker`, `back-hotel-catalogo`
mismo, `back-identity-personas`, `front-admin-web-base`), que no invocan `hotel-service` en tiempo de
ejecución. `back-identity-personas` sí usa Feign a `hotel-service`, pero su evidencia se obtuvo antes
de este hallazgo y conviene revisarla también.

## Alternativas descartadas

- **Corregir `V3__hotel_tablas.sql` y el mapeo de `Habitacion` desde esta campaña.** Es la solución
  real, pero es zona ajena y enmienda un plan cerrado. La norma lo prohíbe, y el orquestador no
  escribe código de implementación.
- **Arrancar `hotel-service` con una copia parcheada de la migración.** La ejecutora lo intentó como
  diagnóstico: la migración pasaba, pero el mapeo JPA seguía fallando. Además, la evidencia no
  representaría `main`.
- **Sustituir el Feign por un stub.** Prohibido: la campaña no usa mocks para salvar una verificación.
- **Marcar el plan `cerrado` con los pasos 6 y 14 sin evidencia.** El §6 del plan exige ejecutar
  PostgreSQL y el servicio reales; marcarlos sería repetir el defecto de `back-hotel-catalogo`.

## Cómo se desbloquea

1. Corregir los dos defectos de `hotel-service` en un PR propio (no de campaña): `publica` →
   `publicada` en `fn_catalogo_publico`, y el mapeo de `Habitacion` frente a `piso`.
2. Verificar que `hotel-service` arranca desde `main` y que `LP01` responde 200 de verdad.
3. Reabrir `back-inventory-stock` (rama ya en `origin`) y ejecutar los pasos 6 y 14.

Este archivo documenta por qué se paró: **el bloqueo no es de este plan, es de `main`**. Quien lo lea
tiene que saber que el trabajo de `back-inventory-stock` está completo y esperando, y que el freno
está fuera de la campaña.