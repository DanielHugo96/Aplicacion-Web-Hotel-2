# IA-back-notification-worker-01 — Migraciones V3–V5 y seeds en `db/seed/dev`

- **Plan:** `back-notification-worker`
- **Paso:** 2 de la sección 4. Pasos (y tarea 1.2 de `tasks.md`)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

El paso 2 pide «Migración **V1** de `notification_db` con `notification_job` y `template` … las tablas
técnicas usan el DDL común … crear los tres procedimientos». Pero `notification-service` ya tiene
`V1__api_idempotency.sql` y `V2__outbox_inbox.sql`, mergeadas por `back-plataforma-comunes` y
`back-plataforma-mensajeria` (ambos `cerrado`), y [configuracion.md](../../especificacion/infraestructura/configuracion.md)
§ Flyway y seeds prohíbe editar una versión ya aplicada. Faltaba además `audit_log` (DDL común) y no
existía en ningún servicio un ejemplo de la ubicación de seeds.

## Qué se decidió

- `V3__audit_log.sql` (tabla técnica común `audit_log`, DDL de
  [`03-contrato-comun.md`](../../especificacion/03-contrato-comun.md) § Persistencia transversal),
  mismo patrón que `V3__audit_log.sql` de la rama `back-identity-auth`.
- `V4__notification_job_template.sql` (`notification_job` y `template` tal cual el DDL de referencia,
  con sus dos CHECK y `ix_notification_job_estado`).
- `V5__notification_procedimientos.sql` (los tres procedimientos con las firmas fijadas).
- Seeds de plantillas v1 en `src/main/resources/db/seed/dev/V900__notification_plantillas.sql`,
  activadas solo por el perfil `dev` (`application-dev.yml` añade la location
  `classpath:db/seed/dev`), como fija configuracion.md § Flyway y seeds. V900 deja libre el rango de
  migraciones del esquema.

## Por qué

El propio plan (§3, Contexto) declara que «los nombres de migración Flyway `V<n>__descripcion.sql` y
la ubicación de seeds de dev … se fijan al ejecutar y se anotan en el PR», así que el número no es un
contrato. La jerarquía de fuentes de [`../README.md`](../README.md) §1 pone la norma por encima del
plan: una versión aplicada no se edita, así que la migración de notification es aditiva (V3+).

## Alternativas descartadas

- **Meter el esquema en V1/V2** (editarlas): rompe la regla de Flyway y el historial de bases ya
  desplegadas; el `validar-contratos`/CI no lo detectaría, pero un entorno ya migrado fallaría por
  checksum.
- **Seeds en `db/migration` con guardas de perfil en SQL**: siembra en producción, prohibido por
  configuracion.md.
- **Seed en código Java (`ApplicationRunner`)**: no es la convención del repositorio y duplica el
  DDL de referencia.

## A quién afecta

Solo a `notification-service`. `V1`/`V2` quedan intactas, así que ningún plan cerrado ni ninguna base
ya migrada se ve afectada. El número de versión de las nuevas migraciones se anota en el PR.