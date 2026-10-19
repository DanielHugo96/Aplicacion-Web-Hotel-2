# IA-back-notification-worker-02 — `sp_notificacion_encolar` recibe el cuerpo del comando en `p_payload`

- **Plan:** `back-notification-worker`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

La spec fija la firma `sp_notificacion_encolar(p_command uuid, p_tipo varchar, p_payload jsonb)` en
[base-de-datos.md](../../especificacion/microservicios/notification/base-de-datos.md) § Procedimientos.
Pero `notification_job` necesita `id_hotel`, y `idHotel` vive en el **envelope** del comando
([`05-mensajeria.md`](../../especificacion/05-mensajeria.md) § Envelope de comando RabbitMQ), no en su
`payload`, que es un esquema cerrado (`additionalProperties:false`). Con la firma fijada no hay
parámetro para la sede: o se cambia la firma o el parámetro `p_payload` lleva algo más que el
`payload` del comando.

## Qué se decidió

`p_payload` recibe el **cuerpo del comando completo** (envelope + payload), que es el «payload» en el
sentido AMQP (el cuerpo del mensaje). El procedimiento:

- toma `p_payload->'payload'` y lo usa para el destinatario, la persona, la recepción y las
  `variables` (que guarda **solo** los campos de la plantilla, según § Esquemas JSONB);
- toma `p_payload->>'idHotel'` para `id_hotel`;
- calcula `payload_hash` sobre el cuerpo completo y `message_id` como `p_command || '@hotel.test'`.

`p_tipo` sigue siendo la plantilla (BIENVENIDA / CHECKIN / CHECKOUT), derivada de la cola por el
listener, y el procedimiento la valida contra la allowlist de `template`.

## Por qué

La firma de la spec es lo que manda y no se toca; el nombre del parámetro no obliga a qué JSON viaja
dentro. El listener es el único llamador, la traducción queda en un solo sitio (el procedimiento) y
así el hash de deduplicación cubre el comando entero, que es lo que pide la spec («mismo ID con
distinto `payload_hash` es error permanente»).

## Alternativas descartadas

- **Añadir un cuarto parámetro `p_id_hotel`**: cambia una firma fijada por la spec.
- **Pasar solo el `payload` y deducir la sede de otra tabla**: notification no tiene FK ni copia de
  sedes (`base-de-datos.md`: «No FK a identity/reception») y la spec prohíbe consultar identity al
  enviar.
- **Inyectar `idHotel` dentro del `payload` en el listener**: convertiría el esquema cerrado del
  comando en otra cosa y rompería la validación contra `command-envelope.v1.schema.json`.

## A quién afecta

Solo a `notification-service`. Ningún contrato publicado cambia: los JSON Schema de
`docs/contracts/events/` y el envelope de `05-mensajeria.md` quedan igual y son los que valida el
listener antes de llamar al procedimiento.