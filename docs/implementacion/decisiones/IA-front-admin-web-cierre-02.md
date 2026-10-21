# IA-front-admin-web-cierre-02 — Acción esperada de los 409 del cierre

- **Plan:** `front-admin-web-cierre`
- **Paso:** 6 y 8 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

El plan pide que los 409 del cierre apliquen «su acción esperada sin reintentar la misma
intención». El catálogo de errores ([`errors.md`](../../contracts/errors.md)) ya fija la acción de
cada código, pero no fija qué hace la pantalla con cada una:

- `AMOUNT_MISMATCH` → «Mostrar la nueva cotización y confirmar con ella» (la cotización y el
  `closureId` vienen en `data`).
- `OPERATIONS_IN_PROGRESS` → «Esperar el resultado y reintentar».
- `CLOSURE_IN_PROGRESS` → «Consultar la estadía; reintentar solo con el mismo `closureId`».
- `STAY_NOT_ACTIVE` → «Recargar el estado; usar el flujo correcto».

El plan no dice cuáles de esas acciones implican una nueva lectura del recurso ni si el botón de
reintento debe reutilizar o no la intención.

## Qué se decidió

- `AMOUNT_MISMATCH`: no se recarga nada. Se pinta la cotización nueva que llegó en el cuerpo y el
  botón «Confirmar con la nueva cotización» envía una **intención nueva** (el interceptor genera
  otra `Idempotency-Key` al cerrarse la anterior con un 409), conservando el ETag del detalle.
- `STAY_NOT_ACTIVE` y `CLOSURE_IN_PROGRESS`: se muestra el mensaje del catálogo y se relee la
  estadía con el `GET` del detalle (mismo `cargar()`), conservando el mensaje en pantalla. En el
  panel del detalle se emite `recargar` para que la pantalla padre relea.
- `OPERATIONS_IN_PROGRESS`: se muestra el mensaje y la acción; **no** se reintenta solo. El
  operador decide cuándo volver a intentar y, al hacerlo, la intención es nueva.

## Por qué

Es la lectura literal de la acción del catálogo y la opción menos invasiva: no añade rutas, no
cambia contratos y reutiliza la recarga que ya existe para `412/428`. Mantener el mensaje tras la
recarga evita el efecto «el error desapareció solo» que tendría recargar y limpiar el banner.

## Alternativas descartadas

- **Reintentar automáticamente los 409.** Duplicaría el cobro o repetiría una intención ya
  rechazada; el contrato dice lo contrario.
- **Recargar también en `AMOUNT_MISMATCH`.** Pisaría la cotización nueva del 409 con la del `GET`,
  que puede ser otra distinta si el consumo cambió en medio.
- **No recargar en `STAY_NOT_ACTIVE`/`CLOSURE_IN_PROGRESS`.** Dejaría la pantalla con un estado que
  ya no es el del backend y el siguiente intento volvería a fallar.

## A quién afecta

- `apps/admin-web/src/app/features/recepcion/cierre/cierre-form.component.ts` y
  `cierre-panel.component.ts`.
- No cambia contratos, servicios ni planes ya cerrados.
