---
description: "Prepara la demo de sustentación: enmienda la especificación, genera los planes, los ejecuta y deja un PR final. Alcance cerrado: circuit breaker visible por Postman en hotel-service, colecciones en tests/postman/ y Kafka UI."
agent: orquestador
model: opencode-go/deepseek-v4.1-flash
---

# Demo de sustentación: circuit breaker por Postman + Kafka UI

Ejecutas el ciclo completo **spec → planes → implementación → PR** para un delta cerrado y pequeño.
No es una campaña normal: el alcance está fijado abajo y **no se amplía**.

## Alcance cerrado (no negociable)

| # | Entregable | Zona |
|---|---|---|
| 1 | El circuit breaker de `hotel-service` → `reception-service` **funciona de verdad** con los valores que ya fija la spec (ventana 10, umbral 50 %, mínimo 5, abierto 10 s, 3 en half-open) y es **observable** | `services/hotel-service/` |
| 2 | La colección Postman del ciclo de vida del circuit breaker, más las colecciones de las rutas principales de la app | `tests/postman/` |
| 3 | Kafka UI en Compose para ver los mensajes de los topics | `infra/compose.yaml` |

**Fuera de alcance, sin excepciones:**

- Circuit breaker en el Gateway, en `reception-service`, `sales-service`, `inventory-service`,
  `identity-service`, `notification-service` o `reporting-service`. **Solo `hotel-service`.**
- Un endpoint o ruta nueva de negocio para "simular" el breaker. No se inventa contrato.
- Frontend. La demo se hace por Postman y por `curl`. `apps/**` no se toca.
- Cambiar los valores del circuit breaker que fija la spec. Los valores se respetan tal cual.
- Retirar rutas `LEGACY`, tocar versiones fijadas (`services/pom.xml`, `apps/*/package.json`,
  `README.md` raíz) ni abrir los endpoints de actuator al exterior por el Gateway.

---

## Fase 0. Preflight

```bash
python scripts/preflight.py
git switch main && git pull origin main
git status --short
```

Si hay `FALLA`, paras y lo escribes al final del turno con el detalle literal: no puedes preguntar,
pero sí informar. No sigas a la Fase 1 con una precondición rota.

Si el árbol tiene cambios sin commitear que no reconoces como tuyos, no los tires ni los commitees:
si están dentro de `docs/` o `services/` y bloquean el arranque, para y repórtalo; si están fuera de
las zonas de este comando, ignóralos y sigue.

Anota el JDK que indique el preflight y usa **siempre** `bash scripts/mvn.sh`, nunca `mvnw` a pelo.

---

## Fase 1. Enmienda de la especificación

Este delta **sí** cambia la especificación, así que va en su propio PR `docs:` antes de planificar.
Tienes permiso de edición sobre **cinco documentos concretos** de la spec, y solo dentro de las
secciones enumeradas abajo. Cualquier otro documento de `docs/especificacion/**` sigue bloqueado.
Si crees que hace falta tocar otra sección u otro documento, no lo toques: anótalo en
`docs/implementacion/decisiones/` y sigue.

### 1.1 Qué enmendar, sección por sección

**a) `docs/especificacion/infraestructura/configuracion.md` — tabla de variables.**

Hoy la tabla nombra las propiedades así:

```
resilience4j.circuitbreaker.sliding-window-size        → CB_WINDOW_SIZE
resilience4j.circuitbreaker.failure-rate-threshold     → CB_FAILURE_RATE
resilience4j.circuitbreaker.minimum-number-of-calls    → CB_MIN_CALLS
resilience4j.circuitbreaker.wait-duration-in-open-state → CB_OPEN_MS
resilience4j.circuitbreaker.permitted-number-of-calls-in-half-open-state → CB_HALF_OPEN_CALLS
```

**Esa forma no existe en Resilience4j 2.3.0.** Verificado leyendo
`META-INF/spring-configuration-metadata.json` del artefacto `resilience4j-spring-boot3:2.3.0`: bajo
`resilience4j.circuitbreaker` solo existen `backends`, `configs`, `instances`, `tags`,
`circuit-breaker-aspect-order` y `metrics.*`. Las claves de primer nivel se descartan **en silencio**
y el breaker corre con los defaults de la librería (ventana 100, mínimo 100 llamadas), con lo que
**nunca se abre** en una demo.

La forma correcta es:

```
resilience4j.circuitbreaker.configs.default.sliding-window-size                 → CB_WINDOW_SIZE
resilience4j.circuitbreaker.configs.default.failure-rate-threshold              → CB_FAILURE_RATE
resilience4j.circuitbreaker.configs.default.minimum-number-of-calls             → CB_MIN_CALLS
resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state         → CB_OPEN_MS
resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state → CB_HALF_OPEN_CALLS
resilience4j.timelimiter.configs.default.timeout-duration                       → CB_TIMEOUT_MS
```

Los **valores no cambian**: 10 / 50 / 5 / 10000 / 3. Se añade una fila nueva:

| Variable | Valor | Motivo |
|---|---|---|
| `CB_TIMEOUT_MS` | `6000` | TimeLimiter del breaker. Debe superar el read-timeout de Feign (5000 ms) para que el breaker no corte antes que la propia llamada. El default de Resilience4j es **1 s**, que abortaría llamadas sanas. |

Verificación de que `configs.default` sí aplica: en `CommonCircuitBreakerConfigurationProperties`
el método `findCircuitBreakerProperties(id)` cae a `configs.get("default")` cuando no hay instancia,
y `CircuitBreakerRegistry.getDefaultConfig()` es la que consume `Resilience4JCircuitBreakerFactory`.
La instancia concreta de Feign se llama `Feign.configKey(...)` alfanumerizado
(`pehotelhotelclientReceptionClientbloquearintBloqueoRequest`), así que **no la configures por
nombre**: usa `configs.default`.

**b) `docs/especificacion/infraestructura/configuracion.md` — health y readiness.**

Añadir una nota bajo la tabla: el estado del circuit breaker es diagnóstico interno y se consulta en
el puerto de gestión del servicio que lo declara, **nunca por el Gateway**. Para esta demo lo declara
solo `hotel-service`, que expone `circuitbreakers` y `circuitbreakerevents` en `localhost:8082`
(perfil local). El resto sigue exponiendo solo `health`.

**c) `docs/especificacion/infraestructura/gateway-eureka-docker.md` — tabla «Compose y puertos».**

Añadir una fila:

| Contenedor | Puerto contenedor | Host dev |
|---|---:|---|
| kafka-ui | 8080 | 127.0.0.1:8091 |

Y un párrafo de una línea: Kafka UI es **herramienta de observación de demo**, no una pieza del
MVP; se levanta siempre con el resto de la infraestructura (sin `profiles`), igual que Mailpit,
porque la sustentación necesita ver los mensajes sin pasos extra. No altera topics, retención ni
consumidores.

**d) `docs/especificacion/01-arquitectura.md` §4 Stack — Kafka UI.**

Añadir a la lista de infraestructura de demo, junto a Mailpit: *«Kafka UI (`kafbat/kafka-ui`, versión
fijada sin `latest`) para inspeccionar topics y mensajes durante la demo.»*

**e) `docs/especificacion/03-contrato-comun.md` §Servicio a servicio.**

Añadir una frase al párrafo de timeouts y cortacircuitos: la configuración del circuit breaker vive
en `resilience4j.circuitbreaker.configs.default.*` y `resilience4j.timelimiter.configs.default.*`,
no en claves de primer nivel, y quien lo declara expone `circuitbreakers` en su puerto de gestión
para diagnóstico. Deja claro que la política de negocio del 202 `PREPARANDO` **no cambia**: cuando el
breaker está abierto, `hotel-service` sigue respondiendo 202 `PREPARANDO` con `Retry-After`, porque el
`blockId` es durable y lo recupera RB3. El circuit breaker **no** introduce un 503 nuevo en esa ruta.

**f) `docs/especificacion/06-migracion-y-pruebas.md` §7 Entregables.**

Añadir a la lista: *«Colección Postman del ciclo de vida del circuit breaker (`tests/postman/`), con
evidencia de CLOSED → OPEN → HALF_OPEN → CLOSED.»*

### 1.2 Publica la enmienda

```bash
git switch -c docs-spec-demo-circuit-breaker
git add docs/especificacion/01-arquitectura.md \
        docs/especificacion/03-contrato-comun.md \
        docs/especificacion/06-migracion-y-pruebas.md \
        docs/especificacion/infraestructura/configuracion.md \
        docs/especificacion/infraestructura/gateway-eureka-docker.md
git commit -m "docs: enmienda la especificacion para la demo de circuit breaker"
git push -u origin docs-spec-demo-circuit-breaker
gh pr create --base main --head docs-spec-demo-circuit-breaker \
  --title "docs: enmienda la especificacion para la demo de circuit breaker" --body-file <cuerpo>
```

El cuerpo resume en 4-6 líneas: por qué la tabla de `resilience4j` estaba mal, qué se añade
(`CB_TIMEOUT_MS`, Kafka UI, endpoints de diagnóstico), y que los valores de la spec no cambian.

**Este PR lo mergeas tú** (`gh pr merge --squash --delete-branch`) y vuelves a `main`, porque la
Fase 2 depende de que la spec ya esté en `main`.

---

## Fase 2. Planes

Delega en **`planificador-especificaciones`**, en primer plano:

```
Task(agent: "planificador-especificaciones",
     description: "Genera planes de la demo de circuit breaker",
     prompt: "Cambió la especificación en el PR de demo de circuit breaker (ya mergeado en main).
              Documentos y secciones: configuracion.md (tabla de resiliencia y health),
              gateway-eureka-docker.md (tabla de puertos, fila kafka-ui), 01-arquitectura.md §4
              (Kafka UI), 03-contrato-comun.md §Servicio a servicio (ubicación de la config del
              breaker), 06-migracion-y-pruebas.md §7 (colección Postman).
              Referencia: origin/main.
              Alcance pedido: exactamente tres planes, uno por zona, porque cada
              ejecutora toca una zona distinta.
              1) back-hotel-resiliencia-demo (back, solo hotel-service).
              2) infra-kafka-ui (infra, infra/compose.yaml y .env.example).
              3) qa-postman-circuit-breaker (qa, tests/postman/), que depende de 1 y 2.
              Nada más. No amplíes el alcance ni inventes endpoints.
              Devuelve los planes, su orden, el PR y la verificación.")
```

El planificador publica los planes `propuesto`, sus filas de catálogo, su tanda y el PR `docs:`.

**Antes de mergear**, comprueba las dos cosas:

```bash
python scripts/validar-planes.py
python scripts/proximo-plan.py --comprobar
```

Si alguna falla, devuélvesela al planificador con la salida literal. Si están en verde, mergea
(`gh pr merge --squash --delete-branch`) y vuelve a `main`.

**Ojo con el calendario:** los tres planes son nuevos y no están en `orden.md`. El planificador los
añade a `orden.md` §2 y al `CALENDARIO` de `scripts/proximo-plan.py` en el mismo PR, que es la
condición para que `proximo-plan.py` los devuelva. Si tras el merge
`python scripts/proximo-plan.py` no los ve, ese es el problema: vuelve al planificador, no lo
parchees tú.

### Qué debe contener cada plan (revísalo al leerlo)

Son **tres** planes porque cada ejecutora tiene una zona distinta, y `plan-back` solo puede escribir
dentro de la zona que declara el plan:

| Zona real de `plan-back` | Archivos |
|---|---|
| `back` | `services/<servicio>-service/**`, `contracts/openapi/<servicio>.yaml`, schemas de eventos |
| `infra` | `infra/**`, `services/api-gateway/**`, `services/discovery-server/**`, `.env.example` |

`plan-qa` solo escribe en `tests/**` y `scripts/**`. Por eso `CB_TIMEOUT_MS` en `.env.example` **no
puede ir en el plan `back`**: va en el plan `infra`. Un plan que mezcle zonas no lo puede ejecutar
nadie sin saltarse sus permisos.

**`back-hotel-resiliencia-demo`** — ámbito `back`, componente `hotel-service`, modo `directo`:

1. Mover la configuración de `resilience4j.circuitbreaker.*` de primer nivel a
   `configs.default.*` y añadir `resilience4j.timelimiter.configs.default.timeout-duration:
   ${CB_TIMEOUT_MS:6000}`. Verificación: arrancar `hotel-service` y comprobar en el log de arranque
   que el `CircuitBreakerRegistry` no usa los defaults de 100 llamadas; el criterio observable real
   es la prueba del paso 4.

   **Por qué el `TimeLimiter` importa aquí:** `Resilience4JCircuitBreakerFactory` envuelve la llamada
   con un `TimeLimiter` cuyo default es **1 segundo** (`TimeLimiterConfig`), y el read-timeout de
   Feign es 5000 ms. Si no se configura, el breaker aborta llamadas que Feign habría completado bien,
   y la demo mostraría fallos que no existen. `CB_TIMEOUT_MS=6000` deja margen sobre los 5 s de Feign.
2. Exponer `circuitbreakers` y `circuitbreakerevents` en `management.endpoints.web.exposure.include`
   y permitir esas rutas en `platform/SecurityConfig.java` junto a `/actuator/health/**`.
   Verificación: `curl http://localhost:8082/actuator/circuitbreakers` responde 200 y JSON.
3. Confirmar que el breaker nace en estado `CLOSED` y que su id es el que reporta el endpoint.
   Verificación: el JSON sale con `"state":"CLOSED"`.
4. Reproducir el ciclo completo con reception detenido y documentarlo: 5 llamadas fallidas abren el
   breaker; la 6.ª responde **sin intentar la llamada de red**; a los 10 s el estado pasa a
   `HALF_OPEN`; con reception arriba, 3 llamadas correctas lo devuelven a `CLOSED`.

   **Cada una de las 5 llamadas va sobre una habitación distinta.** El índice único
   `uq_operacion_vigente` (`V5__hotel_operaciones.sql`) impide dos operaciones vigentes de la misma
   habitación, así que repetir sobre la misma devuelve `409 ROOM_OPERATION_IN_PROGRESS` **antes de
   Feign** y el breaker no acumula fallos. Usa seis o más habitaciones libres, o limpia las
   operaciones con `sp_habitacion_habilitar` entre tandas.
5. No tocar `OperacionHabitacionService`: el 202 `PREPARANDO` es correcto y se mantiene. El plan lo
   declara explícitamente en §3 para que nadie lo "arregle".
6. Registrar en el plan la evidencia del ciclo (salidas literales de los `curl` y los códigos HTTP).

**`infra-kafka-ui`** — ámbito `infra`, componente `infra`, modo `directo`:

1. Añadir `kafka-ui` a `infra/compose.yaml` **sin `profiles`**, junto a Kafka, para que arranque
   con el resto de la infraestructura y la demo no necesite un paso extra.
2. Imagen `kafbat/kafka-ui` con **tag fijo y verificado** (no `latest`). Antes de fijarlo, comprobar
   que el tag existe: `docker manifest inspect kafbat/kafka-ui:<tag>`. Si el tag no existe, se baja
   a la versión publicada más reciente que sí exista y se anota en el plan cuál se fijó y por qué.
3. Entorno: `KAFKA_CLUSTERS_0_NAME: hotel`, `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092`.
   Puerto `127.0.0.1:8091:8080`. Red `backend`. `depends_on: kafka: condition: service_healthy`.
4. Añadir `CB_TIMEOUT_MS=6000` a `.env.example` junto a las demás `CB_*`, sin valores reales. Va en
   este plan porque `.env.example` está en la zona `infra`, no en la zona `back`.
5. Verificación: `docker compose -f infra/compose.yaml config --quiet` termina en 0, y
   `docker compose -f infra/compose.yaml up -d kafka-ui` deja la UI respondiendo en
   `http://localhost:8091`.
6. Verificación de contenido: la UI lista los topics de `05-mensajeria.md`. Si no hay mensajes
   todavía, se anota en el plan cómo generarlos (un check-in o una venta por Postman) para que la
   demo tenga algo que mostrar.
7. No tocar nada más del Compose: ni healthchecks existentes, ni volúmenes, ni la red.

**`qa-postman-circuit-breaker`** — ámbito `qa`, componente `tests`, modo `directo`:
**depende de `back-hotel-resiliencia-demo`** y de `infra-kafka-ui`, porque la colección llama al
endpoint de actuator que expone el primero.

1. Crear `tests/postman/` con una colección por flujo y un entorno, **sin secretos**: las variables
   de entorno llevan `{{baseUrl}}`, `{{token}}`, `{{idHabitacion}}` y similares; el token se obtiene
   con la primera petición de login, no se commitea.
2. Colecciones por flujo, como mínimo: identidad (login, `me`), catálogo hotelero (listar sedes,
   habitaciones, detalle), recepción (check-in, listar estadías), ventas (carrito, alta, pago),
   reportes. Cada colección con un `name` claro y `{{baseUrl}}` apuntando al Gateway (`:8080`), que
   es como entra un cliente real.
3. **Colección `circuit-breaker`**, la importante. Secuencia numerada con `pm.test` que verifique
   códigos y, donde aplique, `Retry-After`:
   - `CB-01` login → guarda `{{token}}`.
   - `CB-02` `POST /api/habitacion/{id}/mantenimiento` con reception arriba → `200`, sobre la
     habitación `{{idHabitacionA}}`.
   - `CB-03` comprobación base: `GET http://localhost:8082/actuator/circuitbreakers` → `CLOSED`.
   - `CB-04` con reception detenido, **cinco llamadas al mantenimiento sobre cinco habitaciones
     distintas** (`{{idHabitacionB}}` … `{{idHabitacionF}}`) → cada una `202` con `Location` y
     `Retry-After`. Guarda el `blockId` del `Location` de la primera.

     **Por qué cinco habitaciones distintas y no cinco veces la misma:** el índice único
     `uq_operacion_vigente` (`V5__hotel_operaciones.sql`, sobre `id_habitacion` donde el estado es
     `PREPARANDO` o `APLICADA`) hace que la segunda llamada sobre la misma habitación devuelva `409
     ROOM_OPERATION_IN_PROGRESS` **antes de llegar a Feign**, y entonces el breaker no acumula fallos
     y nunca se abre. Cada habitación debe tener su propia operación vigente. El entorno de Postman
     necesita por tanto seis habitaciones libres y sin mantenimiento previo.
   - `CB-05` `GET /actuator/circuitbreakers` → `OPEN`. **Esta es la prueba.**
   - `CB-06` una llamada más sobre **otra** habitación (`{{idHabitacionG}}`) → `202`, y el estado
     sigue `OPEN`. Sirve para mostrar que la llamada ya no se intenta.
   - `CB-07` esperar 10 s, `GET /actuator/circuitbreakers` → `HALF_OPEN`.
   - `CB-08` con reception arriba, hasta 3 llamadas correctas → `200`.
   - `CB-09` `GET /actuator/circuitbreakers` → `CLOSED`.
   - `CB-10` `GET /api/habitacion/operaciones/{{blockId}}` → la operación creada en `CB-04` terminó
     `APLICADA` con el mismo `blockId` (RB3 la recuperó). Demuestra que el 202 era correcto.

   **Sobre la latencia, sé honesto en la demo:** si reception está **detenido**, el puerto rechaza la
   conexión y las llamadas fallan en milisegundos, no en 5 s. La diferencia de latencia solo se ve si
   el proceso está **pausado** (conexión que cuelga hasta el read-timeout). Como eso es frágil en
   Windows, la evidencia principal debe ser la **transición de estado en
   `/actuator/circuitbreakers`**, no el cronómetro. Anótalo así en la descripción de la carpeta.
4. Documentar en el propio JSON de la colección, en `description` de cada carpeta, **cuándo hay que
   apagar o pausar reception**. Postman no puede apagar un servicio: el guion es manual y la
   colección asume esos pasos. Deja escrito el comando exacto (`docker compose stop reception-service`
   o el `taskkill` del pid) en la descripción.
5. Verificación: importar la colección en Postman y correrla contra el entorno local completo; la
   secuencia entera pasa. Deja la salida en `tests/evidence/` o en el propio plan.

---

## Fase 3. Ejecución

Bucle normal del orquestador, sin saltarte nada:

```bash
python scripts/proximo-plan.py
```

Para cada plan que devuelva: `git switch -c <plan>`, delega en la ejecutora del ámbito
(`plan-back` para `back` e `infra`, `plan-qa` para `qa`), espera, y si no vuelve `BLOQUEADO`, abre
PR y mergea. Después registra en `docs/implementacion/estado.md` en su PR `docs:` aparte.

El orden real lo pone el script por las dependencias: primero `back-hotel-resiliencia-demo`, luego
`infra-kafka-ui`, y `qa-postman-circuit-breaker` al final, porque su `Depende de` lo exige.

**Reglas que no se relajan en esta campaña:**

- Si la ejecutora devuelve `BLOQUEADO`, no improvises: escribe el motivo en
  `docs/implementacion/decisiones/`, ciérralo en el resumen y sigue.
- Si `main` se rompe, encarga un plan de arreglo a `planificador-arreglos`. Tú no corriges código.
- **Si la ejecutora intenta ampliar el breaker a otro servicio, o inventar un endpoint nuevo, o
  tocar `apps/**`, detenla y ciérralo como bloqueo.** El alcance está cerrado.
- Los valores del breaker son los de la spec: 10 / 50 / 5 / 10 s / 3. No se "ajustan para que la
  demo se vea mejor".

---

## Fase 4. Verificación de la demo (tú, al final)

Sobre `main` ya mergeado, comprueba de verdad que la demo se sostiene. Si algo falla, no lo
arregles tú: es un plan de arreglo.

```bash
bash scripts/mvn.sh -f services/pom.xml -pl :hotel-service -am -q verify
python scripts/validar-planes.py
python scripts/validar-contratos.py --base origin/main
docker compose -f infra/compose.yaml config --quiet
```

Y la prueba manual mínima, que es la que vale:

1. Con todo arriba, `curl http://localhost:8082/actuator/circuitbreakers` → `CLOSED`.
2. Detén reception. Cinco mantenimientos sobre **cinco habitaciones distintas** → `202`. (Sobre la
   misma habitación la segunda da `409 ROOM_OPERATION_IN_PROGRESS` y el breaker no acumula fallos.)
3. El mismo `curl` → `OPEN`.
4. Espera 10 s → `HALF_OPEN`.
5. Levanta reception y haz hasta tres mantenimientos → `200`, y el `curl` → `CLOSED`.
6. Abre `http://localhost:8091` y muestra los mensajes.
7. `curl http://localhost:8082/actuator/circuitbreakers` y guarda el JSON final como evidencia.

Si algún paso no se cumple, para y dilo: no maquilles la evidencia. En particular, si el estado nunca
llega a `OPEN`, la causa más probable es que las llamadas estén fallando **antes** de Feign (un 409
por operación vigente, un 403, un 428 por falta de `If-Match`): revisa el código HTTP que devuelve
cada llamada antes de culpar al breaker.

---

## Fase 5. PR final

Crea una rama y un PR `docs:` con el **guion de la sustentación**, que es lo que se va a usar delante
del profesor:

```bash
git switch main && git pull origin main
git switch -c docs-demo-circuit-breaker-guion
```

Crea `docs/demo-circuit-breaker.md` con:

- Qué se va a mostrar, en una línea, y qué **no** se va a mostrar.
- El orden exacto de la demo, minuto a minuto: login, mantenimiento normal, apagar reception, cinco
  llamadas sobre habitaciones distintas, actuator `OPEN`, esperar, `HALF_OPEN`, levantar, `CLOSED`,
  operación `APLICADA`.
- **Los dos avisos que evitan que la demo falle en vivo:** (a) cada llamada del paso de apertura va
  sobre una habitación distinta, porque el índice `uq_operacion_vigente` devuelve `409
  ROOM_OPERATION_IN_PROGRESS` si se repite, y ese 409 no llega a Feign ni abre el breaker; (b) la
  evidencia del breaker es la **transición de estado** en `/actuator/circuitbreakers`, no el
  cronómetro, porque con reception apagado la conexión se rechaza en milisegundos.
- Los comandos exactos para apagar y levantar reception, y para abrir la UI de Kafka.
- La frase que explica **por qué** el 202 `PREPARANDO` es mejor que un 500: el `blockId` es durable y
  RB3 recupera la operación sin duplicar el bloqueo.
- Dónde están las colecciones (`tests/postman/`) y cómo importarlas.
- Qué decir si el profesor pregunta por el Gateway: el circuit breaker está en la llamada
  servicio-a-servicio, no en el Gateway, y por qué.

Commit, push y `gh pr create`. **Este PR lo dejas abierto**: es el artefacto que la persona va a
revisar y a usar en la exposición.

---

## Resumen final

Cuando termines, responde solo con:

- **Spec:** PR de la enmienda y qué se cambió.
- **Planes:** los ejecutados, con su PR.
- **Bloqueados:** con el motivo y el enlace a la decisión.
- **Verificación:** el resultado literal de los seis pasos de la Fase 4.
- **PR final:** el número.
- **Cómo se hace la demo:** tres líneas, lo mínimo para que la persona pueda empezar.

## Reglas duras

- **Nunca amplías el alcance.** Un solo circuit breaker, un solo servicio (`hotel-service`), un solo
  flujo (`hotel → reception`). Si algo "estaría bueno añadirlo", va al resumen como propuesta, no al
  código.
- **Nunca inventas contrato.** No hay endpoints nuevos, ni códigos de error nuevos, ni eventos nuevos.
- **Nunca tocas** `apps/**`, `services/pom.xml`, `apps/*/package.json` ni el `README.md` raíz.
- **Nunca tocas `docs/especificacion/**` fuera de la Fase 1**, y solo en las secciones enumeradas.
- **Nunca preguntas.** Decides y lo dejas escrito en `docs/implementacion/decisiones/`.
- **Nunca `git push --force` ni push a `main`.**
- **Nunca mergeas el PR final** de la Fase 5.

**Argumentos:** $ARGUMENTS
