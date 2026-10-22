# IA-back-notification-presentacion-real-01 — plantillas nuevas en V901, no en V900

- **Plan:** `back-notification-presentacion-real`
- **Paso:** 1 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

[`notification/base-de-datos.md` §Seeds](../../especificacion/microservicios/notification/base-de-datos.md#seeds) cambia el asunto de BIENVENIDA a «Bienvenido a Casa Sillar» y el pie de las tres plantillas a «Notificación automática de la cadena; no es un comprobante fiscal». Las plantillas viven en `services/notification-service/src/main/resources/db/seed/dev/V900__notification_plantillas.sql`, que ya está aplicada en el perfil dev y no se puede editar sin romper el checksum.

## Qué se decidió

Añadir `V901__notification_plantillas_presentacion.sql` en `db/seed/dev/` con un `UPDATE` por `(tipo, version)` de las tres plantillas, conservando las variables y `templateVersion = 1`.

## Por qué

Es la misma regla que aplica `hotel` con `V4`/`V6`: una migración aplicada no se reescribe. El seed nuevo deja la base en el estado de la spec y es idempotente porque actualiza filas con clave fija.

## Alternativas descartadas

- **Editar `V900`.** Flyway falla por checksum en las bases de dev ya migradas.
- **Mover los textos al código.** Las plantillas son datos de la tabla `template` y la spec las sitúa en el seed; el worker las lee de la base.
- **No cambiar nada.** Los correos seguirían diciendo «Hotel Demo» y «Notificación de demostración», que es justo lo que la v1.3 retira.

## A quién afecta

- `back-notification-presentacion-real`: es su migración.
- `back-notification-worker`: no cambia; sigue renderizando con las mismas variables.
- Contratos: ninguno. `notification/api.md` ya muestra el nombre nuevo en sus ejemplos.
