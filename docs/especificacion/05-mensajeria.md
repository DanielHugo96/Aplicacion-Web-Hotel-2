# Contratos Kafka y RabbitMQ

## Envelope de evento Kafka

```json
{"eventId":"11111111-1111-4111-8111-111111111111","type":"CheckInRegistrado","schemaVersion":1,"aggregateType":"habitacion-ocupacion","aggregateId":"101","aggregateVersion":1,"idHotel":1,"occurredAt":"2026-10-04T15:00:00Z","correlationId":"22222222-2222-4222-8222-222222222222","payload":{"idRecepcion":1001,"idHabitacion":101,"idCliente":100,"estadoEstadia":"ACTIVA"}}
```

El ejemplo ilustra envelope; payload completo obligatorio según cada API/tabla siguiente. Kafka key=aggregateId salvo clave compuesta indicada. Headers `content-type=application/json`, `schema-version=1`, `correlation-id`. Fecha UTC, eventId UUID. idHotel es null para PersonaSnapshot global y un entero positivo para hechos de una sede. Nunca JWT, clave ni hash.

Payload snapshot incluye todos los campos de proyección indicados en la API productora. Evento de baja mantiene el snapshot completo, con id,estado=false,version y el resto de campos del contrato; las bajas de venta son snapshots de anulación, no DELETE físico.

## Topics y asignación exacta

| Topic | Productor | Tipos y key | Consumidores |
|---|---|---|---|
| identity.persona.v1 | identity | PersonaSnapshot; idPersona | reporting |
| hotel.sede.v1 | hotel | SedeSnapshot; idHotel | reporting |
| hotel.habitacion.v1 | hotel | HabitacionCreada, HabitacionSnapshot, HabitacionLista, HabitacionHabilitada; idHabitacion | reporting; reception (alta de gate, ciclos y bloqueos) |
| reception.recepcion.v1 | reception | CheckInRegistrado, RecepcionActualizada, SalidaRegistrada; idHabitacion, version=roomVersion | reporting; hotel (proyección); sales (limpia carrito al cerrar) |
| inventory.stock.v1 | inventory | ProductoStockSnapshot; idHotel:idProducto | reporting |
| inventory.movimiento.v1 | inventory | MovimientoStockResuelto; idVenta | sales (compensación) |
| sales.venta.v1 | sales | VentaSnapshot; idVenta | reporting |

Cada consumidor tiene grupo propio `<servicio>-<proyeccion>-v1`; réplicas del mismo servicio comparten grupo. Demo1 partición/topic, replication-factor1 (no alta disponibilidad). Producción requeriría dimensionamiento distinto. Retención demo7d; la reconstrucción no depende de que exista todo el historial: snapshot de bootstrap + stream.

Productor con acks=all e idempotencia habilitada; esto no elimina necesidad de outbox/inbox. Consumer confirma offset después del commit local. Hasta5 reintentos con espera1,5,30,120,300s después del intento inicial; luego `<topic>.<consumerGroup>.dlt` conserva envelope original, key, topic/partición/offset, consumerGroup y error no sensible. Alertar; reenvío manual conserva eventId y key, y se procesa con el consumerGroup que falló; no confundir fallos de Reporting con fallos de Reception. No habilitar admisión por un evento perdido. Reporting marca proyección degradada ante DLT; el operador no declara paridad hasta reparar.

## Envelope de comando RabbitMQ

Exchange durable tipo direct `hotel.commands.v1`. Cada routing key tiene una cola durable con el mismo nombre; mensajes persistentes, mandatory y publisher confirms; no-routable mantiene outbox pendiente.

```json
{"commandId":"33333333-3333-4333-8333-333333333333","type":"LimpiezaSolicitada","schemaVersion":1,"idHotel":1,"correlationId":"22222222-2222-4222-8222-222222222222","issuedAt":"2026-10-05T16:00:00Z","payload":{"idHabitacion":101,"idRecepcion":1001,"cleaningCycleId":"44444444-4444-4444-8444-444444444444","roomVersion":2}}
```

Props AMQP: messageId=commandId, contentType=application/json, deliveryMode=2, correlationId; header x-schema-version=1. No secretos en headers. Ack después de efecto/inbox.

| Routing key = cola | Productor → consumidor | Payload obligatorio |
|---|---|---|
| limpieza.solicitada | reception → hotel | idHabitacion:int,idRecepcion:int,cleaningCycleId:uuid,roomVersion:long |
| stock.restituir | sales → inventory | idVenta:int,idRecepcion:int,motivo:string; idHotel del envelope |
| recepcion.operacion-finalizar | sales → reception | operationId:uuid,idRecepcion:int,idVenta:int,tipo:VENTA/PAGO/ANULACION,resultado:CONFIRMADA/RECHAZADA/CANCELADA/PAGO_REGISTRADO |
| notificacion.bienvenida | identity → notification | idPersona:int,destinatario:email,nombre:string,templateVersion:1 |
| notificacion.checkin | reception → notification | idPersona:int,idRecepcion:int,destinatario:email,nombre:string,hotel:string,fechaSalida:date,templateVersion:1 |
| notificacion.checkout | reception → notification | idPersona:int,idRecepcion:int,destinatario:email,nombre:string,hotel:string,totalAlojamiento:decimal,totalConsumos:decimal,templateVersion:1 |

Finalizar incluye tipo para poder crear una lápida completa aun antes de admitir. Combinaciones: VENTA→CONFIRMADA/RECHAZADA/CANCELADA; PAGO→PAGO_REGISTRADO/RECHAZADA; ANULACION→CANCELADA/RECHAZADA. Verificar tupla de IDs e idHotel antes de liberar la operación; una contradicción va a DLQ sin desbloquear otra venta.

Bienvenida tiene idHotel=null porque el cliente es global. Restituir no acepta cantidades/precios del emisor: inventory usa su movimiento original, o crea lápida si no existe.

No se añade alerta de stock-bajo por correo en MVP; se ve en reportes. No existe API pública que publique estos comandos.

## Reintentos y DLQ Rabbit

Por cada cola `q`: `q.retry.5s`, `q.retry.30s`, `q.retry.120s` con TTL y dead-letter hacia la cola principal; agotados3 reintentos → `q.dlq` mediante exchange `hotel.dlx.v1`. Cola principal sin TTL que descarte silenciosamente trabajos. Reintento conserva commandId; original ack solo cuando re-publicación queda confirmada. Errores de schema permanentes van directo a DLQ. Controlar x-death/intentos, no hacer nack-requeue infinito.

Stock/limpieza no se consideran completados por llegar a DLQ. Sale queda CANCELACION_PENDIENTE, habitación sigue bloqueada; alerta y acción del operador. Broker permissions por producer/consumer y vhost del proyecto.

## Datos sensibles, correo y orden

Destinatario va solo por cola privada de notificación, no por Kafka de reportes. Retención limitada y acceso restringido. Correo es efecto secundario; caída de Mailpit/SMTP no revierte check-in o cobro.

SMTP no garantiza exactamente una entrega si el worker cae entre enviar y registrar resultado. Se deduplica por commandId y Message-ID estable; en esa ventana puede haber correo duplicado, nunca doble venta/cobro. La UI no usa correo como confirmación autoritativa.

## Pruebas mínimas

Duplicar cada mensaje; invertir limpieza y salida; restituir antes de descontar; perder confirmación del broker; parar consumers y reiniciar; usar eventId repetido con agregado posterior; enviar ciclo viejo después del nuevo. Invariantes del documento de flujos deben mantenerse.
