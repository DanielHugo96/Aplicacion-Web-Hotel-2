# Reception — contrato API

[Comunes](../../03-contrato-comun.md). Todas privadas. A/E limitados por sede; C solo lectura de su propia estadía. No reservas futuras.

## REST

| Método y ruta | Acceso | Entrada | Resultado |
|---|---|---|---|
| GET /api/recepcion/listar · LEGACY | A/E | L,H,estadoEstadia? | 200 Recepcion[] |
| GET /api/recepcion/{id} · LEGACY | A/E/C | id | 200 Recepcion; C propia |
| GET /api/recepcion/cliente/{idCliente} · LEGACY | A/E/C | L,estadoEstadia?; E idHotel | 200 Recepcion[]; C id=sub; E filtra sede |
| GET /api/recepcion/habitacion-activa/{idHabitacion} · LEGACY | A/E | idHabitacion | 200 Recepcion o data=null; valida sede aun si no hay estadía |
| POST /api/recepcion/registrar · LEGACY | A/E | CheckIn | 201 Recepcion;409 ocupado/no limpia |
| PUT /api/recepcion/actualizar/{id} · LEGACY | A/E,V | fechaSalida,observacion? | 200 Recepcion; solo ACTIVA |
| GET /api/recepcion/{id}/cotizacion-salida · NUEVA | A/E | costoPenalidad decimal>=0 default0 | 200 Cotizacion, informativa |
| POST /api/recepcion/registrar-salida · LEGACY | A/E | Salida | 200 Recepcion cerrada o202 Recepcion CERRANDO |
| DELETE /api/recepcion/eliminar/{id} · LEGACY | A,V | id | 200 archivada;409 si no CERRADA |

Idempotency-Key en altas/cierre/PUT/DELETE. El idHabitacion de salida se valida contra recepción; no se usa para cerrar otra habitación. Una estadía cerrada no vuelve a ACTIVA por PUT.

## Request y response

CheckIn:

```json
{"idHotel":1,"idCliente":100,"idHabitacion":101,"fechaSalida":"2026-10-05","adelanto":20.00,"observacion":"Ingreso presencial"}
```

FechaEntrada se fija hoy Lima por servidor. Si cliente envía fechaEntrada distinta o precios/nombre/documento,400; primero crear cliente en identity. FechaSalida hoy o posterior, estadía máxima365 noches; mínimo de cobro una noche. Adelanto no negativo y <=alojamiento.

Salida:

```json
{"idRecepcion":1001,"idHabitacion":101,"costoPenalidad":0.00,"totalPagado":57.00}
```

totalPagado en request significa efectivo/pago manual recibido AHORA, incluyendo consumo pendiente, no total histórico. El panel presenta esta etiqueta explícita. Backend verifica contra cálculo estable; no lo toma como verdad del saldo.

Recepcion: `{idRecepcion,idHotel,idCliente,idHabitacion,numero,categoriaNombre,pisoNombre,detalleHabitacion,precioHabitacion,nombre,apellido,tipoDocumento,documento,correo,fechaEntrada,fechaSalida,fechaSalidaConfirmacion,precioInicial,adelanto,precioRestante,totalPagado,costoPenalidad,observacion,estado,estadoEstadia,archivada,version,closureId,cleaningCycleId}`. `estado` legacy derivado true para ACTIVA/CERRANDO; false para CERRADA. totalPagado de response es total histórico del alojamiento, no mezcla consumo; detalle del cierre en campo adicional `cierre` con el desglose de Cotizacion y cobroAhora. C recibe el propio snapshot; no otro huésped.

Cotizacion: `{idRecepcion,alojamiento:70,adelanto:20,penalidad:0,saldoAlojamiento:50,consumosPagados:0,consumosPendientes:7,cobroAhora:57,moneda:"PEN",calculadoEn}`. El servidor redondea a2 decimales, rechaza discrepancia con409 y nueva cotización.

## Feign entrante

Acceso solo máquina y scope del [común](../../03-contrato-comun.md). POST internos llevan Idempotency-Key estable derivada de operationId/blockId.

| Método/ruta | Caller | Request | Response |
|---|---|---|---|
| GET /internal/recepciones/{id} | sales,recepciones:read | — | 200 {idRecepcion,idHotel,idCliente,idHabitacion,estadoEstadia,version} |
| POST /internal/recepciones/{id}/operaciones | sales,operaciones:write | {operationId,idVenta,idHotel,tipo,actorId,actorRol} | 200 {operationId,estado:"ABIERTA"};409 no ACTIVA/finalizada |
| POST /internal/habitaciones/{id}/bloqueos | hotel,bloqueos:write | {blockId,idHotel,motivo} | 200 {blockId,idHabitacion,estado:"BLOQUEADA"};409 no libre |

Caller sales valida JWT humano/sede y pasa actor auditado; si actorRol=CLIENTE reception comprueba idCliente=actorId y tipo=VENTA. No confundir token máquina con acceso humano sin permisos.

Feign saliente: identity GET cliente; hotel GET habitación; sales GET resumen cierre y POST confirmar cierre. Audiencias y scopes restringidos. Recuperación de cierre usa el mismo POST confirmar, que devuelve receipt persistido. No existe PUT externo para forzar disponibilidad saltando esos flujos.

## Kafka

Publica reception.recepcion.v1 con key=idHabitacion y aggregateVersion=roomVersion; eventos confirmados tras cambio de estadía. Snapshot payload:

`{idRecepcion,idHotel,idHabitacion,idCliente,numero,nombreCliente,fechaEntrada,fechaSalida,fechaSalidaConfirmacion,estadoEstadia,tarifa,precioInicial,adelanto,costoPenalidad,totalPagado,observacion:null,archivada,version,roomVersion,closureId,cleaningCycleId,cierre,ocupacionActual:{idRecepcion,estadoGate,cleaningCycleId,blockId}}`.

ocupacionActual describe el gate actual, no necesariamente la estadía histórica del evento. Archivar una estadía vieja mientras hay otra activa no publica habitación libre. Cada evento incrementa roomVersion bajo lock; version del payload corresponde a la estadía. No documento/correo/observaciones personales en Kafka. En SalidaRegistrada, cierre contiene `{alojamientoTotal,consumosPagadosAntes,consumosPagadosAlCerrar,consumosTotal,cobroAhora,totalGeneral,closedAt}`. Pagos de alojamiento y ventas no se suman dos veces en reporting.

Consume hotel.habitacion.v1: inicializa gates; HabitacionLista libera solo ciclo; HabitacionHabilitada libera solo block. Nunca acepta física LISTA de un snapshot genérico como permiso de limpiar/cancelar un gate.

## Rabbit

Produce limpieza.solicitada, notificacion.checkin y notificacion.checkout.
Consume recepcion.operacion-finalizar, con inbox+lápida. No consulta stock desde listener. Ver payloads en [mensajería](../../05-mensajeria.md).

## Errores/pruebas

ROOM_NOT_READY,ROOM_OCCUPIED,ROOM_NOT_SYNCED,OPERATIONS_IN_PROGRESS,AMOUNT_MISMATCH,STAY_NOT_ACTIVE →409 (ROOM_NOT_SYNCED puede503 transitorio). DEPENDENCY_UNAVAILABLE→503.
Cliente intentando check-in403; fecha futura de entrada400; cierre con venta en vuelo409; timeout después de sales receipt202 y posterior200 con misma clave, una sola salida.
