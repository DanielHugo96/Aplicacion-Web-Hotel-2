# infra-kafka-ui — Kafka UI siempre levantada para inspeccionar los topics de la demo (F5)

| Campo | Valor |
|---|---|
| Plan | `infra-kafka-ui` |
| Ámbito | `infra` |
| Componente | `infra` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `propuesto` |
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

- [ ] 1. Verificar el tag antes de escribirlo: `docker manifest inspect kafbat/kafka-ui:v1.3.0 >/dev/null && echo OK`. Verificación: imprime `OK`. Si falla, probar `v1.2.0` y anotar en este plan el tag usado y el motivo.
- [ ] 2. Añadir el servicio `kafka-ui` en `infra/compose.yaml` justo después de `kafka` y antes de `rabbitmq`, sin `profiles`. Verificación: `grep -n "kafka-ui" -A 20 infra/compose.yaml` muestra el bloque con `image: kafbat/kafka-ui:v1.3.0` y ninguna línea `profiles`.
- [ ] 3. Fijar el entorno del clúster: `KAFKA_CLUSTERS_0_NAME: hotel` y `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092` (nombre interno del broker, no `localhost`). Verificación: las dos variables aparecen con esos valores exactos en el bloque del paso 2.
- [ ] 4. Publicar el puerto en loopback y unir a `backend`: `127.0.0.1:8091:8080` y `networks: [backend]`. Verificación: el bloque muestra el mapeo 8091→8080 y la red `backend`; el 8080 del host sigue siendo solo del Gateway.
- [ ] 5. Declarar la espera del broker: `depends_on: kafka: { condition: service_healthy }`, sin `healthcheck` propio. Verificación: el bloque muestra el `depends_on` con `condition: service_healthy` y no añade `healthcheck`.
- [ ] 6. Añadir `CB_TIMEOUT_MS=6000` a `.env.example` en el bloque de Feign y circuit breaker, detrás de la última variable CB existente y con un comentario de que es el TimeLimiter del breaker. Verificación: `grep -n "CB_" .env.example` lista las seis variables y la nueva vale 6000; no hay ningún valor de credencial nuevo.
- [ ] 7. Validar el archivo: `docker compose -f infra/compose.yaml --env-file .env config --quiet`. Verificación: termina con código 0 y sin salida.
- [ ] 8. Levantar solo la UI: `docker compose -f infra/compose.yaml --env-file .env up -d kafka-ui`. Verificación: el contenedor queda `running` y `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091` devuelve 200.
- [ ] 9. Comprobar el contenido: abrir `http://localhost:8091` y verificar que el clúster `hotel` lista los topics de [`05-mensajeria.md`](../especificacion/05-mensajeria.md) que ya existan en el broker. Verificación: la lista incluye al menos `hotel.habitacion.v1` y `reception.recepcion.v1` con sus particiones.
- [ ] 10. Si la lista sale vacía porque aún no hay mensajes, generar uno: un check-in por Postman contra el entorno local, y confirmar que el topic de recepción aparece con al menos un registro. Verificación: el topic muestra el offset incrementado en la UI.
- [ ] 11. Comprobar que no se tocó nada más: `git diff --stat main -- infra/compose.yaml` solo suma el bloque de `kafka-ui`. Verificación: ningún healthcheck, volumen ni red existente aparece modificado en el diff.
- [ ] 12. Ejecutar la verificación canónica de infraestructura. Verificación: `docker compose -f infra/compose.yaml --env-file .env up -d --wait` termina con los healthchecks verdes, incluido `kafka`, y `python scripts/validar-planes.py` sale con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno.
- **Eventos y comandos:** ninguno nuevo. La UI no produce ni consume: solo muestra los topics y grupos ya fijados en [`05-mensajeria.md`](../especificacion/05-mensajeria.md).
- **Persistencia:** ninguna. No se añade volumen; el estado de la UI es efímero.
- **Errores:** ninguno.
- **Configuración:** `.env.example` suma la variable del TimeLimiter; el nombre y el default son los de [`configuracion.md` § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), no un valor nuevo.

## 6. Pruebas

- Compose válido: `docker compose -f infra/compose.yaml --env-file .env config --quiet` → código 0, sin salida.
- UI arriba: `docker compose -f infra/compose.yaml --env-file .env up -d kafka-ui` → contenedor `running`; `curl -s -o /dev/null -w '%{http_code}' http://localhost:8091` → 200.
- Contenido: la UI muestra el clúster `hotel`, el broker `kafka:9092` y los topics existentes con particiones y offsets; si no había mensajes, el paso 10 genera uno y se ve el offset.
- Aislamiento: `docker ps` confirma que Kafka UI no comparte puerto con el Gateway (`127.0.0.1:8080`) y que solo ella usa el 8091.
- Evidencia: captura o salida literal en este plan de los pasos 7, 8 y 9.
- Comandos canónicos: los de la sección «Verificación» del `README.md` raíz para Compose.

## 7. Preguntas abiertas

Ninguna. La spec fija la imagen, el puerto y que va sin `profiles`; el tag concreto es una versión publicada verificada en el registro, no un valor de negocio.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] `docker compose ... config --quiet` en 0 y la UI en `http://localhost:8091` con los topics visibles.
- [ ] Tag fijado y verificado con `docker manifest inspect`; sin `latest` en ningún sitio.
- [ ] `infra/compose.yaml` sin cambios fuera del bloque `kafka-ui`.
- [ ] Sin preguntas abiertas.
