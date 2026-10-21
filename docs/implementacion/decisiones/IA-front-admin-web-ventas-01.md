# IA-front-admin-web-ventas-01 — `idHotel` en el ajuste de stock (workaround retirado)

- **Plan:** `front-admin-web-ventas`
- **Paso:** 8 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20 · **Actualizado:** 2026-10-21
- **Estado:** resuelto; el workaround ya no está en el cliente

## Qué se encontró

`admin-web.yaml` declara `POST /api/producto/{id}/ajustes` con un cuerpo cerrado
`{idHotel, delta, motivo}` y sin parámetro `idHotel` en la query. El controlador
real de `inventory-service` exigía `@RequestParam Integer idHotel` además del
cuerpo, así que la integración por el Gateway devolvía
`500 INTERNAL_ERROR` con `Required request parameter 'idHotel' … is not present`.

## Qué se decidió entonces

`VentasService.ajustarStock` enviaba `idHotel` **también como query**, con el
mismo valor del cuerpo, sin cambiar el schema. Era la opción menos invasiva
mientras el defecto vivía en `back-inventory-stock`.

## Qué pasó después

El plan de arreglo `back-arreglo-inventory-baja-y-ajuste` (PR #115) corrigió el
controlador: ahora toma `idHotel` de `AjusteRequest.idHotel`, como publica el
contrato, y ya no exige la query. La integración real del 21/10/2026 confirma
`201` enviando solo el cuerpo cerrado.

## Decisión vigente

Se **retiró el workaround**: `ajustarStock` vuelve a enviar únicamente
`{idHotel, delta, motivo}` y el `If-Match`; no añade ningún query param. Las
pruebas del servicio y del listado comprueban que la petición no lleva
`idHotel` en la query.

## Alternativas descartadas

- **Mantener la query “por compatibilidad”.** El backend arreglado ya no la
  necesita y el contrato no la publica; dejarla sería deuda y ruido.
- **Cambiar `admin-web.yaml`.** No hace falta: el contrato ya describía el
  cuerpo correcto; el defecto era del controlador y se reparó en su ámbito.

## A quién afecta

- `apps/admin-web/src/app/core/services/ventas.service.ts` y su spec.
- La pantalla `/app/productos` (diálogo «Ajustar stock»).
- Ningún contrato ni servicio cambia en este plan.
