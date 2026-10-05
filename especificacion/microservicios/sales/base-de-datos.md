# Sales — base de datos, procedimientos y seeds

BD `sales_db`. Carrito y ventas; NO tabla PRODUCTO con stock autoritativo. [Comunes](../../03-contrato-comun.md).

## Tablas

| Tabla | Columnas/restricciones |
|---|---|
| carrito | id:int PK identity; id_recepcion:int externo; id_cliente:int externo; id_hotel:int; id_producto:int externo; cantidad:int CHECK1..9999; created_at:timestamptz; version:bigint; UNIQUE(id_recepcion,id_producto) |
| venta | id:int PK identity; id_recepcion:int; id_cliente:int; id_hotel:int; estado:varchar(12) PENDIENTE/PAGADO; estado_operacion:varchar(24) PENDIENTE/CONFIRMADA/RECHAZADA/CANCELACION_PENDIENTE/CANCELADA; admitida:boolean default false; origen:varchar(12) NUEVA/LEGACY default NUEVA; inventory_version:bigint default0; total:numeric(12,2)>=0; request_items:jsonb; current_operation_id:uuid; request_hash:char(64); motivo_cancelacion:varchar(300)?; version:bigint; created_at/updated_at:timestamptz; paid_at:timestamptz?; closure_id:uuid? |
| detalle_venta | id:int PK identity; id_venta:int FK; id_producto:int externo; nombre_producto:varchar(120); cantidad:int>0; precio_unitario:numeric(12,2)>0; subtotal:numeric(12,2); UNIQUE(id_venta,id_producto); subtotal=cantidad*precio_unitario |
| operacion_venta | operation_id:uuid PK; id_venta:int FK; tipo:varchar(12) VENTA/PAGO/ANULACION; estado:varchar(24) PREPARANDO/ADMITIDA/DEBITO_SOLICITADO/TERMINAL; request:jsonb; actor_id:int; actor_rol:varchar(20); created_at:timestamptz; next_attempt_at:timestamptz; attempts:int |
| cierre_consumos | closure_id:uuid PK; id_recepcion:int UNIQUE; id_hotel:int; request_hash:char(64); total_pendiente:numeric(12,2); total_pagado_previo:numeric(12,2); receipt:jsonb; confirmed_at:timestamptz |
| api_idempotency,outbox,inbox,audit_log | Comunes |

Índice único parcial en operacion_venta(id_venta) WHERE estado<>'TERMINAL': solo un alta/pago/anulación en curso por venta. Índices venta(id_recepcion,estado_operacion,estado), venta(id_hotel,paid_at), operacion_venta(estado,next_attempt_at). `current_operation_id` referencia operacion_venta(operation_id) con FK DEFERRABLE INITIALLY DEFERRED. Se inserta venta con UUID conocido y luego operación en la misma transacción; el commit valida ambas. No usar FK circular inmediata ni dejar una venta sin operación. Mantener historial de operaciones, no sobrescribir petición anterior.

Estado PAGADO exige CONFIRMADA y paid_at; estados rechazado/cancelado no se cuentan como ingreso. Venta cancelada conserva líneas si hubo descuento y respuesta, además de request original.

## Funciones/procedimientos

| Firma | Invariante |
|---|---|
| fn_carrito_agregar(p_recepcion int,p_cliente int,p_hotel int,p_producto int,p_cantidad int) RETURNS carrito | Upsert incrementa cantidad y version; sin stock; límite9999 |
| sp_carrito_quitar(p_recepcion int,p_producto int) | Borra solo línea; nada que restituir |
| fn_venta_iniciar(p_recepcion int,p_cliente int,p_hotel int,p_items jsonb,p_operation uuid,p_pago varchar) RETURNS int | Venta inicia PENDIENTE tanto técnica como de pago; p_pago se guarda en request de la operación como intención, y solo se aplica al confirmar. No detalles autorizados por frontend |
| sp_venta_preparar_debito(p_venta int,p_operation uuid) | CAS venta PENDIENTE y operación PREPARANDO→DEBITO_SOLICITADO; admitida=true; commit antes del HTTP. Si pierde CAS, prohibido invocar inventory |
| sp_venta_confirmar(p_venta int,p_resultado_inventory jsonb) | CAS PENDIENTE→CONFIRMADA con operación DEBITO_SOLICITADO; copia precio snapshot/calcula total; pago autorizado solicitado; version++; outbox y finalizar operación |
| sp_venta_rechazar(p_venta int,p_motivo varchar) | CAS PENDIENTE→RECHAZADA solo sin débito iniciado o con rechazo definitivo de inventory; resultado remoto incierto exige compensación; operación TERMINAL + finalizar con tipo |
| sp_venta_solicitar_cancelacion(p_venta int,p_operation uuid,p_motivo varchar) | CAS desde PENDIENTE, o CONFIRMADA impagada con admisión ANULACION; outbox stock.restituir atomizado |
| sp_venta_cancelacion_completar(p_venta int,p_inventory_version bigint) | Solo CANCELACION_PENDIENTE→CANCELADA tras RESTITUIDO; outbox snapshot y finalizar operación |
| sp_venta_pagar(p_venta int,p_operation uuid,p_version bigint) | Solo CONFIRMADA/PENDIENTE; misma transacción registro PAGADO y evento; no altera precio/items |
| fn_cierre_resumen(p_recepcion int,p_hotel int) RETURNS jsonb | Totales CONFIRMADAS de esa estadía/sede; excluye rechazadas/canceladas/no admitidas; cero ventas devuelve totales0 |
| fn_cierre_confirmar(p_closure uuid,p_recepcion int,p_hotel int,p_expected numeric) RETURNS jsonb | Serializa con pg_advisory_xact_lock(7101,p_recepcion); busca receipt antes de recalcular; valida identidad/hash; sin receipt paga impagadas y guarda receipt único incluso si total0 |

ADMITIDA se usa para PAGO/ANULACION; VENTA pasa de PREPARANDO a DEBITO_SOLICITADO. Cada transición de venta bloquea también su operación actual. TERMINAL se persiste en el mismo commit que el efecto final/outbox; no lo cambia una respuesta tardía. Recepción solo se libera después de ese commit. Nunca marcar admitida=true desde un callback que perdió CAS.

Toda transición utiliza locks/CAS con comprobación de filas modificadas. No hacer “leer pendiente → esperar HTTP → guardar confirmada” incondicional.

La función legacy fn_agregaralcarrito es referenciada por Java pero no está definida en los SQL inspeccionados. Se reemplaza por fn_carrito_agregar aquí especificada; no se supone que descontaba stock. El descuento que sí existe está en sp_RegistrarVenta del monolito y se elimina de Sales.

## Seeds

Carrito, ventas, operaciones y cierres vacíos. Seeds de referencias en otras DB no se copian por SQL.

Fixture de integración por API:

1. Crear estadía cliente100/habitación101/sede1, guardar id real.
2. Agregar producto1 cantidad2 al carrito; inventory sigue20.
3. Vender mismas líneas, estado=PENDIENTE. Precio3.50, total7; stock18.
4. Reintentar misma clave: misma venta/stock18.
5. Cliente no puede marcar PAGADO. E puede pagar o cerrar estadía.
6. Otra venta pendiente, anular como personal y esperar CANCELADA: restituye solo esa venta.

Fechas de demo se calculan hoy; IDs retornados se guardan como variables de test. No insertar ventas pagadas semilla sin movimientos/cierres correlacionados.

## Validación

CAS entre reconciliador y respuesta HTTP confirma o cancela, nunca ambas; DELETE venta PAGADA409; tiempo de llamada desconocido202 con operación recuperable; dos checkouts mismo closureId no duplican pago; registro de operación/finalizar invertidos no deja recepción bloqueada para siempre.
