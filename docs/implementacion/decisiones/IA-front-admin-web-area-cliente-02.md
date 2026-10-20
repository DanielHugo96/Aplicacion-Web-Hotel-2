# IA-front-admin-web-area-cliente-02 — Listado de mis ventas sin endpoint global

- **Plan:** `front-admin-web-area-cliente`
- **Paso:** 7 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El contrato no publica un listado global de ventas para C. Solo existen
`GET /api/venta/recepcion/{idRecepcion}` (ventas de una estadía) y
`GET /api/venta/buscar/{id}` (una venta propia). El plan ya decide en §3 armar el listado con las
estadías propias, pero el paso 7 pide “paginación desde `meta`” sin decir cómo paginar un conjunto
que se compone de varias respuestas paginadas. La spec tampoco fija un endpoint nuevo para C.

## Qué se decidió

`/mis-ventas` lee las estadías propias, recorre cada `GET /api/venta/recepcion/{idRecepcion}` hasta
agotar `meta.totalElements`, descarta cualquier venta cuyo `idCliente` no sea el `sub` y pagina en
el cliente el conjunto unido (20 por página). La cabecera de paginación muestra el total agregado.

## Por qué

Es la lectura propia ya documentada y no inventa una ruta ni un contrato. Se usa `meta` para saber
cuándo terminar cada estadía; la paginación visible se compone porque el contrato pagina por
estadía, no por cliente. Es la opción menos invasiva: no modifica `admin-web.yaml`, no toca sales y
no rompe ningún plan cerrado.

## Alternativas descartadas

- **Añadir un endpoint global `GET /api/venta/cliente/{idCliente}`.** Es un contrato nuevo de sales;
  fuera del alcance de un plan `front` y no está en la spec.
- **Mostrar solo la primera estadía o la primera página de cada una.** Ocultaría consumos reales y
  contradice “mis ventas” de todas las estadías propias.
- **Paginar por la última estadía consultada.** El total y las flechas cambiarían según el orden de
  las respuestas y no representarían el conjunto.

## A quién afecta

- `apps/admin-web`: `mis-ventas.component.ts` y su prueba de agregación/paginación.
- Sin cambios de contrato, servicios ni datos. Si sales publica en el futuro un listado global para
  C, este plan puede migrar a él sin cambiar la pantalla.
