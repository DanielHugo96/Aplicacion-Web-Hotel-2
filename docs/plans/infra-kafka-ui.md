# infra-kafka-ui — Kafka UI siempre levantada para inspeccionar los topics de la demo (F5)

| Campo | Valor |
|---|---|
| Plan | `infra-kafka-ui` |
| Ámbito | `infra` |
| Componente | `infra` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `infra-compose` |
| Spec | [`gateway-eureka-docker.md` § Compose y puertos propuestos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos); [`01-arquitectura.md` § 4. Stack](../especificacion/01-arquitectura.md#4-stack); [`configuracion.md` § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios); [`06` §7 Entregables](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

`docker compose -f infra/compose.yaml --env-file .env up -d` levanta Kafka UI junto a Kafka, sin perfiles, y deja accesible en `http://localhost:8091` la lista de topics del broker con sus mensajes. Se comprueba con `config --quiet` en 0 y con la UI mostrando los topics de [`05-mensajeria.md`](../especificacion/05-mensajeria.md).

## 2. Alcance

**Entra:**

- `infra/compose.yaml`: el servicio `kafka-ui` con imagen fijada, entorno del clúster `hotel`, puerto `127.0.0.1:8091:8080`, red `backend` y `depends_on` de Kafka sano. Sin `profiles`.
- `.env.example`: `CB_TIMEOUT_MS=6000` en el bloque de Feign y circuit breaker, junto a las demás variables `CB_*`. Es zona `infra` por la norma §6.4, no de `back-hotel-resiliencia-demo`.
- Este plan y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- Healthchecks, volúmenes, redes, `restart` y el resto de servicios del Compose: no se tocan. Kafka UI no añade volumen ni healthcheck propio; su función es observar, no alterar.
- Topics, particiones, retención y consumidores: la UI es de solo lectura y no cambia nada de [`05-mensajeria.md`](../especificacion/05-mensajeria.md).
- `services/**`, `apps/**`, `tests/**`, `docs/contracts/**` y `docs/especificacion/**`.
- Las credenciales reales: `.env.example` sigue solo con nombres y defaults no sensibles.

## 3. Contexto

El PR #148 de la especificación enmienda la tabla de puertos de [`gateway-eureka-docker.md` § Compose y puertos propuestos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos): añade la fila `kafka-ui | 8080 | 127.0.0.1:8091` y el párrafo que la explica — es herramienta de observación de demo, se levanta **siempre** con el resto de la infraestructura, sin `profiles`, igual que Mailpit, para que la sustentación vea los mensajes sin pasos extra, y no altera topics, retención ni consumidores.

[`01-arquitectura.md` § 4. Stack](../especificacion/01-arquitectura.md#4-stack) fija la imagen (`kafbat/kafka-ui`) y la regla de versiones: versión fijada, **nunca `latest`**. La misma sección exige pinear tags o digests.

La imagen elegida es `kafbat/kafka-ui:v1.3.0`. Se verificó con `docker manifest inspect kafbat/kafka-ui:v1.3.0` que el tag existe y publica un índice multi-arquitectura; se comprobaron también `v1.4.0`, `v1.3.1`, `v1.2.1` y `v1.2.0`: las tres primeras no existen en el registro y `v1.2.0` sí, pero es anterior. `v1.3.0` es la versión publicada más reciente que se pudo resolver en el momento de planificar. La ejecutora repite la comprobación antes de fijar el tag definitivo y, si `v1.3.0` ya no estuviera, baja a la publicada más reciente que exista y anota en este plan cuál y por qué.

El puerto interno de la imagen es 8080; se publica en `127.0.0.1:8091` para no chocar con el Gateway, que ocupa el 8080 del host.

Este plan no decide nada de negocio: aplica la fila de puertos y la imagen que la spec ya fija.

## 4. Pasos

- [x] 1. Verificar el tag antes de escribirlo: `docker manifest inspect kafbat/kafka-ui:v1.3.0 >/dev/null && echo OK`. Verificación: imprime `OK`. Si falla, probar `v1.2.0` y anotar en este plan el tag usado y el motivo.

  Ejecutado: `docker manifest inspect kafbat/kafka-ui:v1.3.0 >/dev/null && echo OK` → `OK`. El tag `v1.3.0` sigue publicado y es el fijado; no hubo que bajar a `v1.2.0`.
- [x] 2. Añadir el servicio `kafka-ui` en `infra/compose.yaml` justo después de `kafka` y antes de `rabbitmq`, sin `profiles`. Verificación: `grep -n "kafka-ui" -A 20 infra/compose.yaml` muestra el bloque con `image: kafbat/kafka-ui:v1.3.0` y ninguna línea `profiles`.

  Ejecutado: bloque insertado en la línea 99, entre `kafka` (línea 60) y `rabbitmq` (línea 114). `grep` no devuelve ninguna línea `profiles` dentro del bloque.
- [x] 3. Fijar el entorno del clúster: `KAFKA_CLUSTERS_0_NAME: hotel` y `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092` (nombre interno del broker, no `localhost`). Verificación: las dos variables aparecen con esos valores exactos en el bloque del paso 2.

  Ejecutado: líneas 103 y 104, valores exactos `hotel` y `kafka:9092`.
- [x] 4. Publicar el puerto en loopback y unir a `backend`: `127.0.0.1:8091:8080` y `networks: [backend]`. Verificación: el bloque muestra el mapeo 8091→8080 y la red `backend`; el 8080 del host sigue siendo solo del Gateway.

  Ejecutado: `127.0.0.1:8091:8080` y `networks: - backend`. El 8080 del host sigue publicado solo por `api-gateway`.
- [x] 5. Declarar la espera del broker: `depends_on: kafka: { condition: service_healthy }`, sin `healthcheck` propio. Verificación: el bloque muestra el `depends_on` con `condition: service_healthy` y no añade `healthcheck`.

  Ejecutado: `depends_on: kafka: condition: service_healthy`; el bloque no declara `healthcheck`.
- [x] 6. Añadir `CB_TIMEOUT_MS=6000` a `.env.example` en el bloque de Feign y circuit breaker, detrás de la última variable CB existente y con un comentario de que es el TimeLimiter del breaker. Verificación: `grep -n "CB_" .env.example` lista las seis variables y la nueva vale 6000; no hay ningún valor de credencial nuevo.

  Ejecutado: `grep -n "CB_" .env.example` → líneas 49–55, seis variables, con `55:CB_TIMEOUT_MS=6000`. No se añadió ninguna credencial.
- [x] 7. Validar el archivo: `docker compose -f infra/compose.yaml --env-file .env config --quiet`. Verificación: termina con código 0 y sin salida.

  Ejecutado: `config_exit=0`, sin salida. Detalle literal en §6.
- [x] 8. Levantar solo la UI: `docker compose -f infra/compose.yaml --env-file .env up -d kafka-ui`. Verificación: el contenedor queda `running` y `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091` devuelve 200.

  Ejecutado: `hotel-kafka-ui-1 Up ... 127.0.0.1:8091->8080/tcp`; `curl` devolvió `200`.
- [x] 9. Comprobar el contenido: abrir `http://localhost:8091` y verificar que el clúster `hotel` lista los topics de [`05-mensajeria.md`](../especificacion/05-mensajeria.md) que ya existan en el broker. Verificación: la lista incluye al menos `hotel.habitacion.v1` y `reception.recepcion.v1` con sus particiones.

  Ejecutado: `GET http://localhost:8091/api/clusters` devuelve el clúster `hotel` en `online` y `GET http://localhost:8091/api/clusters/hotel/topics` lista 8 topics, incluidos `hotel.habitacion.v1` (partición 0, `offsetMax: 15`) y `reception.recepcion.v1` (partición 0, `offsetMax: 0`). Salida literal en §6.
- [x] 10. Si la lista sale vacía porque aún no hay mensajes, generar uno: un check-in por Postman contra el entorno local, y confirmar que el topic de recepción aparece con al menos un registro. Verificación: el topic muestra el offset incrementado en la UI.

  No aplica: la lista del paso 9 no está vacía (8 topics, `hotel.habitacion.v1` con offset 15). La condición del paso no se da, así que no se genera ningún check-in ni se toca ningún servicio.
- [x] 11. Comprobar que no se tocó nada más: `git diff --stat main -- infra/compose.yaml` solo suma el bloque de `kafka-ui`. Verificación: ningún healthcheck, volumen ni red existente aparece modificado en el diff.

  Ejecutado: `infra/compose.yaml | 18 ++++++++++++++++++` y `1 file changed, 18 insertions(+)`. No hay líneas eliminadas ni modificadas; ningún healthcheck, volumen ni red existente cambia.
- [x] 12. Ejecutar la verificación canónica de infraestructura. Verificación: `docker compose -f infra/compose.yaml --env-file .env up -d --wait` termina con los healthchecks verdes, incluido `kafka`, y `python scripts/validar-planes.py` sale con 0 errores.

  Ejecutado: `up -d --wait` termina con `up_exit=0` y los cinco contenedores en `Healthy`; `python scripts/validar-planes.py` → `0 error(es), 0 aviso(s)`.

## 5. Contratos afectados

- **OpenAPI:** ninguno.
- **Eventos y comandos:** ninguno nuevo. La UI no produce ni consume: solo muestra los topics y grupos ya fijados en [`05-mensajeria.md`](../especificacion/05-mensajeria.md).
- **Persistencia:** ninguna. No se añade volumen; el estado de la UI es efímero.
- **Errores:** ninguno.
- **Configuración:** `.env.example` suma la variable del TimeLimiter; el nombre y el default son los de [`configuracion.md` § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), no un valor nuevo.

## 6. Pruebas

Evidencia recogida en la ejecución del 2026-10-23, sobre la rama `infra-kafka-ui`, con el `.env` local presente (no se versiona; el Compose se ejecutó con `--env-file .env`).

- **Compose válido:** `docker compose -f infra/compose.yaml --env-file .env config --quiet` → `config_exit=0`, sin salida.

- **Tag verificado:** `docker manifest inspect kafbat/kafka-ui:v1.3.0 >/dev/null && echo OK` → `OK`.

- **UI arriba:** `docker compose -f infra/compose.yaml --env-file .env up -d kafka-ui` → arranca tras `Container hotel-kafka-1 Healthy`, `Container hotel-kafka-ui-1 Started`. `docker ps --filter name=kafka-ui`:

  ```
  hotel-kafka-ui-1	Up 22 seconds	127.0.0.1:8091->8080/tcp
  ```

  `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091` → `200`. `curl ... http://localhost:8091/api/clusters` → `200`.

- **Contenido:** `curl -s http://localhost:8091/api/clusters`:

  ```
  [{"name":"hotel","defaultCluster":null,"status":"online","lastError":null,"brokerCount":1,
    "onlinePartitionCount":57,"topicCount":8,"bytesInPerSec":null,"bytesOutPerSec":null,
    "readOnly":false,"version":"Unknown","features":["TOPIC_DELETION"]}]
  ```

  `curl -s http://localhost:8091/api/clusters/hotel/topics` lista ocho topics con su partición 0 y su `offsetMax`; los dos que exige el plan:

  ```
  hotel.habitacion.v1     partitionCount:1  partitions:[{partition:0, leader:1, offsetMin:0, offsetMax:15}]
  reception.recepcion.v1  partitionCount:1  partitions:[{partition:0, leader:1, offsetMin:0, offsetMax:0}]
  ```

  El resto de la lista: `hotel.sede.v1` (0), `identity.persona.v1` (12), `inventory.movimiento.v1` (0), `inventory.stock.v1` (9), `sales.venta.v1` (0) y los topics internos de Kafka. La UI muestra el clúster `hotel` con el broker `kafka:9092` y los topics con particiones y offsets. El paso 10 no aplica: no había lista vacía.

- **Aislamiento:** `docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'`:

  ```
  hotel-kafka-ui-1	Up 38 seconds	127.0.0.1:8091->8080/tcp
  hotel-postgres-1	Up 9 hours (healthy)	127.0.0.1:55432->5432/tcp
  hotel-rabbitmq-1	Up 9 hours (healthy)	127.0.0.1:5673->5672/tcp, 127.0.0.1:15673->15672/tcp
  hotel-kafka-1	Up 9 hours (healthy)	127.0.0.1:29092->29092/tcp
  hotel-mailpit-1	Up 9 hours (healthy)	127.0.0.1:1025->1025/tcp, 127.0.0.1:8025->8025/tcp
  ```

  Solo `kafka-ui` usa el 8091. El 8080 del host sigue siendo del Gateway (`api-gateway`, perfil `full`, no levantado en esta ejecución).

- **Diff acotado:** `git diff --stat main -- infra/compose.yaml` → `1 file changed, 18 insertions(+)`, sin líneas eliminadas.

- **Verificación canónica:** `docker compose -f infra/compose.yaml --env-file .env up -d --wait` → `up_exit=0`, con `postgres`, `kafka`, `rabbitmq`, `mailpit` y `kafka-ui` en `Healthy`. `python scripts/validar-planes.py` → `42 plan(es) revisado(s); 42 previsto(s) en el catálogo; 0 error(es), 0 aviso(s)`.

## 7. Preguntas abiertas

Ninguna. La spec fija la imagen, el puerto y que va sin `profiles`; `v1.3.0` se verificó con `docker manifest inspect` y quedó fijado sin cambios.

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] `docker compose ... config --quiet` en 0 y la UI en `http://localhost:8091` con los topics visibles.
- [x] Tag fijado y verificado con `docker manifest inspect`; sin `latest` en ningún sitio.
- [x] `infra/compose.yaml` sin cambios fuera del bloque `kafka-ui`.
- [x] Sin preguntas abiertas.
