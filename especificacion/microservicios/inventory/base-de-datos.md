# Inventory — base de datos, procedimientos y seeds

BD `inventory_db`. Único dueño del stock; carrito no lo modifica. [Comunes](../../03-contrato-comun.md).

## Tablas

| Tabla | Columnas/restricciones |
|---|---|
| producto | id:int PK identity; sku:varchar(40) UNIQUE; nombre:varchar(120); detalle:varchar(500)?; imagen_url:varchar(500)?; estado:boolean; version:bigint; created_at:timestamptz |
| producto_sede | id_producto:int FK; id_hotel:int externo; precio:numeric(12,2)>0; cantidad:int>=0; umbral_bajo:int>=0; estado:boolean; version:bigint; PK(id_producto,id_hotel) |
| movimiento_venta | id_venta:int PK externo; id_hotel:int; request_hash:char(64)?; estado:varchar(16) DESCONTADO/RECHAZADO/RESTITUIDO; resultado:jsonb?; version:bigint; created_at/updated_at:timestamptz |
| movimiento_detalle | id_venta:int FK; id_producto:int FK; cantidad:int>0; precio_unitario:numeric(12,2); subtotal:numeric(12,2); nombre_snapshot:varchar(120); PK(id_venta,id_producto) |
| ajuste_stock | id:uuid PK; id_producto:int; id_hotel:int; delta:int CHECK delta<>0; motivo:varchar(300); actor_id:int; saldo_resultante:int>=0; created_at:timestamptz; FK local producto_sede |
| api_idempotency,outbox,inbox,audit_log | Comunes |

Restitución sin descuento crea movimiento RESTITUIDO sin detalles/hash; futuros descuentos de esa venta se rechazan. Resultado de un movimiento DESCONTADO se conserva al restituir para auditoría, jamás se borra. Cantidades nunca negativas.

Índices producto_sede(id_hotel,estado), movimientos(id_hotel,created_at). No FK a sales/reception/hotel. Activación de producto en sede consulta hotel por Feign.

## Funciones/procedimientos

| Firma | Resultado/invariante |
|---|---|
| fn_stock_descontar(p_venta int,p_hotel int,p_items jsonb,p_hash char(64)) RETURNS jsonb | Idempotencia venta+hash; lock movimiento primero; lock productos y stock en orden id; check activo/cantidad; todos o ninguno; devuelve precios/nombres autoritativos |
| sp_stock_restituir(p_venta int,p_hotel int,p_motivo varchar) | Lock mismo movimiento; devuelve cantidades originales solo una vez, o crea lápida; emite MovimientoStockResuelto |
| fn_stock_ajustar(p_id uuid,p_producto int,p_hotel int,p_delta int,p_motivo varchar,p_actor int) RETURNS int | Lock stock, valida saldo>=0, registra ajuste; no permite reemplazo arbitrario de cantidad |
| sp_producto_sede_crear(p_producto int,p_hotel int,p_precio numeric,p_inicial int,p_umbral int) | Alta única; stock inicial>=0; registra origen semilla/alta |
| fn_productos_bajo_stock(p_hotel int,p_limite int) RETURNS TABLE(id_producto int,cantidad int) | Consulta local técnica; reportes externos van a reporting |

No hacer SELECT cantidad seguido de UPDATE sin lock. Implementación usa fila bloqueada y/o UPDATE condicional `cantidad >= solicitada` y verifica filas afectadas. Una línea insuficiente revierte todas las deducciones; registrar RECHAZADO en movimiento sin dejar descuentos parciales. IDs duplicados en items se rechazan400 antes de SQL (no sumar ocultamente).

JPA catálogo y precios; cualquier update de producto que cambie snapshot publicado incrementa version de cada producto_sede afectado y emite ProductoStockSnapshot por sede. Precio no altera ventas históricas.

## Seeds

Productos1 AGUA-500/Agua500ml,2 SNACK-01/Snack,3 ASEO-01/Kit de aseo. Estado activo, imágenes locales de demo.

| idHotel | idProducto1: precio/cantidad | idProducto2 | idProducto3 |
|---:|---|---|---|
| 1 | 3.50 / 20 | 8.00 / 10 | 5.00 / 5 |
| 2 | 3.50 / 20 | 8.00 / 10 | 5.00 / 5 |
| 3 | 3.50 / 20 | 8.00 / 10 | 5.00 / 5 |

Umbral_bajo=5, version1. Alerta de bajo stock: cantidad<=umbral. Movimientos/ajustes vacíos salvo trazabilidad del stock inicial. Bootstrap9 ProductoStockSnapshot; repetir seed no repone ventas realizadas. Pruebas usan fixtures aparte.

## Verificación

20 unidades y dos ventas simultáneas de15 → solo una descuenta; stock5. Venta multítem con una línea insuficiente deja stock intacto en ambas. Restituir dos veces no duplica stock. Restituir antes de descuento lo bloquea; baja/precio concurrentes se serializan con débito y la respuesta conserva precio usado.
