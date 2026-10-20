# IA-front-admin-web-catalogo-01 — Adaptaciones al Gateway real sin cambiar contratos

- **Plan:** `front-admin-web-catalogo`
- **Paso:** 3, 4, 12, 13 y 14 de la sección 4 (cliente HTTP, listados, edición de habitación, estado físico y errores)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

Al ejecutar el plan contra el Gateway real (`http://localhost:8080`) y no contra mocks, aparecieron
divergencias entre `docs/contracts/openapi/admin-web.yaml` y la implementación desplegada:

1. `GET /api/persona/listar` y `GET /api/tipopersona/listar` devuelven `data: {personas: [...]}` y
   `data: {tipos: [...]}`, mientras el contrato publica `data` como array.
2. Los listados de hotel (`/api/hotel`, `/api/piso`, `/api/categoria`, `/api/habitacion`) responden
   `meta` solo con `correlationId`; no incluyen `page`, `size` ni `totalElements`, que el contrato
   marca obligatorios para `ApiMetaPage`.
3. `PUT /api/estadohabitacion/actualizar/{id}` ignora el `requestBody` y exige `descripcion` como
   query param; con body únicamente responde 500.
4. El interceptor de `front-admin-web-base` guardaba el `ETag` con la ruta relativa, pero lo buscaba
   con la URL absoluta (`API_BASE_URL` incluido), así que el `If-Match` nunca se enviaba.
5. El mismo interceptor conservaba la `Idempotency-Key` después de un 201/200; un segundo alta desde
   el mismo formulario reutilizaba la clave y el backend respondía 409 `IDEMPOTENCY_CONFLICT` en vez
   de procesar la intención nueva.
6. `GET /api/habitacion/buscar/{id}` no devuelve `urlsImagenes` (el schema lo declara opcional), por
   lo que el formulario de edición parte de una galería vacía.

## Qué se decidió

Se adapta **solo el cliente** y se mantiene el contrato publicado intacto:

- Normalizar `data` de listas: aceptar array o el objeto con `personas`/`tipos`/etc., y usar
  `page`/`size`/`totalElements` del `meta` cuando existan; si faltan, una sola página con el total
  recibido.
- Guardar y leer el `ETag` con la URL absoluta que usa el interceptor. En `PUT`/`DELETE` se mapea la
  ruta de mutación a la de `buscar/{id}` para recuperar el `ETag` vigente.
- Cerrar la intención de idempotencia al recibir una respuesta terminal (2xx/4xx), salvo 503 y 429,
  que se reintentan con la misma clave. Tras corregir un 400/409, la siguiente petición usa una
  clave nueva.
- Enviar `descripcion` a la vez en el body y en la query de `PUT /api/estadohabitacion/actualizar/{id}`.
  El body cumple el contrato y la query satisface la implementación real; un cliente que siga el
  contrato no se rompe.
- El formulario de habitación tolera que `urlsImagenes` no venga: muestra galería vacía y permite
  añadir imágenes; no inventa URLs ni datos.
- Los formularios de mutación se muestran en solo lectura para `EMPLEADO` y el botón de envío solo
  aparece para `ADMIN` (en personas, además, `EMPLEADO` puede crear clientes, como fija la matriz).
  El sidebar se ancla a la izquierda y en ≤1024px arranca plegado con botón “Menú”.

## Por qué

Es la opción menos invasiva: no toca `docs/contracts/**`, no cambia ningún endpoint ni schema, no
modifica servicios y mantiene el mismo comportamiento cuando la implementación siga el contrato.
Las adaptaciones viven en `apps/admin-web/**`, que es la zona del plan.

## Alternativas descartadas

- **Cambiar los contratos OpenAPI para que reflejen el Gateway actual.** La campaña prohíbe tocar
  servicios ajenos y el contrato ya es la norma; además rompería planes de front ya cerrados.
- **Usar mocks del OpenAPI.** Prohibido por la decisión 1 del registro de implementación (§6.2
  estricto).
- **Añadir columnas o parches a los servicios.** La reparación de `tipo_persona.updated_at`,
  `estadohabitacion` y el filtro de sedes de `EMPLEADO` corresponde a un plan de arreglo de back;
  este plan no toca `services/**`.

## A quién afecta

- `apps/admin-web`: el cliente HTTP y los formularios ya toleran las dos formas.
- El arreglo de fondo de identity/hotel queda fuera de esta rama. La evidencia del bloqueo está en
  `docs/plans/front-admin-web-catalogo.md` y en
  `apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md`.
