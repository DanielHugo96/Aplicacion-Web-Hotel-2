# Kafka (KRaft) del proyecto `hotel`

Esta carpeta documenta el broker de desarrollo del plan `infra-compose`. La
configuración efectiva vive como variables de entorno en
[`../compose.yaml`](../compose.yaml), porque la imagen `apache/kafka` traduce
cada `KAFKA_*` a una clave de `server.properties`.

## Modo

KRaft de un solo nodo: el mismo proceso es `broker,controller` (sin ZooKeeper).
El cluster id lo aporta el default de la imagen; no se fija a mano.

## Listeners

| Listener | Escucha | Anunciado | Uso |
|---|---|---|---|
| `INTERNAL` | `:9092` | `kafka:9092` | servicios en la red `backend` |
| `EXTERNAL` | `:29092` | `localhost:29092` | apps desde el host (localhost:29092) |
| `CONTROLLER` | `:9093` | — | quórum KRaft |

Publicación en el host: `127.0.0.1:29092` según la tabla de puertos de
`docs/especificacion/infraestructura/gateway-eureka-docker.md` §Compose y puertos
propuestos.

## Defaults de demo

Los que fija `docs/especificacion/05-mensajeria.md` §Topics y asignación exacta:
una partición por topic y factor de replicación 1 (sin alta disponibilidad) y
retención de 7 días (`KAFKA_LOG_RETENTION_HOURS=168`).

## Persistencia

El log del broker va al volumen nombrado `kafka_data`, montado en
`/var/lib/kafka/data`. `docker compose ... down` sin `-v` lo conserva.

## Healthcheck

El contenedor consulta el broker con las herramientas de la propia imagen
(`kafka-broker-api-versions.sh --bootstrap-server localhost:9092`). El estado
`healthy` es lo que permitirá a los planes de servicio encadenar
`depends_on: { kafka: { condition: service_healthy } }`.
