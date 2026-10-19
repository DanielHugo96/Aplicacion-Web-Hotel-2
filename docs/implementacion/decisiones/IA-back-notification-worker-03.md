# IA-back-notification-worker-03 — Lease e intentos máximos viajan como GUC de sesión

- **Plan:** `back-notification-worker`
- **Paso:** 2 y 6 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

Dos documentos fijan cosas distintas sobre el mismo valor:

- [base-de-datos.md](../../especificacion/microservicios/notification/base-de-datos.md) § Procedimientos
  fija las firmas `fn_notificacion_tomar(p_ahora timestamptz)` y
  `sp_notificacion_resultado(p_command uuid, p_lease_token uuid, p_exito boolean, p_error varchar?)` y
  dice «cambio a ENVIANDO con nuevo `lease_token` y lease **60s**» y «hasta **3** reintentos … (4
  intentos contando inicial)».
- [configuracion.md](../../especificacion/infraestructura/configuracion.md) § notification fija las
  propiedades `hotel.notification.lease-seconds` (`NOTIFICATION_LEASE_SECONDS`, 60) y
  `hotel.notification.max-attempts` (`NOTIFICATION_MAX_ATTEMPTS`, 3) como **sobrescribibles por
  entorno**.

Con la firma congelada no hay parámetro para pasar esos valores.

## Qué se decidió

El listener/planificador publica los valores configurables como GUC de sesión **locales a la
transacción** (`set_config('hotel.notification.lease_seconds', …, true)` y
`set_config('hotel.notification.max_attempts', …, true)`) y los procedimientos los leen con un
`coalesce` sobre el valor por defecto de la spec (60 y 3). La función auxiliar
`notification_setting(nombre, defecto)` centraliza esa lectura.

## Por qué

Es la única forma de respetar las dos cosas a la vez: firma fijada y valor sobrescribible por
entorno. El GUC es local a la transacción, así que no filtra estado entre conexiones del pool. La
jerarquía norma > plan > spec deja la propiedad de configuracion.md como obligatoria, y un valor
hardcodeado en SQL la incumpliría en silencio.

## Alternativas descartadas

- **Ampliar las firmas con un parámetro más**: cambia contratos fijados por la spec.
- **Dejar los valores fijos en el SQL**: incumple configuracion.md y hace que
  `NOTIFICATION_LEASE_SECONDS` no tenga efecto; la verificación del paso 6 depende de que el lease
  sea observable.
- **Leer la propiedad solo en Java y hacer la aritmética allí**: el CAS y el `SKIP LOCKED` viven en la
  base por decisión de diseño del plan (`design.md` §Decisiones 1 y 3).

## A quién afecta

Solo a `notification-service`. Ningún contrato publicado cambia. Se añade la función auxiliar
`notification_setting`, aditiva, junto a los tres procedimientos fijados.