# Reception — contrato API

[Comunes](../../03-contrato-comun.md). Todas privadas. A/E limitados por sede; C solo lectura de su propia estadía. No reservas futuras.

## REST

| Método y ruta | Acceso | Entrada | Resultado |
|---|---|---|---|
| GET /api/recepcion/listar · LEGACY | A/E | L, H, estadoEstadia? | 200 Recepcion[] |
| GET /api/recepcion/{id} · LEGACY | A/E/C | id | 200 Recepcion; C propia |
| GET /api/recepcion/cliente/{idCliente} · LEGACY | A/E/C | L, estadoEstadia?; E idHotel | 200 Recepcion[]; C id=sub; E filtra sede |
| GET /api/recepcion/habitacion-activa/{idHabitacion} · LEGACY | A/E | idHabitacion | 200 Recepcion o data=null; valida sede aun si no hay estadía |
| POST /api/recepcion/registrar · LEGACY | A/E | CheckIn | 201 Recepcion; 409 ocupado/no limpia |
| PUT /api/recepcion/actualizar/{id} · LEGACY | A/E, V | fechaSalida, observacion? | 200 Recepcion; solo ACTIVA |
| GET /api/recepcion/{id}/cotizacion-salida · NUEVA | A/E | costoPenalidad decimal>=0 default 0 | 200 Cotizacion, informativa |
| POST /api/recepcion/registrar-salida · LEGACY | A/E | Salida | 200 Recepcion cerrada o 202 Recepcion CERRANDO |
| POST /api/recepcion/{id}/anular-checkin · NUEVA | A, V | motivo:string 1..300 | 200 Recepcion CERRADA archivada; 409 con ventas admitidas/cierre en curso |
| DELETE /api/recepcion/eliminar/{id} · LEGACY | A, V | id | 200 archivada; 409 si no CERRADA |

Idempotency-Key en altas/cierre/anulación de check-in/PUT/DELETE. El idHabitacion de salida se valida contra recepción; no se usa para cerrar otra habitación. Una estadía cerrada no vuelve a ACTIVA por PUT. Acortar fechaSalida no puede reducir precioInicial por debajo del adelanto (409 ADVANCE_EXCEEDS_NEW_TOTAL); no se inventa un reembolso.

Política operativa de horario: check-in desde las 15:00 y check-out hasta las 12:00 (America/Lima); una noche es la diferencia de días entre `fechaEntrada` y `fechaSalida`; las estancias por horas están fuera de alcance. Las horas son política, no dato: no cambian precio ni esquema, y la ocupación compara fechas locales.

Reglas de estadía: un cliente **puede** tener estadías ACTIVA/CERRANDO simultáneas en habitaciones o sedes distintas (familia/grupo); el invariante único es por habitación (índice parcial de `base-de-datos.md`). Cambiar de habitación durante la estadía está **fuera de alcance del MVP**: se cierra la estadía con su cobro y se crea una nueva; el sistema no mueve huéspedes entre habitaciones.

## Request y response

CheckIn:

```json
{"idHotel":1,"idCliente":100,"idHabitacion":101,"fechaSalida":"2026-10-05","adelanto":20.00,"medioPago":"EFECTIVO","observacion":"Ingreso presencial"}
```

FechaEntrada se fija hoy Lima por servidor. Si cliente envía fechaEntrada distinta o precios/nombre/documento, 400; primero crear cliente en identity. FechaSalida hoy o posterior, estadía máxima 365 noches; mínimo de cobro una noche. Adelanto no negativo y <=alojamiento. `medioPago` obligatorio cuando `adelanto > 0` (`EFECTIVO/TARJETA/OTRO`); sin cobro inicial se omite.

Salida:

```json
{"idRecepcion":1001,"idHabitacion":101,"costoPenalidad":0.00,"montoRecibido":57.00,"medioPago":"EFECTIVO"}
```

`montoRecibido` es el pago manual recibido AHORA, incluyendo consumo pendiente; **no** es el total histórico. `medioPago` es obligatorio en cada cobro. El panel presenta la etiqueta explícita “Monto recibido ahora”. Backend verifica contra el cálculo estable; no lo toma como verdad del saldo. `costoPenalidad` solo lo fijan A/E, con `0 <= costoPenalidad <= precioInicial`; si es > 0 se exige `motivoPenalidad` (1..300) y queda en el request del cierre y en la auditoría. La cotización muestra el desglose y el panel lo resalta.

Recepcion: `{idRecepcion,idHotel,idCliente,idHabitacion,numero,categoriaNombre,pisoNombre,detalleHabitacion,precioHabitacion,nombre,apellido,tipoDocumento,documento,correo,fechaEntrada,fechaSalida,fechaSalidaConfirmacion,precioInicial,adelanto,medioPagoAdelanto,precioRestante,totalPagado,costoPenalidad,motivoPenalidad,observacion,estado,estadoEstadia,archivada,vencida,version,closureId,cleaningCycleId}`. `estado` legacy derivado true para ACTIVA/CERRANDO; false para CERRADA. `totalPagado` de response es el total histórico del alojamiento (adelanto + cobro final), no mezcla consumo; detalle del cierre en campo adicional `cierre` con el desglose de Cotizacion y `cobroAhora`. `medioPagoAdelanto` nulo = `NO_REGISTRADO` (histórico migrado). C recibe el propio snapshot; no otro huésped.

`vencida` es campo derivado booleano (no columna): `true` cuando la estadía está ACTIVA y `fechaSalida` < hoy en America/Lima; el panel de recepción lo muestra como “estadía vencida sin cierre”. La habitación sigue OCUPADA: no se libera ni se cierra nada automáticamente; el cierre lo hace A/E con el flujo normal.

Cotizacion: `{idRecepcion,alojamiento:70,adelanto:20,penalidad:0,saldoAlojamiento:50,consumosPagados:0,consumosPendientes:7,cobroAhora:57,moneda:"PEN",calculadoEn}`. Redondeo HALF_UP a 2 decimales por componente con BigDecimal (nunca double); la comparación contra `montoRecibido` es exacta: cualquier diferencia, aunque sea 0.01, devuelve 409 `AMOUNT_MISMATCH` con nueva cotización. El frontend no recalcula; muestra los importes del backend. En CERRANDO, `cierre` devuelve `{closureId,estado,lastErrorCode,calculo}`; estado VALIDANDO/CONFIRMANDO y calculo=null hasta congelarse. En CERRADA, estado=COMPLETADO y añade el desglose final con `medioPago`. Sin intento de cierre, cierre=null. Un rechazo devuelve 409 con cotización y closureId; historial técnico rechazado no sustituye el último cierre vigente en GET. El mismo Idempotency-Key reanuda o devuelve su resultado terminal.

Anular check-in corregido: `POST /api/recepcion/{id}/anular-checkin` (A, `If-Match`, motivo 1..300, `Idempotency-Key`) corrige una estadía ACTIVA creada por error. Admisible solo si la estadía está ACTIVA, sin ventas admitidas ni cierre en curso. Hace CAS `ACTIVA → CERRADA` con `archivada=true`, libera `room_gate` a LIBRE con `roomVersion++` (sin limpieza ni notificación de checkout), publica `RecepcionActualizada` y audita en `audit_log`. No genera receipt ni cobro. Con operaciones ABIERTAS → 409 `OPERATIONS_IN_PROGRESS`; con cierre vigente → 409 `CLOSURE_IN_PROGRESS`.

## Feign entrante

Acceso solo máquina y scope del [común](../../03-contrato-comun.md). POST internos llevan Idempotency-Key estable derivada de operationId/blockId.

| Método/ruta | Caller | Request | Response |
|---|---|---|---|
| GET /internal/recepciones/{id} | sales, recepciones:read | — | 200 {idRecepcion,idHotel,idCliente,idHabitacion,estadoEstadia,version} |
| POST /internal/recepciones/{id}/operaciones | sales, operaciones:write | {operationId,idVenta,idHotel,tipo,actorId,actorRol} | 200 {operationId,tipo,estado:"ABIERTA"}; 409 no ACTIVA/finalizada, rechazo durable |
| POST /internal/habitaciones/{id}/bloqueos | hotel, bloqueos:write | {blockId,idHotel,motivo} | 200 {blockId,idHabitacion,estado:"BLOQUEADA"}; 409 no libre, decisión por blockId inmutable |

Caller sales valida JWT humano/sede y pasa actor auditado; si actorRol=CLIENTE reception comprueba idCliente=actorId y tipo=VENTA. No confundir token máquina con acceso humano sin permisos.

Feign saliente: identity GET cliente; hotel GET habitación; sales GET resumen cierre y POST confirmar cierre. Audiencias y scopes restringidos. Recuperación de cierre usa el mismo POST confirmar, que devuelve receipt persistido. No existe PUT externo para forzar disponibilidad saltando esos flujos.

## Kafka

Publica reception.recepcion.v1 con key=idHabitacion y aggregateVersion=roomVersion; eventos confirmados tras cambio de estadía. Snapshot payload:

`{idRecepcion,idHotel,idHabitacion,idCliente,numero,nombreCliente,fechaEntrada,fechaSalida,fechaSalidaConfirmacion,estadoEstadia,tarifa,precioInicial,adelanto,medioPagoAdelanto,costoPenalidad,motivoPenalidad,totalPagado,observacion:null,archivada,version,roomVersion,closureId,cleaningCycleId,cierre,ocupacionActual:{idRecepcion,estadoGate,cleaningCycleId,blockId}}`.

ocupacionActual describe el gate actual, no necesariamente la estadía histórica del evento. Archivar una estadía vieja mientras hay otra activa no publica habitación libre. Cada evento incrementa roomVersion bajo lock; version del payload corresponde a la estadía. No documento/correo/observaciones personales en Kafka. En SalidaRegistrada, cierre contiene `{alojamientoTotal,consumosPagadosAntes,consumosPagadosAlCerrar,consumosTotal,cobroAhora,totalGeneral,medioPago,closedAt}`. Pagos de alojamiento y ventas no se suman dos veces en reporting.

Consume hotel.habitacion.v1: inicializa gates; HabitacionLista libera solo ciclo; HabitacionHabilitada libera solo block. Nunca acepta física LISTA de un snapshot genérico como permiso de limpiar/cancelar un gate.

## Rabbit

Produce limpieza.solicitada, notificacion.checkin y notificacion.checkout.
Consume recepcion.operacion-finalizar, con inbox+lápida completa `{operationId,idRecepcion,idVenta,tipo,resultado}` e idHotel del envelope. Coincidencia estricta de tupla y combinaciones tipo/resultado; no revive FINALIZADA. No consulta stock desde listener. Ver payloads en [mensajería](../../05-mensajeria.md).

## Errores/pruebas

ROOM_NOT_READY, ROOM_OCCUPIED, ROOM_NOT_SYNCED, OPERATIONS_IN_PROGRESS, AMOUNT_MISMATCH, STAY_NOT_ACTIVE, ADVANCE_EXCEEDS_NEW_TOTAL, CLOSURE_IN_PROGRESS → 409 (ROOM_NOT_SYNCED puede 503 transitorio). DEPENDENCY_UNAVAILABLE → 503 antes de crear una operación durable. Si ya se aceptó el cierre y la estadía está CERRANDO, un fallo recuperable devuelve 202 con fase/Location; no indica al usuario que vuelva a iniciar el cobro. Catálogo completo con código, HTTP, campo y mensaje de UI: [`errors.md`](../../../contracts/errors.md).
Cliente intentando check-in 403; fecha futura de entrada 400; cierre con venta en vuelo 409; timeout después de sales receipt 202 y posterior 200 con misma clave, una sola salida.
