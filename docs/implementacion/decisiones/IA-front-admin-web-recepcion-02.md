# IA-front-admin-web-recepcion-02 — La clave de idempotencia se conserva hasta la respuesta terminal

- **Plan:** `front-admin-web-recepcion`
- **Paso:** 6, 9 y 11 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan exige que un `503` se reintente “con la misma clave” y que “el reintento conserve clave y
cuerpo”. Al probarlo con `HttpClient` real, el reintento recibía otra clave. La causa no es del
módulo: el interceptor de `front-admin-web-base`, ya cerrado, borraba la intención en el primer
`HttpEvent` que no fuera un `202`. `HttpClient` emite `HttpSentEvent` y `HttpHeaderResponse` antes
de la respuesta, así que la clave se borraba **antes** de que llegara el `503` o el `200`. La prueba
unitaria del base no lo detectó porque invocaba el interceptor con un handler que solo devolvía la
respuesta final, sin eventos intermedios.

## Qué se decidió

La intención se cierra solo cuando llega un `HttpResponse` (respuesta terminal) o cuando se acepta
un `202` durable. `HttpSentEvent` y `HttpHeaderResponse` ya no la borran. El `503`/`429` sigue
conservando la clave, como estaba documentado.

## Por qué

Es un defecto de la base que rompe una regla de la especificación
([`08` §1](../../especificacion/08-admin-web.md#1-reglas-transversales)) y de este plan. La
corrección es aditiva y mínima: no cambia rutas, headers, códigos ni el comportamiento observable
salvo hacer que la idempotencia funcione. Se mantiene la limpieza tras el éxito para que una
mutación nueva a la misma URL use una clave nueva, tal como exige la misma sección.

## Alternativas descartadas

- **Generar la clave en `RecepcionService` y pasarla como header.** Duplicaría la lógica del
  interceptor, chocaría con el header que él mismo escribe y dejaría el defecto vivo para el resto
  del panel.
- **Quitar el borrado de intenciones por completo.** Haría que una segunda operación legítima a la
  misma URL reutilizara la clave y devolviera `409 IDEMPOTENCY_CONFLICT`.
- **No tocar la base y aceptar el reintento con clave nueva.** El backend podría ejecutar dos veces
  la misma intención; es justo lo que el contrato prohíbe.

## A quién afecta

- `apps/admin-web`: `core/gateway.interceptor.ts` y su spec.
- Todo el panel que dependa de reintentar con la misma clave ante `503`/`429` (login y registro no
  llevan clave y no cambian).
- No cambia contratos, servicios ni planes ya cerrados; corrige un comportamiento que la norma ya
  exigía.
