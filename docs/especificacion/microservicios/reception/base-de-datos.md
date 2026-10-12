# Reception — base de datos, procedimientos y seeds

BD `reception_db`. Autoridad de ocupación/admisión. Sin FK a personas/habitaciones de otras DB. [Comunes](../../03-contrato-comun.md).

## Tablas

| Tabla | Columnas |
|---|---|
| room_gate | id_habitacion:int PK externo; id_hotel:int; estado:varchar(24) LIBRE/OCUPADA/LIMPIEZA_PENDIENTE/BLOQUEADA; id_recepcion:int? FK local; cleaning_cycle_id:uuid?; block_id:uuid?; room_version:bigint default0; hotel_version:bigint; updated_at:timestamptz |
| recepcion | id:int PK identity; id_hotel:int; id_habitacion:int; id_cliente:int; cliente_snapshot:jsonb; habitacion_snapshot:jsonb; tarifa:numeric(12,2)>0; fecha_entrada:date; fecha_salida:date; fecha_salida_confirmacion:timestamptz?; precio_inicial,adelanto,precio_restante,total_pagado,costo_penalidad:numeric(12,2); estado_estadia:varchar(16) ACTIVA/CERRANDO/CERRADA; observacion:varchar(500)?; archivada:boolean default false; version:bigint; created_at:timestamptz |
| operacion_consumo | operation_id:uuid PK; id_recepcion:int FK; id_venta:int externo; tipo:varchar(12) VENTA/PAGO/ANULACION; estado:varchar(12) ABIERTA/FINALIZADA; resultado:varchar(30)?; created_at/finished_at?:timestamptz; UNIQUE(id_venta,operation_id) |
| cierre | closure_id:uuid PK; id_recepcion:int FK; estado:varchar(20) VALIDANDO/CONFIRMANDO/COMPLETADO/RECHAZADO; request_hash:char(64); request:jsonb inmutable; cobro_declarado,penalidad:numeric(12,2); calculo:jsonb?; receipt_sales:jsonb?; cleaning_cycle_id:uuid; attempts:int default0; next_attempt_at:timestamptz; last_error_code:varchar(80)?; completed_at:timestamptz? |
| bloqueo_habitacion | block_id:uuid PK; id_habitacion:int; id_hotel:int; motivo:varchar(300); request_hash:char(64); estado:varchar(12) ACTIVO/LIBERADO/RECHAZADO; error_code:varchar(80)?; created_at:timestamptz; released_at:timestamptz? |
| api_idempotency,outbox,inbox,audit_log | Comunes |

Cierre RECHAZADO se conserva junto con su respuesta409; nuevo intento usa nueva clave. `calculo` es null en VALIDANDO/RECHAZADO y obligatorio desde CONFIRMANDO: `{alojamientoTotal,saldoAlojamiento,consumosPagadosAntes,consumosPendientes,consumosTotal,cobroAhora,totalGeneral,expectedPendingTotal}`. alojamientoTotal=precioInicial+penalidad; saldoAlojamiento=alojamientoTotal-adelanto; consumosTotal=consumosPagadosAntes+consumosPendientes; cobroAhora=saldoAlojamiento+consumosPendientes; totalGeneral=alojamientoTotal+consumosTotal; expectedPendingTotal=consumosPendientes. El evento final asigna consumosPagadosAlCerrar=consumosPendientes. El cálculo se congela antes de invocar Sales; no se recalcula en CONFIRMANDO. COMPLETADO exige receipt_sales y completed_at. Nunca borrar un cierre para recuperarlo.

Constraints: fechaSalida>=fechaEntrada; dinero>=0; adelanto<=precioInicial; gate OCUPADA requiere idRecepcion; LIMPIEZA_PENDIENTE requiere ciclo; BLOQUEADA requiere block. Índice único parcial:

```sql
CREATE UNIQUE INDEX uq_recepcion_habitacion_activa
ON recepcion (id_habitacion)
WHERE estado_estadia IN ('ACTIVA', 'CERRANDO');

CREATE UNIQUE INDEX uq_cierre_vigente
ON cierre (id_recepcion)
WHERE estado <> 'RECHAZADO';
```

Índices recepcion(id_hotel,estado_estadia), (id_cliente,created_at), operacion_consumo(id_recepcion,estado), cierre(estado,next_attempt_at). FK circular gate→recepción no obliga recepción→gate; la función controla consistencia.

## Procedimientos / funciones

| Firma | Invariante |
|---|---|
| fn_checkin(p_request jsonb,p_cliente_snapshot jsonb,p_habitacion_snapshot jsonb) RETURNS int | Lock gate LIBRE; calcula tarifa/noches; inserta ACTIVA; gate OCUPADA; roomVersion++ |
| sp_recepcion_actualizar(p_id int,p_salida date,p_observacion varchar,p_version bigint) | Solo ACTIVA, misma identidad/habitación; recalcula precio, no adelanto/pagos; rechaza409 si nuevo precioInicial<adelanto |
| sp_operacion_admitir(p_operation uuid,p_recepcion int,p_venta int,p_tipo varchar) | Lock recepción y luego operación; exige ACTIVA; valida tupla; rechazo tardío queda FINALIZADA/RECHAZADA; no revive una lápida |
| sp_operacion_finalizar(p_operation uuid,p_recepcion int,p_venta int,p_tipo varchar,p_resultado varchar) | Mismo orden de locks; inbox + upsert FINALIZADA con tupla completa; acepta llegada previa a admitir; resultado contradictorio se rechaza; no cambia dinero |
| sp_cierre_iniciar(p_closure uuid,p_recepcion int,p_request jsonb) | Lock ACTIVA y cero ABIERTAS; CERRANDO + cierre VALIDANDO; persiste solicitud/clave |
| sp_cierre_rechazar_monto(p_closure uuid) | CAS solo VALIDANDO→RECHAZADO; vuelve ACTIVA auditado; conserva cierre y respuesta409 |
| sp_cierre_preparar_confirmacion(p_closure uuid,p_calculo jsonb) | CAS VALIDANDO→CONFIRMANDO; persiste cálculo validado e inmutable antes del HTTP |
| sp_cierre_completar(p_closure uuid,p_receipt jsonb) | Solo CONFIRMANDO; valida recibo contra cálculo congelado; una vez: COMPLETADO, CERRADA + gate LIMPIEZA_PENDIENTE, roomVersion++ y outbox |
| sp_habitacion_bloquear(p_block uuid,p_habitacion int,p_hotel int,p_motivo varchar) | Lock gate; persiste ACTIVO si LIBRE y bloquea; si no, RECHAZADO/409 durable. Mismo block+hash conserva decisión aun si gate cambia; payload distinto409 |
| sp_habitacion_habilitar(p_habitacion int,p_ciclo uuid?,p_block uuid?) | Solo libera correlación actual; no toca estadías activas |
| sp_recepcion_archivar(p_id int,p_version bigint) | Solo CERRADA, archivada=true; no borrar importes/snapshots |
| sp_gate_inicializar(p_habitacion int,p_hotel int,p_fisico varchar,p_version bigint) | Bootstrap/alta de catálogo; solo crea gate ausente LIBRE si LISTA/activo; no sobreescribe un gate existente |

Bootstrap de habitación no admisible crea gate BLOQUEADA con block_id determinista de migración y bloqueo local auditado; nunca inventa LIBRE. En migración de ocupadas, se carga gate OCUPADA con su recepción antes de habilitar tráfico.

Las funciones trabajan con datos ya verificados por Feign. El service escribe eventos de salida en la misma transacción; jamás hace llamadas remotas desde el procedimiento.

## Seeds

No estadías ni cierres activos en seed base. Gate se crea al consumir las6 HabitacionCreada del seed hotel; antes no se admite check-in. Para tests aislados sí cargar gates101,102,201,202,301,302 LIBRE usando fixture equivalente a evento, no en paralelo con broker.

Demo por API, no SQL cruzado: cliente100 y habitación101, fechaEntrada=fecha actual en Lima, fechaSalida=mañana, tarifa70,adelanto20. Esperado precioInicial70/saldo50. La API genera idRecepcion; los tests guardan ese ID, no asumen1001 del ejemplo.

No resetear gates/recepciones con seed reejecutable. Pruebas de limpieza/doble check-in usan transacciones/fixtures en DB efímera.

## Validación esencial

Dos check-ins101→201+409; room101 sede1 y room201 sede2 independientes. Rabbit detenido no libera gate después de cierre. Ciclo viejo ignorado. Mantenimiento concurrente/check-in: solo uno gana. CERRANDO bloquea ventas; finalización duplicada no cambia resultados. Finalizar antes de admitir crea fila completa con tipo; cierre CONFIRMANDO se recupera sin recalcular; bloqueo RECHAZADO no se admite al quedar libre después.
