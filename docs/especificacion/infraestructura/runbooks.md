# Runbooks de operación, backups y alertas

Pasos del operador para los casos atascados de la demo. El estado técnico persistido es la fuente de verdad; no se "arregla" con SQL directo salvo el procedimiento indicado y siempre con auditoría. Las consultas son de la base del servicio dueño.

## RB1 — Mensajes en DLQ (Kafka y Rabbit)

**Síntomas:** alerta de mensajes en `<topic>.<consumerGroup>.dlt` o en `q.dlq`; `degraded=true` en reportes; limpieza/stock no completados; venta en `CANCELACION_PENDIENTE`.

**Diagnóstico:**

1. Kafka: identificar topic y sufijo `.dlt`; el mensaje conserva envelope original (eventId, type, key, topic/partición/offset), consumerGroup y error no sensible. Buscar el efecto esperado en la tabla destino del grupo que falló (reporting, hotel, reception, sales).
2. Rabbit: identificar la cola `q.dlq` y el `commandId`; comparar con el estado del destino (`notification_job`, `movimiento_venta`, `operacion_consumo`).
3. Confirmar si el efecto ya se aplicó: inbox, versión del agregado o lápida (`FINALIZADA`, `RESTITUIDO`, `ENVIADA`).

**Pasos:**

1. Si el efecto no se aplicó y el payload es válido: reenviar conservando `eventId`/`commandId` original y el consumerGroup que falló; el inbox/lápida evita duplicar.
2. Si el efecto ya se aplicó: descartar el mensaje de la DLQ solo con esa evidencia y registrarlo (auditoría del operador).
3. Si el payload es inválido permanente: corregir el productor y generar un mensaje nuevo con nuevo id, referenciando el original en auditoría; no editar el envelope a mano ni saltar el inbox.
4. Notification: revisar jobs `ERROR` con la CLI local (`java -jar notification-service.jar --admin`): `list --estado ERROR`, `retry <commandId>` (preserva commandId y pasa por inbox), `discard`, `purge --before <fecha>`; cada acción queda en `audit_log`.
5. Nunca borrar la DLQ sin dejar evidencia; no confundir fallos de Reporting con fallos de Reception.

**Escalar:** si el mensaje no puede reprocesarse sin cambiar su payload o su id.

## RB2 — Cierre atascado en CONFIRMANDO

**Síntomas:** estadía `CERRANDO` con cierre `CONFIRMANDO` > 5min; `lastErrorCode` presente; el pago pudo haberse aplicado en Sales.

**Diagnóstico:**

1. `GET /api/recepcion/{id}`: fase del cierre, `calculo` congelado, `lastErrorCode`, `closureId`.
2. `sales_db.cierre_consumos` por `closure_id`/`id_recepcion`: ¿existe `receipt`?
3. `recepcion_db.cierre`: `receipt_sales` presente o null.

**Pasos:**

1. **No volver a cobrar** ni pedir efectivo de nuevo al huésped.
2. Si Sales ya tiene receipt: reintentar la confirmación con el payload congelado (mismo `{closureId,idRecepcion,idHotel,expectedPendingTotal}`); reception lo valida y completa. No recalcular montos.
3. Si Sales no tiene receipt: repetir el POST de confirmación con el mismo payload congelado; si el fallo es transitorio, la recuperación de 30s lo reintenta sola.
4. Fallo permanente: conservar `CERRANDO` + `lastErrorCode`, no borrar el cierre, no devolver la estadía a ACTIVA; preparar corrección manual auditada.

**Criterio de decisión:** receipt existente → completar; 409 de negocio → revisar `calculo` congelado contra el receipt antes de cualquier corrección. **Escalar:** toda divergencia de importes.

## RB3 — Operación de habitación en PREPARANDO

**Síntomas:** `operacion_habitacion` `PREPARANDO` > 10min; mantenimiento/baja no aplicado; nuevas mutaciones de esa habitación devuelven 409 `ROOM_OPERATION_IN_PROGRESS`.

**Diagnóstico:**

1. `GET /api/habitacion/operaciones/{blockId}`: tipo y estado (`PREPARANDO`/`APLICADA`/`LIBERADA`/`RECHAZADA`).
2. `reception_db.bloqueo_habitacion` por `block_id`, `request_hash`: ¿`ACTIVO`, `LIBERADO` o `RECHAZADO`? ¿Con qué `error_code`?
3. `next_attempt_at`/`attempts` de la operación hotel.

**Pasos:**

1. Bloqueo `ACTIVO` y hotel sin aplicar: el job reintenta la aplicación local (CAS `PREPARANDO → APLICADA`); verificar el próximo intento.
2. Bloqueo `RECHAZADO` (gate `OCUPADA`/`LIMPIEZA_PENDIENTE`): marcar la operación hotel como `RECHAZADA`; el rechazo es durable por `blockId` y no se admite aunque la habitación quede libre después.
3. Bloqueo inexistente en reception (fallo antes del commit remoto): reintentar el mismo POST idempotente con el mismo `blockId`.
4. Nunca liberar por timeout ni editar `room_gate` a mano.

**Escalar:** si el bloqueo y la operación no coinciden en tipo/habitación/sede.

## RB4 — Outbox atrasado

**Síntomas:** alerta de pendientes > 15min o `attempts` > 10; eventos que no llegan a proyecciones/gates; `ROOM_NOT_SYNCED` intermitente; bootstrap de reporting incompleto.

**Diagnóstico:**

```sql
SELECT destination, channel, count(*), min(created_at) AS mas_antiguo
FROM outbox WHERE published_at IS NULL GROUP BY destination, channel;
SELECT id, type, attempts, next_attempt_at, aggregate_id FROM outbox
WHERE published_at IS NULL ORDER BY next_attempt_at LIMIT 20;
```

**Pasos:**

1. Verificar el broker destino (Kafka/Rabbit) y su health; si está caído, resolverlo: el poller reintenta solo, sin perder filas.
2. Fila "venenosa" (payload inválido, `attempts` alto): inspeccionar `type`/`payload` contra el JSON Schema de `contracts/events/`; corregir el productor y reencolar. Descartar una fila exige decisión auditada, nunca un borrado silencioso.
3. Verificar el consumidor: inbox, DLT y `projection_checkpoint` para no duplicar el efecto al reencolar.
4. Recordar: una fila por destino; no marcar dos brokers con el mismo flag.

**Criterio:** ninguna fila se marca publicada sin confirmación del broker. **Escalar:** si el atraso proviene de un evento perdido y la reconstrucción requiere snapshot.

## Backups y restauración

- `pg_dump -Fc` diario por base; retención 7 días en dev/demo. Backup **obligatorio** antes de migraciones, importación del monolito o reconstrucción de proyecciones.
- Restauración en una **DB nueva** (nunca encima de una en uso):

```bash
pg_dump -Fc -h localhost -p 55432 -U postgres -d sales_db -f sales_db.dump
createdb -h localhost -p 55432 -U postgres sales_db_restore
pg_restore -h localhost -p 55432 -U postgres -d sales_db_restore sales_db.dump
```

- Verificación posterior: conteos por tabla, checksums de claves, últimos offsets/watermarks (`projection_checkpoint`), outbox pendientes y secuencias por encima del mayor ID.
- Reanudar outbox/checkpoints solo tras verificar; ejecutar un simulacro de restauración y guardarlo como evidencia.

## Alertas y umbrales

| Alerta | Umbral | Detección | Severidad | Runbook |
|---|---|---|---|---|
| Mensajes en DLT Kafka | > 0 | log estructurado del consumer / conteo del topic `.dlt` | Alta | RB1 |
| Mensajes en DLQ Rabbit | > 0 | management / conteo de `q.dlq` | Alta | RB1 |
| Outbox pendiente | > 15min o `attempts` > 10 | consulta a `outbox` | Alta | RB4 |
| Cierre atascado | `CONFIRMANDO` > 5min | consulta a `cierre` | Alta | RB2 |
| Operación de habitación | `PREPARANDO` > 10min | consulta a `operacion_habitacion` | Media | RB3 |
| Readiness caída | > 1min | `/actuator/health/readiness` | Media | Revisar dependencia; el 503 ya es visible |
| Jobs de notification | estado `ERROR` > 0 | consulta a `notification_job` | Media | RB1 (CLI) |
| Reporting degradado | `degraded=true` o sin conexión Kafka > 5min | `projection_checkpoint` / meta de la API | Media | RB1 |
| Ventas en recuperación | `CANCELACION_PENDIENTE` > 30min | consulta a `venta` | Media | RB1/RB4 |
