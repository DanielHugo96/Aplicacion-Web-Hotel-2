# Inventory — contrato API

[Comunes](../../03-contrato-comun.md). No REST público. A configura; E lee su sede. C consume exclusivamente GET /api/venta/catalogo de sales, que valida su estadía y llama al catálogo interno de inventory. /api/producto no acepta CLIENTE; no hay dependencia inventory→reception.

## REST

| Método/ruta | Acceso | Entrada | Éxito |
|---|---|---|---|
| GET /api/producto/listar · LEGACY | A/E | L,H | 200 Producto[] de sede |
| GET /api/producto/buscar/{id} · LEGACY | A/E | id,H | 200 Producto |
| POST /api/producto/registrar · LEGACY | A | ProductoAlta | 201 Producto con asignación inicial |
| PUT /api/producto/actualizar/{id} · LEGACY | A,V | H,ProductoUpdate | 200 Producto, no permite cantidad |
| DELETE /api/producto/eliminar/{id} · LEGACY | A,V | H | 200 baja en esa sede; no destruye movimientos |
| POST /api/producto/{id}/sedes · NUEVA | A | {idHotel,precio,cantidadInicial,umbralBajo} | 201 Producto de sede nueva |
| POST /api/producto/{id}/ajustes · NUEVA | A,V | {idHotel,delta,motivo} | 201 {ajusteId,cantidad,version} |
| GET /api/producto/{id}/movimientos · NUEVA | A/E | H,L,inicio?,fin? | 200 Movimiento[] |

Idempotency-Key en mutaciones. V corresponde a producto_sede.version; cambios del catálogo global mediante ProductoUpdate solo ADMIN y se serializan también con version de producto indicada como `productoVersion` en body. Una baja impide nuevas ventas, pero permite restituir stock de ventas previas. En ProductoUpdate, estado pertenece solo a producto_sede; nombre/detalle/imagenUrl son globales y precio/umbralBajo son de la sede indicada. productoVersion y V se validan juntos en la transacción antes de cualquier cambio. Producto.estado global queda activo en MVP, sin endpoint de baja global; DELETE afecta únicamente a la sede.

ProductoAlta: `{sku,nombre,detalle?,imagenUrl?,idHotel,precio,cantidadInicial,umbralBajo}`.
ProductoUpdate: `{nombre,detalle?,imagenUrl?,precio,umbralBajo,estado,productoVersion}`.
Precio>0; inicial>=0; delta entero distinto0, motivo1..300. Las cantidades de venta1..9999, hasta50 productos distintos.
Producto response: `{idProducto,sku,nombre,detalle,imagenUrl,idHotel,precio,cantidad,umbralBajo,estado,fechaCreacion,version,productoVersion}`.
Movimiento response es unión discriminada: `{tipo:"VENTA",idVenta,idHotel,estado,cantidad,precioUnitario,createdAt,updatedAt}` o `{tipo:"AJUSTE",ajusteId,idHotel,delta,motivo,createdAt}`. VENTA refleja el movimiento y su estado actual (DESCONTADO/RECHAZADO/RESTITUIDO), no dos débitos por tener dos timestamps. Sin detalle de producto —rechazo o lápida anterior al débito— no aparece en esta ruta por producto; sí en consulta interna por idVenta. inicio/fin opcionales ISO-8601, intervalo [inicio,fin) sobre createdAt; orden createdAt,id/tipo y paginación L.

## Feign entrante

| Método/ruta | Caller/scope | Entrada | data/status |
|---|---|---|---|
| GET /internal/productos | sales/stock:read | H,page,size,estado=true | 200 Producto[]; catálogo para clientes |
| GET /internal/productos/{id} | sales/stock:read | H | 200 Producto |
| POST /internal/stock/descontar | sales/stock:debit | Debito | 200 DebitoResultado |
| GET /internal/stock/movimientos/{idVenta} | sales/stock:read | idVenta | 200 {idVenta,idHotel,estado,resultado,version};404 sin registro |

Debito:

```json
{"idVenta":5001,"idHotel":1,"items":[{"idProducto":1,"cantidad":2}]}
```

No precios, nombres, total ni recepción necesaria para descontar. Inventory confía solo en sales autenticado, no permite token CLIENTE. Idempotency-Key de Feign estable por venta; además UNIQUE idVenta evita duplicados incluso con otra clave HTTP.

DebitoResultado:

```json
{"idVenta":5001,"idHotel":1,"estado":"DESCONTADO","total":7.00,"items":[{"idProducto":1,"nombreProducto":"Agua500ml","cantidad":2,"precioUnitario":3.50,"subTotal":7.00}],"version":1}
```

Dentro del envelope común. Mismo idVenta+hash→mismo resultado aunque cambie catálogo. Otro hash→409. STOCK_INSUFFICIENT,PRODUCT_INACTIVE,DEBIT_CANCELLED→409. Fallo transitorio503, resultado incierto recuperado por consulta/compensación.

Feign saliente: `GET /internal/hoteles/{id}` al crear asignación o activar producto_sede, scope hoteles:read. No necesita consultar reception para débito: responsabilidad de admisión de sales/reception.

## Kafka

Produce inventory.stock.v1, key `idHotel:idProducto`, aggregateVersion=producto_sede.version.
Payload `{idProducto,idHotel,sku,nombreProducto,precio,cantidad,umbralBajo,estado,version}`; estado efectivo producto.estado AND producto_sede.estado; no es una proyección del estado del hotel.

Produce inventory.movimiento.v1 MovimientoStockResuelto, key=idVenta, version del movimiento.
Payload `{idVenta,idHotel,estado,version}`. DESCONTADO/RECHAZADO/RESTITUIDO; sales solo finaliza compensación al ver RESTITUIDO para CANCELACION_PENDIENTE. Un DESCONTADO tardío jamás confirma una venta por sí solo.

No consume Kafka.

## Rabbit

Consume stock.restituir; no ruta REST humana de restitución. Consume con inbox, efecto y evento de resolución en misma transacción. Si mismatch idHotel con movimiento existente:DLQ/alerta, no mover stock ajeno. No produce Rabbit en este MVP.
