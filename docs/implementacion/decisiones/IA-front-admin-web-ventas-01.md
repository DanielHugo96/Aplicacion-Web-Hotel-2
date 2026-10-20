# IA-front-admin-web-ventas-01 — `idHotel` también como query en el ajuste de stock

- **Plan:** `front-admin-web-ventas`
- **Paso:** 8 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

`admin-web.yaml` declara `POST /api/producto/{id}/ajustes` con un cuerpo cerrado
`{idHotel, delta, motivo}` y sin parámetro `idHotel` en la query. El contrato de
inventory (`inventory.yaml`) publica el mismo `AjusteRequest` y tampoco declara la
query. En cambio, el controlador real de `inventory-service`
(`ProductoController.ajustar`) exige `@RequestParam Integer idHotel` además del
cuerpo. Al ejecutar la integración real por el Gateway, el front enviaba solo el
cuerpo y el servicio respondía `500 INTERNAL_ERROR` con
`Required request parameter 'idHotel' … is not present` en su log.

## Qué se decidió

`VentasService.ajustarStock` envía `idHotel` **también como query**, con el mismo
valor que ya viaja en el cuerpo cerrado del contrato. No se quita `idHotel` del
cuerpo ni se cambia ningún schema: la petición sigue siendo válida para
`admin-web.yaml` y satisface al controlador real.

## Por qué

Es la opción menos invasiva: una línea en el cliente HTTP del plan, sin tocar
servicios, contratos ni planes ya cerrados. Mantener el cuerpo intacto evita
romper el schema `AjusteRequest`/`ProductoAjuste` y el validador de contratos.
La alternativa de cambiar el contrato para declarar la query es zona de
`docs/contracts/**` y exigiría un plan de arreglo de back; la norma del plan
`front` solo permite ampliaciones aditivas y aquí basta con enviar el parámetro.

## Alternativas descartadas

- **Quitar `idHotel` del cuerpo y mandarlo solo en la query.** Rompe el schema
  cerrado que publica `admin-web.yaml` y las pruebas de contrato del front.
- **Cambiar `inventory.yaml` para declarar la query.** Es un contrato de otro
  ámbito; un plan `front` no lo modifica y obligaría a reabrir
  `back-inventory-stock`.
- **Ajustar el controlador de inventory.** Zona `back`; fuera del alcance de este
  plan y de su rama.

## A quién afecta

- `apps/admin-web/src/app/core/services/ventas.service.ts` y su spec.
- La pantalla `/app/productos` (diálogo «Ajustar stock»).
- Ningún contrato ni servicio cambia; queda anotado para que un futuro plan de
  arreglo alinee `inventory-service` con `admin-web.yaml`.
