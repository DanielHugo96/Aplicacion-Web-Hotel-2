# Design

## Context

Ver [`proposal.md`](proposal.md) para la motivación. El cambio se implementa en un monorepo sin código todavía: `services/notification-service/` nace en este plan, aplica la convención ya mergeada de `back-plataforma-comunes` y `back-plataforma-mensajeria` (envelope, inbox, health, logs) y no migra datos del monolito. La spec ya fija el contrato completo: sin rutas de negocio ni login propio, Gateway que no enruta a este servicio ([`notification/api.md` §REST](../../../docs/especificacion/microservicios/notification/api.md#rest)), tres colas con sus payloads exactos ([§RabbitMQ](../../../docs/especificacion/microservicios/notification/api.md#rabbitmq)), sin Feign ni Kafka ([§Feign y Kafka](../../../docs/especificacion/microservicios/notification/api.md#feign-y-kafka)), tablas y procedimientos con sus CHECK y leases ([`base-de-datos.md`](../../../docs/especificacion/microservicios/notification/base-de-datos.md)), y reintentos y DLQ por cola ([`05-mensajeria.md` §Reintentos y DLQ Rabbit](../../../docs/especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit)). Restricciones que gobiernan el diseño: SMTP ocurre fuera de la transacción; una `commandId` produce un único job; el correo es efecto secundario y su caída no revierte negocio; ENVIADA significa SMTP aceptó el mensaje.

## Goals / Non-Goals

**Goals:**

- Persistir cada comando una sola vez como job, con inbox en la misma transacción del efecto y ack solo tras el commit.
- Despachar correo con un solo poseedor por job (lease con token), con reintentos del job y sin doble reenvío Rabbit + job a la vez.
- Hacer observables los fallos (ERROR, DLQ) y operables con la CLI local auditada, sin exponer reenvíos por red.
- Retener lo mínimo: purgar destinatario y variables de ENVIADA antigua sin borrar PENDIENTE ni ERROR.

**Non-Goals:**

- Emisión de `notificacion.bienvenida`, `notificacion.checkin` o `notificacion.checkout` (planes `back-identity-auth`, `back-reception-operacion` y `back-reception-cierre`).
- Outbox, poller de 5 s, exchanges y DLT/DLQ genéricos (plan `back-plataforma-mensajeria`); aquí solo se consume lo asignado.
- Reportes, proyecciones y pantallas Angular (planes `back-reporting-proyecciones` y `front-*`).
- Proveedor SMTP de producción, adjuntos, HTML arbitrario o URLs a descargar.

## Decisions

1. **Persistencia tal como la spec.** Migración Flyway V1 derivada del [DDL de referencia](../../../docs/especificacion/microservicios/notification/base-de-datos.md#ddl-de-referencia) con `notification_job`, `template`, `inbox` y `audit_log` del DDL común; índice `ix_notification_job_estado` por estado y `next_attempt_at`; CHECK de lease obligatorio en ENVIANDO y de destinatario y variables obligatorios salvo ENVIADA tras purga. Los tres procedimientos se implementan con las firmas fijadas (`sp_notificacion_encolar`, `fn_notificacion_tomar`, `sp_notificacion_resultado`) porque concentran el CAS, el `SKIP LOCKED` y la comparación de `payload_hash` en la base, sin HTTP ni publicación a brokers. Alternativa descartada: lógica de lease solo en memoria; la spec exige lease persistido con token y recuperación por vencimiento.

2. **Recepción con un solo mecanismo de reintento a la vez.** El listener valida envelope y payload contra los JSON Schemas, ejecuta `sp_notificacion_encolar` con inbox en la misma transacción y solo entonces hace ack. Los errores antes de persistir usan los reintentos TTL y la DLQ de Rabbit; una vez persistido, se hace ack y los reintentos pertenecen al job (`next_attempt_at`), nunca a Rabbit. Un `commandId` duplicado no crea otro job; el mismo ID con distinto `payload_hash` es error permanente a DLQ; una plantilla desconocida va directo a DLQ sin reintentos. Alternativa descartada: nack con requeue infinito; la spec lo prohíbe y exige controlar intentos sin descartes silenciosos.

3. **Despacho con lease y CAS.** Un planificador toma vencidos con `fn_notificacion_tomar` (`FOR UPDATE SKIP LOCKED`, un job por worker, nuevo `lease_token` y 60 s). El envío SMTP ocurre fuera de la transacción con timeout de 10 s y `Message-ID` estable `<commandId@hotel.test>`; el resultado se registra con `sp_notificacion_resultado` solo si el lease sigue vigente y el token coincide. Un lease vencido vuelve a PENDIENTE mediante CAS que elimina su token; la respuesta tardía del poseedor anterior se ignora. Los reintentos del job son a 1 min, 5 min y 30 min (4 intentos contando el inicial); agotados pasa a ERROR. Alternativa descartada: reencolar por Rabbit tras el fallo SMTP; duplicaría el mecanismo y la spec ordena pertenencia al job tras persistir.

4. **Plantillas como allowlist en BD.** La tabla `template` con PK por tipo y versión guarda asunto y cuerpo en texto plano con el aviso de demostración; el worker solo acepta los tres tipos y la versión 1, valida el destinatario como correo de hasta 254 caracteres y rechaza claves desconocidas o valores no escalares en variables. Los importes viajan como `numeric(12,2)` y las variables se escapan al componer el mensaje; sin HTML del emisor, sin adjuntos y sin descargas. Seeds solo en perfiles no productivos, con jobs e inbox vacíos. Alternativa descartada: plantillas en archivos o en código; la spec fija tabla y seeds versionados.

5. **CLI local sin red.** La herramienta corre en el mismo proceso con perfil `admin` contra `notification_db` con credencial de entorno, sin puerto ni ruta por Gateway. Cada acción escribe en `audit_log` con actor, `commandId` y resultado (reintento, descarte y purga con sus acciones auditadas); `retry` reencola preservando `commandId` pasando por el inbox sin duplicar el job; `discard` pasa a estado terminal con motivo sin borrar el histórico; `purge --before` solo toca ENVIADA y deja NULL destinatario y variables. Si el payload es inválido se crea un `commandId` nuevo con referencia al original en auditoría; nunca se edita el payload ni se salta el inbox. Alternativa descartada: endpoint REST de reenvío; la spec lo prohíbe explícitamente.

6. **Salud y observabilidad.** `GET /actuator/health/liveness` responde sin consultar dependencias; `GET /actuator/health/readiness` exige PostgreSQL y RabbitMQ y muestra SMTP como degradación sin bloquear el negocio. No se habilita prometheus en el MVP; la operación usa logs estructurados con `commandId` y correlación, sin destinatario completo ni contenido privado, y las alertas de plataforma para pendientes y reintentos. Alternativa descartada: readiness solo con DB; la spec exige Rabbit disponible y SMTP degradado visible.

7. **Pruebas con dependencias reales.** PostgreSQL, RabbitMQ y Mailpit reales (Compose o Testcontainers), sin sustituir leases ni inbox por mocks; los casos NOT01–NOT03 se prueban publicando los comandos reales y observando `notification_job`, el buzón de Mailpit y `audit_log`.

## Risks / Trade-offs

- [Caída entre SMTP y persistencia de ENVIADA] → el correo puede duplicarse; se mitiga con `Message-ID` estable y deduplicación por `commandId`, pero la spec lo declara limitación explícita, nunca doble venta o cobro.
- [Lease vencido durante un envío lento] → otro worker toma el job; el resultado tardío del anterior se ignora por token, a costa de un posible duplicado SMTP.
- [Comando inválido permanente] → va directo a DLQ y a ERROR visible para el operador; no se corrige editando el payload, sino creando un `commandId` nuevo con referencia auditada.
- [SMTP caído] → los jobs quedan en reintento con backoff y el negocio que los originó sigue en 200; la cola Rabbit no se bloquea porque el ack ya se hizo tras persistir.
- [Purga agresiva] → solo ENVIADA antigua pierde destinatario y variables; PENDIENTE y ERROR nunca se purgan sin revisión y el estado y las marcas se conservan para auditoría.
- [Zona horaria] → `fechaSalida` viaja como fecha y los vencimientos de lease y reintentos se calculan en servidor con `timestamptz`.
