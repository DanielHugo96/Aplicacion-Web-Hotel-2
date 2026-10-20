# IA-back-sales-ventas-02 — Transiciones CAS en el repositorio, no funciones PL/pgSQL con firma incompleta

- **Plan:** `back-sales-ventas`
- **Paso:** 7–10 y 14 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

`sales/base-de-datos.md` § Funciones/procedimientos lista firmas como
`sp_venta_preparar_debito(p_venta int,p_operation uuid)`, `sp_venta_confirmar(p_venta int,p_resultado_inventory jsonb)`
o `fn_venta_iniciar(...)` sin parámetros para el actor, el rol ni el hash de la petición, que son
columnas `NOT NULL` de `operacion_venta` y `venta`. Una función con esa firma no puede insertar la
operación completa sin un `UPDATE` posterior ni puede recibir el `medio_pago` de un pago manual.
Además, `sp_venta_confirmar` tendría que construir en SQL los envelopes de outbox de dos destinos,
duplicando la lógica que ya tiene la plataforma en Java.

## Qué se decidió

Las transiciones de `venta` y `operacion_venta` se implementan como CAS con `UPDATE ... WHERE`
sobre el estado origen y comprobación de filas modificadas en `SalesRepository`, dentro de la misma
transacción local que el efecto y el outbox. La migración V3 conserva el DDL de referencia completo
(checks, unicidades, índices y FK diferida). Solo los upserts del carrito y el bloqueo por recepción
del cierre se apoyan en SQL directo.

## Por qué

La spec fija invariantes (CAS, una operación no terminal por venta, commit antes del HTTP, terminal
en el mismo commit que el outbox), no la ubicación del código. El CAS en el repositorio conserva
esos invariantes y permite pasar actor, rol, hash y medio de pago sin inventar una firma distinta
a la publicada. Es la opción menos invasiva: no cambia tablas, columnas, contratos ni
procedimientos existentes.

## Alternativas descartadas

- **Crear las funciones con parámetros extra.** Cambia la firma que documenta la spec y obliga a
  mantener lógica de envelopes en PL/pgSQL, más difícil de validar contra los JSON Schemas.
- **Crear las funciones con la firma publicada y completar actor/hash con `UPDATE` posterior.**
  Deja filas `NOT NULL` con valores provisionales y hace el flujo más frágil ante concurrencia.

## A quién afecta

`sales-service`: `SalesRepository`, `SalesService` y `RecuperadorVentas`. No cambia contratos,
endpoints, eventos ni códigos de error.
