# IA-back-hotel-operaciones-01 — nombre de la migración y propiedades del job

- **Plan:** `back-hotel-operaciones`
- **Paso:** 1 y 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan llama «Migración V2 de `hotel_db`» a la migración de `limpieza` y
`operacion_habitacion`, pero el repositorio ya tiene aplicadas `V1__api_idempotency`,
`V2__outbox_inbox`, `V3__hotel_tablas` y `V4__seeds_hotel`. Una migración `V2` nueva
colisiona con el historial de Flyway ya desplegado. El propio plan (§3) delega el nombre
concreto de la migración y de las propiedades del job a la ejecución.

## Qué se decidió

La migración se llama `V5__hotel_operaciones.sql` y las propiedades del job son
`hotel.operaciones.recovery-interval-ms`, `hotel.operaciones.recovery-batch-size`,
`hotel.operaciones.recovery-max-backoff-seconds` y
`hotel.operaciones.alert-pending-minutes`.

## Por qué

La jerarquía de fuentes (norma > plan > catálogo > registro) hace que el historial de Flyway
ya aplicado mande sobre el nombre literario del plan: una migración nueva debe ser la
siguiente versión y no puede reutilizar una ya instalada. El plan autoriza expresamente fijar
estos nombres al ejecutar. Las propiedades siguen el prefijo `hotel.*` ya usado por el
servicio y son sobrescribibles por entorno, sin tocar valores fijados.

## Alternativas descartadas

- Renombrar las migraciones existentes: rompe el historial de Flyway ya aplicado en las bases
  de la demo y en cualquier entorno con datos.
- Llamar a la migración `V2b` o `V2_1`: Flyway ordena por versión y el nombre quedaría fuera
  de la convención `Vn__descripcion.sql`.
- Fijar los valores del job en constantes Java: impide ajustar la periodicidad por entorno y
  contradice la práctica de configuración del servicio.

## A quién afecta

Solo a `hotel-service` y a su base `hotel_db`. No cambia ningún contrato publicado ni ningún
plan cerrado.
