# Proposal

## Why

El cierre de estadía y el registro público emiten comandos de correo que nadie consume todavía: sin `notification-service` los comandos `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` quedarían sin efecto observable y sin reintentos. El envío de correo es el primer efecto secundario del proyecto con leases concurrentes, reintentos SMTP y purga de datos personales; un error aquí duplica correos, pierde trabajos o expone destinatarios en logs. Por eso este plan se ejecuta en modo `openspec` ([norma §3](../../../docs/plans/README.md)), y su propuesta acompaña al plan [`docs/plans/back-notification-worker.md`](../../../docs/plans/back-notification-worker.md).

## What Changes

- Alta de `services/notification-service/` con su base `notification_db`: tablas `notification_job` y `template` del [DDL de referencia](../../../docs/especificacion/microservicios/notification/base-de-datos.md#ddl-de-referencia), más las tablas técnicas comunes (`inbox`, `audit_log`), sin rutas de negocio y sin exponer nada por el Gateway.
- Consumo RabbitMQ de las tres colas `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` (exchange `hotel.commands.v1`, reintentos con TTL y DLQ por cola, exchange `hotel.dlx.v1`), con inserción idempotente de job por `commandId` en la misma transacción que el inbox.
- Despacho SMTP hacia Mailpit con lease de 60 s (`fn_notificacion_tomar` con `SKIP LOCKED`), `Message-ID` estable, timeout de 10 s fuera de la transacción y resultado por CAS (`sp_notificacion_resultado`) con reintentos a 1 min, 5 min y 30 min (4 intentos en total, luego ERROR).
- Plantillas v1 BIENVENIDA, CHECKIN y CHECKOUT sembradas con el texto de demostración y variables escapadas; jobs e inbox vacíos en el seed y SMTP deshabilitado por defecto en producción.
- CLI local de operador con perfil `admin` (`list --estado ERROR`, `retry`, `discard`, `purge --before`), auditada en `audit_log` y sin red ni Gateway.
- Purga por retención: destinatario y variables a NULL en ENVIADA de más de 30 días, conservación de estado y marcas 90 días, sin tocar PENDIENTE ni ERROR.
- Readiness con PostgreSQL y RabbitMQ, con SMTP degradado visible sin bloquear el negocio, y logs sin destinatario completo ni contenido privado.

Sin cambios de ruptura: el monorepo no tiene código todavía y notification no declara rutas en ningún `contracts/openapi/*.yaml`.

## Capabilities

### New Capabilities

- `notification/worker`: worker RabbitMQ de notificación — consumo idempotente de las tres colas `notificacion.*`, trabajos con lease y reintentos SMTP hacia Mailpit, CLI de operador auditada, purga por retención y readiness con RabbitMQ.

### Modified Capabilities

Ninguna: `openspec/specs/` está vacío y no hay requisitos previos que cambien.

## Impact

- **Código**: `services/notification-service/**` (nuevo); sin cambios en `contracts/openapi/*.yaml` porque notification no expone rutas de negocio.
- **Datos**: `notification_db` nueva (esquema V1, procedimientos `sp_notificacion_encolar`, `fn_notificacion_tomar` y `sp_notificacion_resultado`, seeds de las tres plantillas v1); sin destinatarios reales ni envíos durante el seed.
- **Dependencias**: RabbitMQ (tres colas principales más sus reintentos y DLQ) y SMTP Mailpit en dev (`localhost:1025`); sin Feign, sin Kafka y sin llamadas a identity en operación normal.
- **Casos**: NOT01–NOT03 de [`06-migracion-y-pruebas.md`](../../../docs/especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional), con su evidencia en el PR de ejecución.
- **Otros planes**: `back-identity-auth` produce `notificacion.bienvenida`, `back-reception-operacion` produce `notificacion.checkin` y `back-reception-cierre` produce `notificacion.checkout`; `back-plataforma-mensajeria` aporta la convención de exchanges, reintentos y DLQ que este plan consume; `qa-runbooks` opera sus DLQ y `back-reporting-proyecciones` no depende de este worker.
