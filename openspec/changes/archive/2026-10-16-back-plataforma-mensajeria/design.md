# Design

## Context

Ver `proposal.md - Why`. Los siete servicios comparten PostgreSQL lógico por base, Kafka KRaft y RabbitMQ ya sanos por `infra-compose`, y la convención JWT/envelope/salud/logs ya aplicada por `back-plataforma-comunes`. La entrega exige al menos una vez con efectos idempotentes; no existe orden entre Kafka y RabbitMQ.

## Goals / Non-Goals

**Goals:**

- Dejar una sola implementación de referencia de outbox/inbox, poller, orden y validación replicable en los siete servicios sin compartir entidades de dominio.
- Hacer observables los fallos mediante DLT/DLQ, alertas y logs con identificadores de correlación.
- Permitir que los planes de dominio solo añadan producción y consumo de sus tipos asignados.

**Non-Goals:**

- Implementar admisión, débito, cierres, limpieza, reportes o correos; esos flujos pertenecen a sus planes.
- Dimensionar producción (particiones, réplicas, retención) más allá de la demo; ni añadir Redis, Debezium o Config Server.

## Decisions

- Poller por servicio con `SELECT ... FOR UPDATE SKIP LOCKED` sobre el índice parcial de pendientes, lote de 50 cada 5 s, frente a un publicador central: evita un punto único y respeta una base por servicio con credenciales propias.
- Una fila de `outbox` por destino (`KAFKA` o `RABBIT`) con `published_at` por fila, frente a un flag compartido: impide marcar dos brokers con el mismo flag y permite reintentos independientes.
- Productor Kafka con `acks=all` e idempotencia más confirmación de offset tras commit local, frente a solo `acks=all`: el primero evita pérdidas del productor y el segundo evita reprocesar tras reinicio.
- Reintentos RabbitMQ con colas de TTL y exchange hotel.dlx.v1, frente a `nack-requeue` infinito: acota reintentos a 3, conserva `commandId` y deriva a DLQ observable.
- Validación estricta contra JSON Schemas v1 en ambos extremos, con errores permanentes directo a DLT/DLQ: evita envenenar el poller con filas inválidas.
- Alertas vía log estructurado más consulta SQL de RB4 por el operador, frente a métrica/Prometheus: sin infra extra para el MVP y con umbrales ya fijados.
- Purga de `inbox` con job diario por servicio tras 30 días sin borrar pendientes, frente a particionado: simple, auditable y alineado con la retención de idempotencia.
- Topología RabbitMQ declarada por código al arranque con usuarios/vhost/permisos en infra, frente a definiciones manuales: reproducible en cada entorno.
- Dimensionado queda en demo (1 partición, factor 1, 7 días) sin perfil prod: producción fuera del MVP.
- Reenvío DLT/DLQ manual vía runbook más CLI conservando identificadores, frente a herramienta admin nueva: sin código extra y con evidencia auditada.
- Este change solo deja la interfaz común; el cableado por servicio lo detalla cada plan de dominio según el catálogo.

## Risks / Trade-offs

- [Caída entre publicar y marcar como publicada] → Duplicado posible; mitigación con `inbox` y versiones/ciclos idempotentes.
- [Fila venenosa bloquea el lote] → Mitigación con backoff, alertas y derivación a DLT/DLQ tras agotar reintentos, más runbooks RB1/RB4.
- [Desfase entre proyección y gate] → Puede demorar admisión; nunca admite una habitación sucia ni habilita negocio con dependencia degradada.
- [Correo duplicado si el worker cae entre enviar y registrar] → Limitación aceptada; nunca duplica venta ni cobro.
