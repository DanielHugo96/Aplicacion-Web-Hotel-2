# Sales — contrato API

[Comunes](../../03-contrato-comun.md). A/E por sede; C solo recepciones propias. El rol/sede se valida antes de cada Feign y se revalida al admitir operación. Precio autoritativo de inventory.

## REST: catálogo privado y carrito

| Método/ruta | Acceso | Entrada | Éxito |
|---|---|---|---|
| GET /api/venta/catalogo · NUEVA | A/E/C | idRecepcion, page, size | 200 Producto[] de esa sede; recepción ACTIVA |
| POST /api/carrito/agregar · LEGACY | A/E/C | query idRecepcion, idProducto, cantidad; sin body | 200 CarritoItem |
| GET /api/carrito/listar/{idRecepcion} · LEGACY | A/E/C | idRecepcion | 200 CarritoItem[] |
| DELETE /api/carrito/eliminar · LEGACY | A/E/C | query idRecepcion, idProducto; Idempotency-Key | 204 |

Agregar y eliminar exigen Idempotency-Key para que un retry no incremente ni borre dos veces. Cantidad 1..9999, siempre se suma a línea actual; cliente no pasa idPersona ni precio. Para cambiar cantidad se elimina línea y agrega nueva cantidad; no se necesita endpoint extra en MVP.

CarritoItem: `{idCarrito,idRecepcion,idPersona,idHotel,idProducto,nombreProducto,precioUnitario,imagenUrl,cantidad,fechaRegistro,version}`. PrecioUnidad obtenido al consultar inventory; si caído 503 sin fallback de precio inventado. Lectura de estadía CERRADA retorna[] tras validar propiedad; si CERRANDO también solo lectura.

## REST: ventas

| Método/ruta | Acceso | Entrada | Éxito |
|---|---|---|---|
| GET /api/venta · LEGACY | A/E | L, H, estado?, estadoOperacion? | 200 Venta[] |
| GET /api/venta/buscar/{id} · LEGACY | A/E/C | id | 200 Venta; C propia |
| GET /api/venta/recepcion/{idRecepcion} · LEGACY | A/E/C | L | 200 Venta[] propia/sede |
| POST /api/venta · LEGACY | A/E/C | VentaAlta | 201 CONFIRMADA o 202 en recuperación |
| PUT /api/venta/{id} · LEGACY | A/E, V | {estado:"PAGADO",medioPago} | 200 Venta o 202 operación en curso |
| DELETE /api/venta/{id} · LEGACY | A/E, V | query motivo requerido 1..300 | 202 CANCELACION_PENDIENTE; 200 si ya CANCELADA |
| GET /api/venta/operaciones/{operationId} · NUEVA | A/E/C | operationId | 200 {operationId,idVenta,tipo,estado,estadoOperacion} |

Ruta operaciones solo C si operación de su venta; no expone payload interno. Historial no desaparece en DELETE. Venta LEGACY sin movimiento conciliado devuelve 409 LEGACY_MOVEMENT_UNVERIFIED; no emitir restitución sin evidencia. No hay CRUD independiente de detalle: líneas inmutables tras confirmar.

VentaAlta:

```json
{"idRecepcion":1001,"estado":"PENDIENTE","detalles":[{"idProducto":1,"cantidad":2}]}
```

A/E pueden enviar PAGADO como registro de pago manual al crear; en ese caso `medioPago` (`EFECTIVO/TARJETA/OTRO`) es obligatorio y el pago se registra al confirmar la venta. C solo PENDIENTE o campo omitido. idHotel/idCliente se derivan de recepción, no se aceptan del body. No aceptar total, precioUnitario, subTotal, nombreProducto, estadoOperacion ni idVenta. Hasta 50 líneas de IDs distintos.

Venta:

```json
{"idVenta":5001,"idRecepcion":1001,"idCliente":100,"idHotel":1,"total":7.00,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"55555555-5555-4555-8555-555555555555","medioPago":null,"version":2,"fechaCreacion":"2026-10-04T15:05:00Z","paidAt":null,"detalles":[{"idDetalleVenta":1,"idProducto":1,"nombreProducto":"Agua500ml","cantidad":2,"precioUnitario":3.50,"subTotal":7.00}]}
```

`medioPago` queda `null` hasta que la venta se paga y es obligatorio al registrar cada cobro (creación con PAGADO o `PUT .../{id}`); históricos migrados usan `null = NO_REGISTRADO`. 202 misma forma, estadoOperacion PENDIENTE/CANCELACION_PENDIENTE; Location apunta GET buscar/id y Retry-After 2. Si ya existe venta por clave, no crea nuevo ID. 409 stock, estadía cerrando, cobro no permitido; 503 dependencia antes de cualquier operación durable.

Un pago/anulación confirmado requiere operación de recepción admitida para evitar carrera con checkout. Pago solo desde CONFIRMADA/PENDIENTE; pago repetido idempotente. Anulación nunca hace HTTP restituir: emite comando durable.

## Feign entrante: cierre de consumos

| Método/ruta | Caller | Entrada | data |
|---|---|---|---|
| GET /internal/cierres/{idRecepcion}/resumen | reception, cierres:write | idRecepcion + query idHotel obligatorio | 200 {idRecepcion,idHotel,totalPendiente,totalPagado,ventasPendientes:[id],hayOperacionesAbiertas} |
| POST /internal/cierres/confirmar | reception, cierres:write | {closureId,idRecepcion,idHotel,expectedPendingTotal,medioPago} | 200 Receipt |

Idempotency-Key=closureId. Receipt `{closureId,idRecepcion,idHotel,totalPagadoPrevio,totalPagadoAhora,totalConsumos,medioPago,ventasPagadas:[id],confirmedAt}`. Una recepción tiene un solo cierre de consumo. Mismo cierre/request retorna receipt ANTES de recalcular ventas ahora pagadas; otro cierre para misma recepción 409. Si no hay receipt, rechazar expectedPendingTotal distinto sin mutar. Cero ventas devuelve totales 0 y permite receipt de importe 0: idHotel procede del caller reception y debe coincidir con cualquier fila existente. El `medioPago` recibido se aplica a las ventas que se marcan PAGADO en esa confirmación y queda en el receipt; los históricos sin dato usan null=NO_REGISTRADO. Resumen excluye ventas no admitidas; una operación admitida aún no terminal devuelve 409.

No contar ventas técnicas PENDIENTE sin admisión: no llegaron a modificar inventario. Una venta admitida no terminal hace 409; recepción normalmente ya impide llegar aquí por el gate de operaciones. Sales confía solo en caller reception autenticado para el cierre; el usuario no puede invocar esta API directamente.

## Feign saliente

- reception GET recepción para sede/propietario; POST operaciones para VENTA/PAGO/ANULACION.
- inventory GET catálogo/producto para carrito; POST descontar y GET movimiento para recuperación.
- No invoca hotel ni identity. No joins remotos para cada reporte.

## Kafka

Produce sales.venta.v1, VentaSnapshot key=idVenta/version de venta:
`{idVenta,idRecepcion,idCliente,idHotel,estado,estadoOperacion,total,medioPago,createdAt,paidAt,closureId,version,detalles:[{idProducto,nombreProducto,cantidad,precioUnitario,subTotal}]}`.
Emitir también cuando se paga desde cierre o se cancela; no convertir un timeout en VentaCONFIRMADA.

Consume inventory.movimiento.v1 para compensaciones. Consume reception.recepcion.v1 SalidaRegistrada para limpiar carrito. El evento no marca ventas pagadas: el pago ocurrió por API interna del cierre y tiene receipt.

## Rabbit

Produce stock.restituir y recepcion.operacion-finalizar. Este último incluye tipo=VENTA/PAGO/ANULACION, tras estado terminal persistido de la operación, incluida RECHAZADA aunque la admisión pudiera no haber llegado. Tras PAGO_REGISTRADO se refiere al operationId del pago, no al de alta.

No consume Rabbit. Todas las publicaciones mediante outbox con clave estable. Ver [flujos](../../04-flujos.md) para CAS y lápidas.

## Pruebas

Cliente enviando precio 400; estado PAGADO 403; venta ajena 404. Dos solicitudes misma clave y payload crean una venta; misma clave distinto payload 409. Pagar/anular a la vez solo una transición válida. Cerrar estancia con venta admitida 409. Fallar after-debit/before-commit termina CONFIRMADA o CANCELADA con stock correcto. Pago sin `medioPago` 400; el `medioPago` registrado queda disponible para el desglose del reporte de cobros. Catálogo de errores: [`errors.md`](../../../contracts/errors.md).
