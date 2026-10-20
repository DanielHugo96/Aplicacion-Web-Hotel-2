# IA-back-sales-ventas-01 — La migración de dominio es V3, no V1

- **Plan:** `back-sales-ventas`
- **Paso:** 3 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El paso 3 pide «Crear la migración V1 de `sales_db` con carrito, venta, detalle_venta,
operacion_venta y cierre_consumos». Pero `services/sales-service/` ya nació con la convención de
plataforma mergeada: `V1__api_idempotency.sql` y `V2__outbox_inbox.sql` existen y forman parte del
historial de Flyway de `sales_db`. Editar V1 o renombrar las migraciones ya aplicadas rompería el
checksum y la convención común, que es norma y manda sobre el detalle del plan.

## Qué se decidió

El esquema de dominio va en `V3__sales_tablas.sql`, con el DDL de referencia completo (checks,
unicidades, índices y la FK diferida entre `venta` y `operacion_venta`). La verificación del paso se
ejecuta igual sobre una base vacía aplicando V1–V3.

## Por qué

Jerarquía de fuentes: norma > plan. La convención de plataforma ya cerrada fija V1/V2 y el plan solo
necesita que el esquema exista y se verifique; el número de archivo no cambia el contrato ni el
comportamiento. Es la opción menos invasiva y la misma que ya se registró para `reception-service`
(`IA-back-reception-operacion-01`).

## Alternativas descartadas

- **Renombrar V1/V2 a V2/V3 y poner el dominio en V1.** Rompe el checksum de una base ya migrada y
  contradice la convención de plataforma.
- **Meter el dominio en V1.** Implica editar una migración ya aplicada.

## A quién afecta

`sales-service` y únicamente su `db/migration`. No toca contratos ni otros servicios.
