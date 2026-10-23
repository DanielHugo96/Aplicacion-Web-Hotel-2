# IA-back-hotel-resiliencia-demo-01 — Cómo se observa HALF_OPEN en el actuator

- **Plan:** `back-hotel-resiliencia-demo`
- **Paso:** 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-23

## Qué se encontró

El paso 13 pide `"state":"HALF_OPEN"` «a los 10 s» de abrirse el breaker, leyendo
`GET /actuator/circuitbreakers`. En Resilience4j 2.3.0 el paso de `OPEN` a `HALF_OPEN`
**no es un reloj**: `CircuitBreakerStateMachine$OpenState.getState()` devuelve siempre
`OPEN`, y la transición ocurre en `OpenState.tryAcquirePermission()` —es decir, cuando
llega una llamada y el intervalo de espera ya venció— o por el planificador automático
solo si `automatic-transition-from-open-to-half-open-enabled` está activo. La spec no
menciona esa propiedad y la configuración publicada no la incluye, así que el
`curl` de solo lectura a los 10 s sigue devolviendo `OPEN` aunque el intervalo ya haya
pasado. Es el comportamiento de la librería, no un fallo de la configuración: los cinco
valores del breaker y el `CB_TIMEOUT_MS=6000` se aplican tal cual.

## Qué se decidió

Se lee `HALF_OPEN` **después de que llegue una llamada** pasados los 10 s, que es cuando la
librería evalúa la transición, y se deja constancia en §6 de las dos lecturas: la de solo
lectura a los 10 s (que sigue en `OPEN`, porque nadie ha llamado todavía) y la que trae el
estado `HALF_OPEN` con la siguiente llamada. **No** se añade
`automatic-transition-from-open-to-half-open-enabled` al `application.yml`: la spec no lo
fija y añadirlo sería inventar configuración.

## Por qué

La jerarquía de fuentes ([`../README.md`](../README.md) §1) pone la norma y la spec por encima
del plan: la spec fija los cinco valores y el TimeLimiter, y **no** fija el planificador
automático de transición. La opción menos invasiva es no tocar la configuración y explicar en
la evidencia en qué momento exacto la librería pasa a `HALF_OPEN`, sin maquillar la primera
lectura.

## Alternativas descartadas

- **Activar `automatic-transition-from-open-to-half-open-enabled: true`.** Cambiaría la
  configuración publicada (una propiedad que la spec no recoge) y el plan prohíbe ajustar
  los valores del breaker. Además el `curl` de los 10 s funcionaría «por casualidad» de
  reloj, no porque la demo muestre el comportamiento real de la librería.
- **Bajar `wait-duration-in-open-state` para que el `curl` pase antes.** Prohibido: los
  valores son de la spec y no se ajustan.
- **Dejar el paso 13 sin marcar y responder `BLOQUEADO`.** No es una ambigüedad de negocio:
  el comportamiento es determinista y verificable, y el resultado observado (ciclo completo
  hasta `CLOSED`) es exactamente el que pide el plan.

## A quién afecta

- `docs/plans/back-hotel-resiliencia-demo.md` §6 (evidencia literal del ciclo).
- `qa-postman-circuit-breaker` (`CB-07`): su comprobación de `HALF_OPEN` tiene que seguir el
  mismo criterio —una llamada después de los 10 s— o comprobará un estado que la librería
  todavía no ha evaluado. No cambia ningún contrato de negocio ni ninguna ruta.
