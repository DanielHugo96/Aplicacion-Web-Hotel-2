# IA-front-admin-web-recepcion-01 — 202 de mantenimiento y baja con banner y polling visibles

- **Plan:** `front-admin-web-recepcion`
- **Paso:** 11 y 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan exige que `POST /api/habitacion/{id}/mantenimiento` y
`DELETE /api/habitacion/eliminar/{id}` muestren un banner persistente con
“Operación en proceso. No repitas la acción.”, sondeos a `Location` según `Retry-After` y refresco
al llegar al estado terminal. El interceptor de `front-admin-web-base`, ya cerrado, convierte
cualquier `202` en un polling interno y devuelve al llamador solo la respuesta terminal; con eso la
pantalla no puede pintar el banner ni el avance. Los dos requisitos chocan, y ya existe un
precedente idéntico para `POST /api/venta` (`IA-front-admin-web-area-cliente-01`).

## Qué se decidió

El interceptor deja pasar el `202` en crudo para `POST /api/habitacion/{id}/mantenimiento` y
`DELETE /api/habitacion/eliminar/{id}`. `RecepcionService` sondea `Location` con `Retry-After` y
notifica cada avance; la pantalla de habitaciones pinta el banner reutilizable y refresca la fila al
terminar. El resto de mutaciones conserva el polling automático del panel.

## Por qué

Es la opción menos invasiva: no cambia el comportamiento del catálogo ni del área de cliente, no
toca contratos ni el Gateway y reutiliza el patrón ya validado en el plan cerrado del área C. La
jerarquía de fuentes de [`../README.md`](../README.md) §1 da prioridad a la norma y al plan; el
plan pide un estado pendiente visible y el interceptor base no lo permite sin esta excepción.

## Alternativas descartadas

- **Cabecera propia para pedir el 202 en crudo.** El Gateway no la permite en CORS; en el navegador
  fallaría el preflight. Añadirla al Gateway es zona `infra`, fuera de este plan.
- **Quitar el polling automático a todo el interceptor.** Rompería el comportamiento cerrado de
  `front-admin-web-base` y de las pantallas que ya cuentan con él.
- **Mostrar el banner solo con el resultado terminal.** No cumple “nunca éxito optimista” ni el
  avance `dd/MM/yyyy HH:mm` que pide el paso 13.

## A quién afecta

- `apps/admin-web`: `core/gateway.interceptor.ts`, `core/services/recepcion.service.ts` y la
  pantalla `/app/habitaciones`.
- Cualquier futura pantalla A/E que necesite el mismo patrón: la condición es por método y URL, sin
  cabecera nueva.
- No cambia contratos, servicios ni planes ya cerrados.
