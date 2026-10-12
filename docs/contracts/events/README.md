# Eventos y comandos — catálogo de JSON Schemas v1

Contratos de mensajería del MVP: **JSON Schema draft 2020-12** para los **7 topics
Kafka** (eventos de dominio) y los **6 comandos RabbitMQ** definidos en
[`05-mensajeria.md`](../../especificacion/05-mensajeria.md). Cada archivo de
mensaje describe el **mensaje completo envuelto** (`allOf` sobre el envelope
correspondiente) e incluye un ejemplo válido embebido en `examples`.

Artefactos:

- [`envelope.v1.schema.json`](envelope.v1.schema.json) — envelope común de eventos Kafka.
- [`command-envelope.v1.schema.json`](command-envelope.v1.schema.json) — envelope común de comandos RabbitMQ.
- Un `<topic o routing key>.<Tipo>.v1.schema.json` por cada tipo de evento/comando.

## 1. Eventos Kafka

| Tipo | Archivo | Topic (Kafka key) | Productor | Consumidores |
|---|---|---|---|---|
| `PersonaSnapshot` | `identity.persona.v1.PersonaSnapshot.schema.json` | `identity.persona.v1` (`idPersona`) | identity | reporting (`reporting-proyeccion-v1`) |
| `SedeSnapshot` | `hotel.sede.v1.SedeSnapshot.schema.json` | `hotel.sede.v1` (`idHotel`) | hotel | reporting (`reporting-proyeccion-v1`) |
| `HabitacionCreada` | `hotel.habitacion.v1.HabitacionCreada.schema.json` | `hotel.habitacion.v1` (`idHabitacion`) | hotel | reporting; reception (`reception-gates-v1`, gate inicial) |
| `HabitacionSnapshot` | `hotel.habitacion.v1.HabitacionSnapshot.schema.json` | `hotel.habitacion.v1` (`idHabitacion`) | hotel | reporting; reception (`reception-gates-v1`, solo snapshot) |
| `HabitacionLista` | `hotel.habitacion.v1.HabitacionLista.schema.json` | `hotel.habitacion.v1` (`idHabitacion`) | hotel | reporting; reception (`reception-gates-v1`, libera solo el ciclo) |
| `HabitacionHabilitada` | `hotel.habitacion.v1.HabitacionHabilitada.schema.json` | `hotel.habitacion.v1` (`idHabitacion`) | hotel | reporting; reception (`reception-gates-v1`, libera solo el bloqueo) |
| `CheckInRegistrado` | `reception.recepcion.v1.CheckInRegistrado.schema.json` | `reception.recepcion.v1` (`idHabitacion`, versión=`roomVersion`) | reception | reporting; hotel (`hotel-ocupacion-v1`); sales (`sales-compensacion-v1`) |
| `RecepcionActualizada` | `reception.recepcion.v1.RecepcionActualizada.schema.json` | `reception.recepcion.v1` (`idHabitacion`, versión=`roomVersion`) | reception | reporting; hotel (`hotel-ocupacion-v1`); sales (`sales-compensacion-v1`) |
| `SalidaRegistrada` | `reception.recepcion.v1.SalidaRegistrada.schema.json` | `reception.recepcion.v1` (`idHabitacion`, versión=`roomVersion`) | reception | reporting; hotel (`hotel-ocupacion-v1`); sales (`sales-compensacion-v1`, limpia carrito) |
| `ProductoStockSnapshot` | `inventory.stock.v1.ProductoStockSnapshot.schema.json` | `inventory.stock.v1` (`idHotel:idProducto`) | inventory | reporting (`reporting-proyeccion-v1`) |
| `MovimientoStockResuelto` | `inventory.movimiento.v1.MovimientoStockResuelto.schema.json` | `inventory.movimiento.v1` (`idVenta`) | inventory | sales (`sales-compensacion-v1`, compensación) |
| `VentaSnapshot` | `sales.venta.v1.VentaSnapshot.schema.json` | `sales.venta.v1` (`idVenta`) | sales | reporting (`reporting-proyeccion-v1`) |

## 2. Comandos RabbitMQ

Exchange durable direct `hotel.commands.v1`; routing key = nombre de cola.

| Tipo | Archivo | Routing key = cola | Productor → consumidor |
|---|---|---|---|
| `LimpiezaSolicitada` | `limpieza.solicitada.LimpiezaSolicitada.v1.schema.json` | `limpieza.solicitada` | reception → hotel |
| `StockRestituir` | `stock.restituir.StockRestituir.v1.schema.json` | `stock.restituir` | sales → inventory |
| `RecepcionOperacionFinalizar` | `recepcion.operacion-finalizar.RecepcionOperacionFinalizar.v1.schema.json` | `recepcion.operacion-finalizar` | sales → reception |
| `NotificacionBienvenida` | `notificacion.bienvenida.NotificacionBienvenida.v1.schema.json` | `notificacion.bienvenida` | identity → notification |
| `NotificacionCheckin` | `notificacion.checkin.NotificacionCheckin.v1.schema.json` | `notificacion.checkin` | reception → notification |
| `NotificacionCheckout` | `notificacion.checkout.NotificacionCheckout.v1.schema.json` | `notificacion.checkout` | reception → notification |

## 3. Convenciones

### 3.1 Envelope de evento Kafka (`envelope.v1.schema.json`)

`{eventId, type, schemaVersion, aggregateType, aggregateId, aggregateVersion, idHotel, occurredAt, correlationId, payload}`,
`additionalProperties:false`:

- `eventId`: UUID del evento; se conserva al reenviar desde DLT.
- `type`: tipo en PascalCase; cada esquema lo fija con `const`.
- `schemaVersion`: `const 1`.
- `aggregateType` / `aggregateId`: agregado y su identificador como texto (= Kafka key).
- `aggregateVersion`: entero ≥ 0; en `reception.recepcion.v1` es el `roomVersion`.
- `idHotel`: entero ≥ 1, o `null` solo en hechos globales (`PersonaSnapshot`).
- `occurredAt`: `date-time` UTC (`Z`).
- `correlationId`: UUID de correlación; viaja también en el header `correlation-id`.
- `payload`: objeto; cada tipo lo restringe con `additionalProperties:false`, tipos y enums.

Headers Kafka: `content-type=application/json`, `schema-version=1`, `correlation-id`.
Productor `acks=all` con idempotencia (no elimina outbox/inbox); el consumidor confirma
offset después del commit local.

### 3.2 Envelope de comando RabbitMQ (`command-envelope.v1.schema.json`)

`{commandId, type, schemaVersion, idHotel, correlationId, issuedAt, payload}`,
`additionalProperties:false`; `schemaVersion` `const 1`; `idHotel` entero ≥ 1 o `null`
(solo en `notificacion.bienvenida`, porque el cliente es global).

Props AMQP: `messageId=commandId`, `contentType=application/json`, `deliveryMode=2`,
`correlationId` y header `x-schema-version=1`. Mensajes persistentes, `mandatory` y
publisher confirms; ack después del efecto/inbox.

### 3.3 Composición y validación

Cada esquema de mensaje compone:

```json
{
  "allOf": [
    {"$ref": "./envelope.v1.schema.json"},
    {"properties": {"type": {"const": "<Tipo>"}, "payload": {"additionalProperties": false}}}
  ]
}
```

Por eso un mensaje se valida **entero** (envelope + payload) contra un único archivo.

### 3.4 Tipos, importes y fechas

- Campos `camelCase`; `null` = sin dato / `NO_REGISTRADO` (p. ej. `medioPagoAdelanto`, `medioPago`, `paidAt`, `closureId`).
- Importes `number` con `multipleOf: 0.01` (BigDecimal HALF_UP en los servicios, nunca `double`).
- Fechas `format: date` (America/Lima); sellos `format: date-time` en UTC.
- Enums relevantes: `estadoEstadia` ACTIVA/CERRANDO/CERRADA; `estadoGate` LIBRE/OCUPADA/LIMPIEZA_PENDIENTE/BLOQUEADA; `estadoFisico` LISTA/EN_LIMPIEZA/MANTENIMIENTO; `medioPago*` EFECTIVO/TARJETA/OTRO; `estadoOperacion` PENDIENTE/CONFIRMADA/RECHAZADA/CANCELACION_PENDIENTE/CANCELADA; `MovimientoStockResuelto.estado` DESCONTADO/RECHAZADO/RESTITUIDO; `tipo` VENTA/PAGO/ANULACION y `resultado` CONFIRMADA/RECHAZADA/CANCELADA/PAGO_REGISTRADO.

Nunca viajan por Kafka documento, correo, clave ni hash. El `destinatario` (correo)
solo aparece en los comandos de notificación, que usan una cola privada.

### 3.5 Versionado

- `schemaVersion` y el sufijo `.v1` identifican esta generación; un cambio incompatible
  publica un archivo `<...>.v2.schema.json` nuevo y el `type`/`schemaVersion` permiten
  distinguirlos, sin editar el contrato v1 en caliente.
- Añadir un campo opcional no rompe v1; volver obligatorio un campo o cambiar semántica
  de uno existente sí.

### 3.6 Grupos de consumidor, DLT y DLQ

Kafka (una fila por topic+grupo; réplicas del mismo servicio comparten grupo):

| consumerGroup | Consumidor | Topics | DLT |
|---|---|---|---|
| `reporting-proyeccion-v1` | reporting | identity.persona.v1, hotel.sede.v1, hotel.habitacion.v1, reception.recepcion.v1, inventory.stock.v1, sales.venta.v1 | `<topic>.reporting-proyeccion-v1.dlt` |
| `hotel-ocupacion-v1` | hotel | reception.recepcion.v1 | `reception.recepcion.v1.hotel-ocupacion-v1.dlt` |
| `reception-gates-v1` | reception | hotel.habitacion.v1 | `hotel.habitacion.v1.reception-gates-v1.dlt` |
| `sales-compensacion-v1` | sales | inventory.movimiento.v1, reception.recepcion.v1 | `inventory.movimiento.v1.sales-compensacion-v1.dlt`, `reception.recepcion.v1.sales-compensacion-v1.dlt` |

Kafka reintenta hasta 5 veces con espera 1s, 5s, 30s, 120s y 300s tras el intento
inicial; agotados, `<topic>.<consumerGroup>.dlt` conserva envelope, key, topic/partición/offset,
consumerGroup y error no sensible. El reenvío manual conserva `eventId` y key.

Rabbit, por cada cola `q`: `q.retry.5s`, `q.retry.30s` y `q.retry.120s` con TTL y
dead-letter hacia la cola principal; agotados 3 reintentos → `q.dlq` vía exchange
`hotel.dlx.v1`. Errores de schema permanentes van directo a DLQ. Reintento conserva
`commandId`; el original se ack solo cuando la republicación queda confirmada.

### 3.7 Retención demo

Demo: 1 partición por topic, replication-factor 1 (sin alta disponibilidad) y retención
7 días; la reconstrucción no depende de todo el historial (snapshot de bootstrap +
stream). En producción el dimensionamiento sería distinto.

### 3.8 Cómo validar un mensaje

El validador debe resolver `$ref` relativos; con `jsonschema` ≥ 4.18 y `referencing`:

```python
import json
from pathlib import Path
from jsonschema import Draft202012Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

carpeta = Path("docs/contracts/events")
esquemas = [json.loads(p.read_text(encoding="utf-8")) for p in carpeta.glob("*.schema.json")]
registry = Registry().with_resources(
    (e["$id"], Resource.from_contents(e, default_specification=DRAFT202012))
    for e in esquemas
)

esquema = next(e for e in esquemas if e["$id"].endswith("reception.recepcion.v1.SalidaRegistrada.schema.json"))
mensaje = json.loads(Path("mensaje.json").read_text(encoding="utf-8"))
Draft202012Validator(esquema, registry=registry).validate(mensaje)
print("OK")
```

Alternativa rápida por CLI (resuelve los `$ref` relativos desde el archivo del esquema):

```bash
python -m jsonschema -i mensaje.json reception.recepcion.v1.SalidaRegistrada.schema.json
```

Para probar el contrato con los ejemplos embebidos basta validar `examples[0]` de cada
archivo, como hace la validación de la carpeta.
