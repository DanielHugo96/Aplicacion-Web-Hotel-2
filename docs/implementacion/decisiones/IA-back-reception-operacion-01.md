# IA-back-reception-operacion-01 — La migración de dominio es V3, no V1

- **Plan:** `back-reception-operacion`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

El paso 2 pide «Migración V1 de `reception_db` con las tablas `room_gate`, `recepcion`,
`operacion_consumo` y `bloqueo_habitacion`». Pero `services/reception-service/` ya nació con la
convención de plataforma mergeada: `V1__api_idempotency.sql` y `V2__outbox_inbox.sql` existen y
están aplicadas en `reception_db`. Renombrarlas o reutilizar V1 rompería el historial de Flyway de
la base ya creada y la convención común, que es norma y manda sobre el detalle del plan.

## Qué se decidió

El esquema de dominio va en `V3__reception_tablas.sql` y las funciones en
`V4__reception_funciones.sql`. La verificación del paso se ejecuta igual sobre una base vacía
aplicando V1–V4.

## Por qué

Jerarquía de fuentes: norma > plan. La convención de plataforma ya cerrada fija V1/V2 y el plan solo
necesita que el esquema exista y se verifique; el número de archivo no cambia el contrato ni el
comportamiento. Se elige la opción menos invasiva: añadir V3/V4 sin tocar las migraciones ya
aplicadas.

## Alternativas descartadas

- **Renombrar V1/V2 a V2/V3 y poner el dominio en V1.** Rompe el checksum de Flyway de una base ya
  migrada y contradice la convención de plataforma.
- **Meter el dominio en V1.** Imposible sin editar una migración ya aplicada.

## A quién afecta

`reception-service` y únicamente su `db/migration`. No toca contratos ni otros servicios.
