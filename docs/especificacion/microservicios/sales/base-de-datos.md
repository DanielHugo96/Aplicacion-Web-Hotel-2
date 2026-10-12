# Sales — base de datos, procedimientos y seeds

BD `sales_db`. Carrito y ventas; NO tabla PRODUCTO con stock autoritativo. [Comunes](../../03-contrato-comun.md).

## Tablas

| Tabla | Columnas/restricciones |
|---|---|
| carrito | id:int PK identity; id_recepcion:int externo; id_cliente:int externo; id_hotel:int; id_producto:int externo; cantidad:int CHECK 1..9999; created_at:timestamptz; version:bigint; UNIQUE(id_recepcion,id_producto) |
| venta | id:int PK identity; id_recepcion:int; id_cliente:int; id_hotel:int; estado:varchar(12) PENDIENTE/PAGADO; estado_operacion:varchar(24) PENDIENTE/CONFIRMADA/RECHAZADA/CANCELACION_PENDIENTE/CANCELADA; admitida:boolean default false; origen:varchar(12) NUEVA/LEGACY default NUEVA; inventory_version:bigint default 0; total:numeric(12,2)>=0; medio_pago:varchar(12)? EFECTIVO/TARJETA/OTRO; request_items:jsonb; current_operation_id:uuid; request_hash:char(64); motivo_cancelacion:varchar(300)?; version:bigint; created_at/updated_at:timestamptz; paid_at:timestamptz?; closure_id:uuid? |
| detalle_venta | id:int PK identity; id_venta:int FK; id_producto:int externo; nombre_producto:varchar(120); cantidad:int>0; precio_unitario:numeric(12,2)>0; subtotal:numeric(12,2); UNIQUE(id_venta,id_producto); subtotal=cantidad*precio_unitario |
| operacion_venta | operation_id:uuid PK; id_venta:int FK; tipo:varchar(12) VENTA/PAGO/ANULACION; estado:varchar(24) PREPARANDO/ADMITIDA/DEBITO_SOLICITADO/TERMINAL; request:jsonb; actor_id:int; actor_rol:varchar(20); created_at:timestamptz; next_attempt_at:timestamptz; attempts:int |
| cierre_consumos | closure_id:uuid PK; id_recepcion:int UNIQUE; id_hotel:int; request_hash:char(64); total_pendiente:numeric(12,2); total_pagado_previo:numeric(12,2); medio_pago:varchar(12) EFECTIVO/TARJETA/OTRO; receipt:jsonb; confirmed_at:timestamptz |
| api_idempotency, outbox, inbox, audit_log | Comunes |

Índice único parcial en operacion_venta(id_venta) WHERE estado<>'TERMINAL': solo un alta/pago/anulación en curso por venta. Índices venta(id_recepcion,estado_operacion,estado), venta(id_hotel,paid_at), operacion_venta(estado,next_attempt_at). `current_operation_id` referencia operacion_venta(operation_id) con FK DEFERRABLE INITIALLY DEFERRED. Se inserta venta con UUID conocido y luego operación en la misma transacción; el commit valida ambas. No usar FK circular inmediata ni dejar una venta sin operación. Mantener historial de operaciones, no sobrescribir petición anterior.

Estado PAGADO exige CONFIRMADA y paid_at; estados rechazado/cancelado no se cuentan como ingreso. Venta cancelada conserva líneas si hubo descuento y respuesta, además de request original.

## Funciones/procedimientos

| Firma | Invariante |
|---|---|
| fn_carrito_agregar(p_recepcion int,p_cliente int,p_hotel int,p_producto int,p_cantidad int) RETURNS carrito | Upsert incrementa cantidad y version; sin stock; límite 9999 |
| sp_carrito_quitar(p_recepcion int,p_producto int) | Borra solo línea; nada que restituir |
| fn_venta_iniciar(p_recepcion int,p_cliente int,p_hotel int,p_items jsonb,p_operation uuid,p_pago varchar) RETURNS int | Venta inicia PENDIENTE tanto técnica como de pago; p_pago se guarda en request de la operación como intención, y solo se aplica al confirmar. No detalles autorizados por frontend |
| sp_venta_preparar_debito(p_venta int,p_operation uuid) | CAS venta PENDIENTE y operación PREPARANDO → DEBITO_SOLICITADO; admitida=true; commit antes del HTTP. Si pierde CAS, prohibido invocar inventory |
| sp_venta_confirmar(p_venta int,p_resultado_inventory jsonb) | CAS PENDIENTE → CONFIRMADA con operación DEBITO_SOLICITADO; copia precio snapshot/calcula total; pago autorizado solicitado; version++; outbox y finalizar operación |
| sp_venta_rechazar(p_venta int,p_motivo varchar) | CAS PENDIENTE → RECHAZADA solo sin débito iniciado o con rechazo definitivo de inventory; resultado remoto incierto exige compensación; operación TERMINAL + finalizar con tipo |
| sp_venta_solicitar_cancelacion(p_venta int,p_operation uuid,p_motivo varchar) | CAS desde PENDIENTE, o CONFIRMADA impagada con admisión ANULACION; outbox stock.restituir atomizado |
| sp_venta_cancelacion_completar(p_venta int,p_inventory_version bigint) | Solo CANCELACION_PENDIENTE → CANCELADA tras RESTITUIDO; outbox snapshot y finalizar operación |
| sp_venta_pagar(p_venta int,p_operation uuid,p_version bigint,p_medio_pago varchar) | Solo CONFIRMADA/PENDIENTE; exige `EFECTIVO/TARJETA/OTRO`; misma transacción registro PAGADO, medio y evento; no altera precio/items |
| fn_cierre_resumen(p_recepcion int,p_hotel int) RETURNS jsonb | Totales CONFIRMADAS de esa estadía/sede; excluye rechazadas/canceladas/no admitidas; cero ventas devuelve totales 0 |
| fn_cierre_confirmar(p_closure uuid,p_recepcion int,p_hotel int,p_expected numeric,p_medio_pago varchar) RETURNS jsonb | Serializa con pg_advisory_xact_lock(7101,p_recepcion); busca receipt antes de recalcular; valida identidad/hash; sin receipt paga las impagadas (con el medio recibido) y guarda receipt único incluso si total 0 |

ADMITIDA se usa para PAGO/ANULACION; VENTA pasa de PREPARANDO a DEBITO_SOLICITADO. Cada transición de venta bloquea también su operación actual. TERMINAL se persiste en el mismo commit que el efecto final/outbox; no lo cambia una respuesta tardía. Recepción solo se libera después de ese commit. Nunca marcar admitida=true desde un callback que perdió CAS.

Toda transición utiliza locks/CAS con comprobación de filas modificadas. No hacer “leer pendiente → esperar HTTP → guardar confirmada” incondicional.

La función legacy fn_agregaralcarrito es referenciada por Java pero no está definida en los SQL inspeccionados. Se reemplaza por fn_carrito_agregar aquí especificada; no se supone que descontaba stock. El descuento que sí existe está en sp_RegistrarVenta del monolito y se elimina de Sales.

## DDL de referencia

Fuente única; los scripts Flyway se derivan de aquí:

```sql
CREATE TABLE carrito (
  id           int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  id_recepcion int         NOT NULL,
  id_cliente   int         NOT NULL,
  id_hotel     int         NOT NULL,
  id_producto  int         NOT NULL,
  cantidad     int         NOT NULL CHECK (cantidad BETWEEN 1 AND 9999),
  created_at   timestamptz NOT NULL DEFAULT now(),
  version      bigint      NOT NULL DEFAULT 1,
  UNIQUE (id_recepcion, id_producto)
);

CREATE TABLE venta (
  id                   int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  id_recepcion         int           NOT NULL,
  id_cliente           int           NOT NULL,
  id_hotel             int           NOT NULL,
  estado               varchar(12)   NOT NULL CHECK (estado IN ('PENDIENTE','PAGADO')),
  estado_operacion     varchar(24)   NOT NULL CHECK (estado_operacion IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELACION_PENDIENTE','CANCELADA')),
  admitida             boolean       NOT NULL DEFAULT false,
  origen               varchar(12)   NOT NULL DEFAULT 'NUEVA' CHECK (origen IN ('NUEVA','LEGACY')),
  inventory_version    bigint        NOT NULL DEFAULT 0,
  total                numeric(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  medio_pago           varchar(12)   CHECK (medio_pago IN ('EFECTIVO','TARJETA','OTRO')),
  request_items        jsonb         NOT NULL,
  current_operation_id uuid          NOT NULL,
  request_hash         char(64)      NOT NULL,
  motivo_cancelacion   varchar(300),
  version              bigint        NOT NULL DEFAULT 1,
  created_at           timestamptz   NOT NULL DEFAULT now(),
  updated_at           timestamptz   NOT NULL DEFAULT now(),
  paid_at              timestamptz,
  closure_id           uuid,
  CHECK (estado <> 'PAGADO' OR (estado_operacion = 'CONFIRMADA' AND paid_at IS NOT NULL))
);

CREATE TABLE operacion_venta (
  operation_id    uuid        PRIMARY KEY,
  id_venta        int         NOT NULL REFERENCES venta(id),
  tipo            varchar(12) NOT NULL CHECK (tipo IN ('VENTA','PAGO','ANULACION')),
  estado          varchar(24) NOT NULL CHECK (estado IN ('PREPARANDO','ADMITIDA','DEBITO_SOLICITADO','TERMINAL')),
  request         jsonb       NOT NULL,
  actor_id        int         NOT NULL,
  actor_rol       varchar(20) NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  attempts        int         NOT NULL DEFAULT 0
);

-- FK circular diferida: la venta conoce su operación y el commit valida ambas
ALTER TABLE venta
  ADD CONSTRAINT fk_venta_operacion FOREIGN KEY (current_operation_id)
  REFERENCES operacion_venta (operation_id) DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX uq_operacion_venta_en_curso ON operacion_venta (id_venta) WHERE estado <> 'TERMINAL';
CREATE INDEX ix_operacion_venta_recuperacion ON operacion_venta (estado, next_attempt_at);
CREATE INDEX ix_venta_recepcion ON venta (id_recepcion, estado_operacion, estado);
CREATE INDEX ix_venta_hotel_paid ON venta (id_hotel, paid_at);

CREATE TABLE detalle_venta (
  id              int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  id_venta        int           NOT NULL REFERENCES venta(id),
  id_producto     int           NOT NULL,
  nombre_producto varchar(120)  NOT NULL,
  cantidad        int           NOT NULL CHECK (cantidad > 0),
  precio_unitario numeric(12,2) NOT NULL CHECK (precio_unitario > 0),
  subtotal        numeric(12,2) NOT NULL,
  UNIQUE (id_venta, id_producto),
  CHECK (subtotal = cantidad * precio_unitario)
);

CREATE TABLE cierre_consumos (
  closure_id          uuid          PRIMARY KEY,
  id_recepcion        int           NOT NULL UNIQUE,
  id_hotel            int           NOT NULL,
  request_hash        char(64)      NOT NULL,
  total_pendiente     numeric(12,2) NOT NULL CHECK (total_pendiente >= 0),
  total_pagado_previo numeric(12,2) NOT NULL CHECK (total_pagado_previo >= 0),
  medio_pago          varchar(12)   NOT NULL CHECK (medio_pago IN ('EFECTIVO','TARJETA','OTRO')),
  receipt             jsonb         NOT NULL,
  confirmed_at        timestamptz   NOT NULL DEFAULT now()
);
```

`api_idempotency`, `outbox`, `inbox` y `audit_log` usan el DDL común de [`03-contrato-comun.md`](../../03-contrato-comun.md).

## Esquemas JSONB

`request_items` de venta (líneas pedidas; sin precios autorizados por el cliente):

```json
[{"idProducto":1,"cantidad":2}]
```

`request` de `operacion_venta` (intención original, inmutable): alta `{}`; pago `{"medioPago":"EFECTIVO"}`; anulación `{"motivo":"Error de pedido"}`. Nunca contiene precios, totales ni idCliente ajeno.

`receipt` de `cierre_consumos` (recibo del cierre de consumos):

```json
{"closureId":"66666666-6666-4666-8666-666666666666","idRecepcion":1001,"idHotel":1,"totalPagadoPrevio":0.00,"totalPagadoAhora":7.00,"totalConsumos":7.00,"medioPago":"EFECTIVO","ventasPagadas":[5001],"confirmedAt":"2026-10-05T16:10:00Z"}
```

Obligatorios todos los campos; importes numeric(12,2) con HALF_UP; `ventasPagadas` puede ser `[]` (cierre con cero consumos). El receipt se devuelve tal cual a reception en reintentos y no se recalcula.

## Seeds

Carrito, ventas, operaciones y cierres vacíos. Seeds de referencias en otras DB no se copian por SQL.

Fixture de integración por API:

1. Crear estadía cliente 100/habitación 101/sede 1, guardar id real.
2. Agregar producto 1 cantidad 2 al carrito; inventory sigue 20.
3. Vender mismas líneas, estado=PENDIENTE. Precio 3.50, total 7; stock 18.
4. Reintentar misma clave: misma venta/stock 18.
5. Cliente no puede marcar PAGADO. E puede pagar o cerrar estadía.
6. Otra venta pendiente, anular como personal y esperar CANCELADA: restituye solo esa venta.

Fechas de demo se calculan hoy; IDs retornados se guardan como variables de test. No insertar ventas pagadas semilla sin movimientos/cierres correlacionados.

## Validación

CAS entre reconciliador y respuesta HTTP confirma o cancela, nunca ambas; DELETE venta PAGADA 409; tiempo de llamada desconocido 202 con operación recuperable; dos checkouts mismo closureId no duplican pago; registro de operación/finalizar invertidos no deja recepción bloqueada para siempre.
