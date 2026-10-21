# IA-front-admin-web-cierre-01 — El 202 del cierre necesita el banner y el polling de la pantalla

- **Plan:** `front-admin-web-cierre`
- **Paso:** 6 y 7 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

El plan exige que `POST /api/recepcion/registrar-salida` muestre el banner persistente «cierre
pendiente, no volver a cobrar» con spinner, hora de actualización, `aria-live` y polling a
`Location` según `Retry-After`, sin éxito optimista ([`08` §1](../../especificacion/08-admin-web.md#1-reglas-transversales)
y [§4](../../especificacion/08-admin-web.md#4-estados-por-pantalla)). El interceptor de
`front-admin-web-base`, ya cerrado, convierte cualquier `202` en un polling interno y solo entrega
al llamador la respuesta terminal, de modo que la pantalla no puede pintar el banner ni el avance.
Los dos requisitos chocan, y el repositorio ya tiene dos precedentes idénticos:
`IA-front-admin-web-area-cliente-01` (ventas) e `IA-front-admin-web-recepcion-01` (mantenimiento y
baja de habitación).

## Qué se decidió

El interceptor deja pasar el `202` en crudo para `POST /api/recepcion/registrar-salida`.
`RecepcionService.registrarSalida` devuelve `{kind:'pendiente'}` con `location` y
`retryAfterMs`; `CierreFormComponent` pinta `app-pending-banner` y sondea con `seguirCierre` hasta
que la estadía deja de estar `CERRANDO`. El resto de mutaciones conserva el polling automático del
panel.

## Por qué

Es la opción menos invasiva: no cambia el comportamiento del panel fuera de esa ruta, no toca
contratos ni el Gateway y reutiliza el patrón ya validado y registrado en dos planes cerrados. La
condición es por método y URL, sin cabecera nueva, así que no hay preflight que el Gateway tenga
que permitir.

## Alternativas descartadas

- **Cabecera propia para pedir el 202 en crudo.** El Gateway no la permite en CORS; añadirla es
  zona `infra`, fuera de este plan.
- **Quitar el polling automático a todo el interceptor.** Rompería el comportamiento cerrado de
  `front-admin-web-base` y de las pantallas que ya cuentan con él.
- **Mostrar el banner solo con el resultado terminal.** No cumple «nunca éxito optimista» ni el
  avance `dd/MM/yyyy HH:mm` que pide el paso 13.

## A quién afecta

- `apps/admin-web`: `core/gateway.interceptor.ts`, `core/services/recepcion.service.ts` y
  `features/recepcion/cierre/`.
- Cualquier futura pantalla A/E que necesite el mismo patrón: la condición es por método y URL.
- No cambia contratos, servicios ni planes ya cerrados.
