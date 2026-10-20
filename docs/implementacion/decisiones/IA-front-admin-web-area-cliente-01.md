# IA-front-admin-web-area-cliente-01 — 202 de venta con banner y polling visibles

- **Plan:** `front-admin-web-area-cliente`
- **Paso:** 6 y 9 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan exige que, tras un `202` de `POST /api/venta`, la pantalla muestre un banner persistente
con “Operación en proceso. No repitas la acción.”, actualice “Actualizado dd/MM/yyyy HH:mm” en cada
sondeo y deshabilite las acciones que duplicarían la intención (paso 9). El interceptor de
`front-admin-web-base`, ya cerrado, convierte cualquier `202` en un polling automático a
`Location` y devuelve al llamador solo la respuesta terminal; con ese comportamiento la pantalla
no puede pintar el banner ni el aviso de cada reintento. Los dos documentos chocan.

## Qué se decidió

El interceptor conserva su comportamiento automático para todas las rutas salvo `POST /api/venta`.
Para esa ruta devuelve el `202` tal cual y el `ClienteService` sondea `Location` respetando
`Retry-After`, consulta `GET /api/venta/operaciones/{operationId}` y notifica cada avance a la
pantalla. La condición es por método y URL (`POST` + `/api/venta`), sin cabecera nueva.

## Por qué

Es la opción menos invasiva: no cambia ningún plan cerrado (el resto del panel sigue con el
polling automático), no toca contratos ni servicios y no añade una cabecera que el Gateway no
tenga en `Access-Control-Allow-Headers`. La jerarquía de fuentes de
[`../README.md`](../README.md) §1 da prioridad a la norma y al plan; el plan pide un estado
pendiente visible y esta es la forma de dárselo sin romper la base.

## Alternativas descartadas

- **Cabecera propia para pedir el 202 en crudo.** El Gateway no la permite en CORS; en el navegador
  fallaría el preflight. Añadirla al Gateway es zona `infra`, fuera de este plan.
- **Quitar el polling automático a todo el interceptor.** Rompería el comportamiento cerrado de
  `front-admin-web-base` y de futuras pantallas de recepción/cierre.
- **Hacer el HTTP de la venta a mano, sin interceptor.** Duplicaría el manejo de JWT,
  `Idempotency-Key`, `X-Correlation-Id` y errores.
- **Mostrar el banner solo con el resultado terminal.** No cumple “nunca éxito optimista” ni el
  aviso de cada reintento.

## A quién afecta

- `apps/admin-web`, área de cliente: `gateway.interceptor.ts`, `cliente.service.ts` y `carrito`.
- Futuras pantallas A/E que usen `POST /api/venta`: el `202` de esa ruta es manual; deberán hacer su
  propio banner y polling o reutilizar el servicio del área.
- No cambia ningún contrato, servicio ni plan ya cerrado.
