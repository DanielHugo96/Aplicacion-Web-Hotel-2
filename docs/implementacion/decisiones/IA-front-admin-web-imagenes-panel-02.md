# IA-front-admin-web-imagenes-panel-02 — miniatura de línea de venta sin `imagenUrl` en el contrato

- **Plan:** `front-admin-web-imagenes-panel`
- **Paso:** 10 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-22

## Qué se encontró

[`08` §6](../../especificacion/08-admin-web.md#6-imágenes-en-el-panel) pide la miniatura del
producto «en cada línea» del catálogo, carrito y ventas, y añade: «Se toma `imagenUrl` del
contrato de la **venta**; si es nula, fallback neutro». El plan repite esa frase en su paso 10.

El contrato no lo permite tal cual:

- `VentaDetalle` en [`admin-web.yaml`](../../contracts/openapi/admin-web.yaml) y en
  [`sales.yaml`](../../contracts/openapi/sales.yaml) solo publica
  `idDetalleVenta`, `idProducto`, `nombreProducto`, `cantidad`, `precioUnitario` y `subTotal`.
  **No tiene `imagenUrl`**, y `additionalProperties` no está abierto.
- `CarritoItem` y `CatalogoItem` **sí** publican `imagenUrl`
  ([`sales.yaml`](../../contracts/openapi/sales.yaml) §CarritoItem y §CatalogoItem), y el
  `Producto` del catálogo del cliente también.

Es decir: el carrito, el catálogo y la rejilla de venta nueva tienen campo; la línea confirmada
de una venta no. El plan declara `docs/contracts/**` y `services/**` fuera de alcance, así que la
ejecutora no puede añadir el campo al contrato ni hacer que `sales-service` lo emita.

## Qué se decidió

Pintar la miniatura en las líneas donde el contrato ya expone `imagenUrl` —catálogo del área de
cliente, carrito, venta nueva (carrito y catálogo de la sede) y las dos vistas de detalle de
venta— declarando `imagenUrl` **opcional** en el modelo local de `VentaDetalle`. Cuando la línea
no trae el campo (el caso de hoy en `VentaDetalle`) la miniatura pinta el **fallback neutro**, tal
como la propia regla de §6 manda para el valor nulo. El `alt` siempre es el nombre del producto,
como ya fija [`IA-front-admin-web-imagenes-panel-01.md`](IA-front-admin-web-imagenes-panel-01.md).

No se toca ningún contrato ni servicio: si `sales-service` publica el campo en el futuro, el
modelo opcional lo pinta sin cambiar una línea de plantilla.

## Por qué

1. **Jerarquía de fuentes** ([`../../implementacion/README.md`](../../implementacion/README.md) §1):
   manda la norma/plan, y el plan cerrado `front-admin-web-ventas` fijó `VentaDetalle` «con los
   nombres y tipos exactos» del contrato, sin `imagenUrl`. Añadirlo aquí rompería su prueba de
   campos.
2. **Ampliar sí, cambiar no** (§6.11 nivel 3): la ampliación aditiva del contrato corresponde al
   servicio que produce el dato (`sales-service`), no a la app que lo consume, y el plan lo deja
   explícitamente fuera de su zona.
3. **Opción menos invasiva:** declarar el campo opcional en el front no altera el contrato, no
   reabre ningún plan cerrado y deja la pantalla correcta hoy (fallback neutro) y mañana
   (miniatura real) sin deuda.
4. §6 ya contempla este caso literalmente: «si es nula, fallback neutro». Un contrato que no
   publica el campo es el caso extremo de «nula», y el fallback es justo lo que se exige.

## Alternativas descartadas

- **Añadir `imagenUrl` a `VentaDetalle` en `admin-web.yaml` y `sales.yaml`.** Es zona de `back` y
  de `infra` de contratos, no de este plan (`Alcance` → fuera de alcance), y sin el cambio de
  `sales-service` el campo llegaría siempre vacío: una ampliación de contrato que no se cumple.
- **Deducir la imagen cruzando `idProducto` con el catálogo de la sede o de la estadía.** Inventa
  una correlación que el contrato no da (una venta histórica puede ser de un producto ya dado de
  baja o con la imagen editada) y añade una petición por línea. Descartado por invasivo y por
  mostrar un dato que no es el snapshot de la línea.
- **Dejar el detalle de venta sin columna de imagen.** Incumple §6 en dos de las nueve pantallas y
  deja la regla «una URL de imagen nunca se muestra sola» sin cumplir donde no hay URL.
- **Pintar el nombre del producto como si fuera la imagen.** Representar una imagen con texto no es
  un fallback neutro y confunde el estado vacío con el estado con dato.

## A quién afecta

- `front-admin-web-imagenes-panel`: es su regla de UI para las líneas de venta.
- `front-admin-web-ventas` y `front-admin-web-area-cliente`: cerrados; su modelo de
  `VentaDetalle` gana un campo **opcional** que no cambia ninguna prueba de campos requeridos.
- `sales-service` (`back-sales-*`): queda como hueco conocido para un plan futuro; no se toca.
- Contratos: ninguno. No se modifica ningún fichero de `docs/contracts/**`.
