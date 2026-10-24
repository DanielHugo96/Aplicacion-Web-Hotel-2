# Demo de sustentación — circuit breaker por Postman y Kafka UI

Guion para la exposición. El objetivo es enseñar, en vivo y con evidencia, que el
circuit breaker de `hotel-service` → `reception-service` **funciona de verdad** y que una
caída de reception no rompe el negocio. Se apoya en las colecciones de `tests/postman/` y en
Kafka UI.

## Qué se muestra y qué no

**Se muestra:** el ciclo completo del breaker —`CLOSED → OPEN → HALF_OPEN → CLOSED`— leído en
el puerto de gestión de `hotel-service`, la respuesta `202 PREPARANDO` que el servicio sigue
dando con el breaker abierto, la recuperación de esa operación por el job RB3 hasta `APLICADA`,
y los mensajes de los topics en Kafka UI.

**No se muestra:** frontend (la demo es por Postman y `curl`), circuit breaker en el Gateway ni
en el resto de servicios, y ninguna ruta nueva de negocio. El alcance es un solo breaker, un solo
servicio y un solo flujo: `hotel-service` → `reception-service`.

## Orden de la demo, minuto a minuto

| Min | Qué se hace | Qué se ve |
|---:|---|---|
| 0 | Arranca el stack y abre `http://localhost:8091` (Kafka UI) | Clúster `hotel` online, 9 topics |
| 1 | Postman → `identidad` → login | `200`, el token queda en `{{token}}` |
| 2 | `catalogo-hotelero` → listar habitaciones | `200`; se anotan seis habitaciones libres |
| 3 | `circuit-breaker` → **CB-02**: mantenimiento con reception arriba | `200` con `blockId` |
| 3.5 | **CB-03**: `curl http://localhost:8082/actuator/circuitbreakers` | `"state":"CLOSED"` |
| 4 | **Apagar reception** (comando abajo) | El puerto 8083 deja de responder |
| 5 | **CB-04**: cinco mantenimientos sobre **cinco habitaciones distintas** | Cinco `202` con `Location` y `Retry-After` |
| 7 | **CB-05**: el mismo `curl` del actuator | **`"state":"OPEN"`** — *esta es la prueba* |
| 8 | **CB-06**: una llamada más, sobre otra habitación | `202` (no 503), y el estado sigue `OPEN`: la llamada ya no se intenta |
| 9 | Esperar 10 s | — |
| 10 | **Levantar reception** y **CB-07**: una llamada de sonda | `200` y el actuator pasa a `"state":"HALF_OPEN"` |
| 11 | **CB-08**: una o dos llamadas sanas más | `200`, y el actuator queda **`"state":"CLOSED"`** |
| 12 | **CB-10**: `GET /api/habitacion/operaciones/{{blockId}}` | La operación del primer `202` está **`APLICADA`** con el mismo `blockId` |
| 13 | Volver a Kafka UI | Los mensajes de `hotel.habitacion.v1` y los topics de la operación |

## Los dos avisos que evitan que la demo falle en vivo

**(a) Cada llamada del paso de apertura va sobre una habitación distinta.**
El índice único parcial `uq_operacion_vigente` (`V5__hotel_operaciones.sql`) impide dos
operaciones vigentes sobre la misma habitación. Repetir habitación devuelve
`409 ROOM_OPERATION_IN_PROGRESS` **antes de llegar a Feign**, así que ese 409 no cuenta como
fallo del breaker y el breaker nunca se abriría. Hacen falta **seis habitaciones libres**; y
"libre" significa libre en `hotel_db.habitacion` **y** en `reception_db.room_gate`, porque una
habitación `OCUPADA` o `BLOQUEADA` en el gate también produce un rechazo que no es del breaker.
Comprobación rápida:

```bash
docker exec hotel-postgres-1 psql -U reception_app -d reception_db \
  -c "SELECT id_habitacion, estado FROM room_gate WHERE estado='LIBRE' ORDER BY id_habitacion;"
```

**(b) La evidencia del breaker es la transición de estado, no el cronómetro.**
Con reception **apagado**, el puerto rechaza la conexión en milisegundos: las llamadas no tardan
5 s y el cronómetro no demuestra nada. La diferencia de latencia solo se vería con el proceso
**pausado** (conexión que cuelga hasta el read-timeout), que es frágil de montar en Windows. Lo
que demuestra el ciclo es la lectura de `/actuator/circuitbreakers` antes, durante y después.

**Aviso extra sobre `HALF_OPEN`.** En Resilience4j 2.3.0 el paso `OPEN → HALF_OPEN` **no es un
reloj**: la lectura de solo lectura a los 10 s sigue devolviendo `OPEN`, y la transición se evalúa
cuando llega una llamada después del intervalo. Por eso el guion espera, **levanta reception** y
hace primero la sonda: con reception sano esa llamada tiene éxito y el breaker queda en
`HALF_OPEN`, que es observable. Si se hace la sonda con reception aún caído, el fallo devuelve el
breaker a `OPEN` y no hay `HALF_OPEN` que enseñar. Está explicado en
[`docs/implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md`](implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md).

## Comandos exactos

```bash
# Apagar reception (corre en el IDE, no en Compose)
jps -l | grep ReceptionServiceApplication     # anota el PID
taskkill //PID <pid> //F

# Levantar reception
bash scripts/mvn.sh -f services/pom.xml -pl :reception-service spring-boot:run

# Si reception corriera en contenedores, el equivalente sería:
#   docker compose -f infra/compose.yaml --env-file .env stop reception-service
#   docker compose -f infra/compose.yaml --env-file .env start reception-service

# El estado del breaker, siempre en el puerto de gestión (nunca por el Gateway)
curl -s http://localhost:8082/actuator/circuitbreakers

# Kafka UI
#   http://localhost:8091  — clúster `hotel`, topics y mensajes
```

## Por qué el 202 `PREPARANDO` es mejor que un 500

Cuando reception no está, `hotel-service` **no** devuelve un error: responde `202 PREPARANDO` con
`Location` y `Retry-After`. El `blockId` de esa operación es **durable** y ya está persistido; el
job RB3 lo reintenta con el **mismo** `blockId`, y cuando reception vuelve, la operación pasa a
`APLICADA` sin duplicar el bloqueo. El cliente sabe que su intención se aceptó y puede consultar
su estado. Un `500` habría perdido esa intención y obligado al cliente a reintentar a ciegas. El
circuit breaker **no** introduce un `503` en esa ruta: el contrato no cambia.

Frase para decir en voz alta: *«el breaker protege la llamada de red, no la intención de negocio;
por eso el 202 sigue siendo la respuesta correcta y RB3 recupera la operación sin duplicar nada»*.

## Las colecciones

Están en [`tests/postman/`](../tests/postman/):

- `Hotel-local.postman_environment.json` — entorno sin secretos (`{{baseUrl}}`,
  `{{token}}`, `{{idHabitacionA}}`…`{{idHabitacionG}}`, `{{blockId}}`).
- `identidad`, `catalogo-hotelero`, `recepcion`, `ventas`, `reportes` — una por flujo.
- `circuit-breaker` — la de la demo, CB-01 a CB-10.

**Cómo importarlas:** Postman → *Import* → arrastra los `.json` de `tests/postman/` → elige el
entorno `Hotel-local` arriba a la derecha. El token **no** está en el archivo: lo obtiene `CB-01`
con el login. `{{baseUrl}}` apunta al Gateway (`http://localhost:8080`), que es como entra un
cliente real; el diagnóstico va a `localhost:8082` porque es el puerto de gestión del servicio.

Para correrlas por consola:

```bash
npx newman run tests/postman/circuit-breaker.postman_collection.json \
  -e tests/postman/Hotel-local.postman_environment.json --reporters cli
```

## Si el profesor pregunta por el Gateway

El circuit breaker está en la **llamada servicio a servicio** (`hotel-service` → `reception-service`),
no en el Gateway, y es deliberado. En el Gateway el breaker protegería al cliente de un servicio
caído, pero **no** evitaría que `hotel-service` siguiera gastando su read-timeout de 5 s en cada
una de las operaciones de mantenimiento y baja mientras reception está caído. El punto de dolor
está en la llamada interna: ahí es donde hay que cortar. Además, el Gateway enruta a servicios por
Eureka y ya tiene su propio manejo de `503`; duplicar la política en los dos sitios daría dos
veredades para el mismo fallo. La spec lo fija así y este trabajo lo respeta: **un solo breaker,
en `hotel-service`**.

## Evidencia de esta corrida

- [`tests/evidence/demo-circuit-breaker/verificacion-final.txt`](../tests/evidence/demo-circuit-breaker/verificacion-final.txt)
  — salida literal de los siete pasos de la verificación.
- [`tests/evidence/qa-postman-circuit-breaker/04-rerun.txt`](../tests/evidence/qa-postman-circuit-breaker/04-rerun.txt)
  — corrida de newman de la colección, con todas las aserciones en verde.
- [`tests/evidence/qa-postman-circuit-breaker/04-actuator-transiciones.txt`](../tests/evidence/qa-postman-circuit-breaker/04-actuator-transiciones.txt)
  — los cinco JSON del actuator: `CLOSED → OPEN → OPEN → HALF_OPEN → CLOSED`.
- [`tests/evidence/qa-postman-circuit-breaker/03-resumen.txt`](../tests/evidence/qa-postman-circuit-breaker/03-resumen.txt)
  — habitaciones usadas, PIDs y desviaciones registradas.
