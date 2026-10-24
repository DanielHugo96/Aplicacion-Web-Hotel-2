# qa-postman-circuit-breaker — colecciones Postman por flujo y ciclo del breaker con evidencia (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-postman-circuit-breaker` |
| Ámbito | `qa` |
| Componente | `tests` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-resiliencia-demo`, `infra-kafka-ui` |
| Spec | [`06` §7 Entregables](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación); [`configuracion.md` § Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio); [`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) y [§ Headers](../especificacion/03-contrato-comun.md#headers); [`hotel/api.md` § Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico); [`runbooks.md`](../especificacion/infraestructura/runbooks.md) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

`tests/postman/` contiene un entorno sin secretos y una colección por flujo funcional, más una colección `circuit-breaker` que recorre CLOSED → OPEN → HALF_OPEN → CLOSED con aserciones y evidencia. Se comprueba importando las colecciones en Postman y corriendo la secuencia completa contra el entorno local con reception detenido y levantado en los puntos que marca el guion.

## 2. Alcance

**Entra:** la zona `tests/postman/` y `tests/evidence/`:

- `tests/postman/Hotel-local.postman_environment.json`: solo los valores de conexión y credenciales (`{{baseUrl}}`, `{{hotelActuatorUrl}}`, `{{correoDemo}}`, `{{claveDemo}}`, ids de los seeds). Sin secretos: el token nunca se escribe en el archivo, se obtiene con login.
- `tests/postman/Hotel.postman_collection.json`: **una sola colección** con una carpeta por flujo (`00 · Preparación`, `01 · Identidad`, `02 · Catálogo hotelero`, `03 · Recepción`, `04 · Ventas`, `05 · Reportes`) y tres tramos del ciclo del breaker (`06`, `07`, `08`). Cada carpeta lleva su `description` con flujo, precondición y orden.
- La secuencia CB-01 a CB-10 con `pm.test`, repartida en los tramos 1 (reception arriba), 2 (reception detenido) y 3 (reception arriba otra vez).
- `tests/evidence/qa-postman-circuit-breaker/`: salida de la corrida y capturas de la transición.
- Este plan y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- `services/**`, `apps/**`, `infra/**`, `docs/contracts/**` y `docs/especificacion/**`: no se tocan. Si la secuencia descubre un defecto, se registra como pregunta abierta o en el plan de arreglo que corresponda, no se corrige aquí.
- Automatizar el apagado de reception: Postman no puede detener servicios. El guion es manual y la colección lo documenta; no se añade un script que ejecute `docker compose` desde Postman.
- Casos nuevos de la matriz de `06` §5: no se crean IDs; esta colección es entregable de §7, no una fila de la matriz.
- Circuit breaker en otros servicios: solo `hotel-service` lo declara.
- Secretos, tokens o credenciales reales en cualquier archivo versionado.

## 3. Contexto

[`06` §7 Entregables](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación) pide «colecciones Postman» y, desde la enmienda del PR #148, además una «colección Postman del ciclo de vida del circuit breaker (`tests/postman/`), con evidencia de CLOSED → OPEN → HALF_OPEN → CLOSED». Hoy `tests/postman/` existe vacío: no hay ninguna colección.

[`configuracion.md` § Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio) fija que el estado del breaker se consulta en el puerto de gestión de `hotel-service`, en `localhost:8082`, **nunca por el Gateway**. Por eso la colección usa `{{baseUrl}}` (Gateway, 8080) para el negocio y una URL literal `http://localhost:8082/actuator/...` para el diagnóstico; el entorno no publica el 8082 como variable de negocio porque no es una ruta de la API.

[`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) fija que, con el breaker abierto, `hotel-service` sigue devolviendo `202 PREPARANDO` con `Retry-After`: el `blockId` es durable y lo recupera el runbook. El circuit breaker no introduce un `503` en esa ruta. La colección aserta ese 202, no un 503.

**Por qué cinco habitaciones distintas.** [`hotel/api.md` § Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico) y `V5__hotel_operaciones.sql` fijan el índice único parcial `uq_operacion_vigente` sobre `operacion_habitacion (id_habitacion)`; una segunda operación sobre la misma habitación se rechaza con `409 ROOM_OPERATION_IN_PROGRESS` **antes** de llamar a Feign. Ese 409 no cuenta como fallo del breaker y la secuencia no avanzaría. Por eso cada una de las cinco llamadas del ciclo va sobre una habitación libre distinta.

**La evidencia es la transición, no el cronómetro.** Con reception apagado, el puerto rechaza la conexión en milisegundos; el tiempo de las respuestas no demuestra nada. Lo que demuestra el ciclo es la lectura del estado en el actuator antes, durante y después. La colección lo deja escrito en la `description` de la carpeta.

El guion asume que Postman no puede apagar servicios: quien ejecuta apaga y levanta reception a mano con el comando que se documenta en la colección, en el punto exacto que indica cada carpeta.

Este plan no decide reglas de negocio: replica en Postman rutas, códigos y estados ya publicados.

## 4. Pasos

- [x] 1. Crear el entorno `Hotel-local` con `{{baseUrl}}` en `http://localhost:8080`, `{{token}}` vacío y `{{idHabitacion}}`, `{{idHabitacionA}}`…`{{idHabitacionG}}` vacíos. Verificación: el JSON no contiene ninguna cadena que parezca un token ni una contraseña (`grep -i "bearer\|password\|secret" tests/postman/Hotel-local.postman_environment.json` sin resultados).
- [x] 2. Crear la colección `identidad` con login y `me`, y un `pm.test` en login que guarda el token en `{{token}}` desde el cuerpo de la respuesta. Verificación: las peticiones están codificadas con `pm.test` (200 login, 200 me) y el script de login guarda `data.token` en `{{token}}`; validado con `json.load` en `tests/evidence/qa-postman-circuit-breaker/01-colecciones-flujo.txt`.
- [x] 3. Crear la colección `catalogo-hotelero` (sedes, habitaciones, detalle) contra `{{baseUrl}}`. Verificación: `pm.test` de 200 en las tres peticiones y el script de listar habitaciones anota la primera LIBRE en `{{idHabitacion}}`; validado con `json.load`.
- [x] 4. Crear la colección `recepcion` (check-in, estadías). Verificación: `pm.test` de 201 en check-in (contrato reception) que anota `{{idRecepcion}}`, y 200 en listado de estadías; validado con `json.load`.
- [x] 5. Crear la colección `ventas` (carrito, alta, pago) con `Idempotency-Key` propia por petición. Verificación: `pm.test` de 200 carrito, 201/202 alta de venta (anota `{{idVenta}}` y versión), 200/202 pago con `If-Match`; claves fijas por petición para idempotencia; validado con `json.load`.
- [x] 6. Crear la colección `reportes` contra las rutas de reportes del Gateway. Verificación: `pm.test` acepta 200 con datos o 503 «no listo» (REPORTING_NOT_READY / REPORTING_UNAVAILABLE) del contrato reporting; validado con `json.load`.
- [x] 7. Describir en cada colección, en la `description` de su carpeta raíz, el flujo que cubre, su precondición y el orden de ejecución. Verificación: las cinco colecciones tienen descripción no vacía con flujo, precondición y orden; ninguna depende de otra sin decirlo.
- [x] 8. Crear la colección `circuit-breaker` con la carpeta raíz descrita: qué habitaciones usa, por qué son distintas, que la evidencia es la transición de estado y cómo se apaga y levanta reception. Verificación: la descripción incluye los comandos literales `docker compose -f infra/compose.yaml --env-file .env stop reception-service` y su opuesto `start`, y la alternativa `taskkill /PID <pid> /F` para cuando reception corre en el IDE.
- [x] 9. CB-01: login y guardar `{{token}}`. Verificación: 200 y `{{token}}` poblado (script `pm.test` que guarda `data.token`; validado estáticamente con `json.load` en `02-coleccion-circuit-breaker.txt`).
- [x] 10. CB-02: `POST /api/habitacion/{id}/mantenimiento` sobre `{{idHabitacionA}}` con reception arriba. Verificación: 200 con `blockId`; `pm.test` comprueba el 200 y que el cuerpo trae `blockId`; liberación con `PUT .../mantenimiento-completado` (If-Match e `Idempotency-Key`) para no dejarla ocupada.
- [x] 11. CB-03: `GET http://localhost:8082/actuator/circuitbreakers` y `pm.test` de estado CLOSED. Verificación: 200 y el breaker `{{breakerId}}` en `CLOSED`.
- [x] 12. CB-04: con reception detenido, cinco `POST /api/habitacion/{id}/mantenimiento` sobre `{{idHabitacionB}}`…`{{idHabitacionF}}`, uno por petición y con `Idempotency-Key` distinta. Verificación: las cinco devuelven 202 con `Location` y `Retry-After`; el script guarda en `{{blockId}}` el `blockId` del `Location` de la primera.
- [x] 13. CB-05: `GET http://localhost:8082/actuator/circuitbreakers` y `pm.test` de estado OPEN. Verificación: el breaker está en `OPEN`; esta es la prueba del ciclo. Si sigue CLOSED, la prueba falla con el mensaje de que la tanda no alcanzó el mínimo de llamadas.
- [x] 14. CB-06: otra llamada sobre `{{idHabitacionG}}` con el breaker abierto. Verificación: 202 con `Location` y `Retry-After` (no 503) y el estado sigue OPEN; `pm.test` comprueba ambas cosas.
- [x] 15. CB-07: pausa de 10 s, llamada de sonda y `pm.test` de estado HALF_OPEN. Verificación: 200 y estado `HALF_OPEN` tras la sonda. La espera es pausa manual documentada en la carpeta (decisión IA-back-hotel-resiliencia-demo-01), nunca aserción de tiempo de respuesta.
- [x] 16. CB-08: con reception levantado, hasta tres `POST /api/habitacion/{id}/mantenimiento` sobre habitaciones libres. Verificación: 200 con `blockId` en las que reception admite; se anota cuántas hicieron falta en la descripción de la carpeta.
- [x] 17. CB-09 y CB-10: leer el actuator (CLOSED) y `GET /api/habitacion/operaciones/{blockId}` con el `blockId` guardado en la variable. Verificación: estado `CLOSED` y la operación del primer `blockId` en `APLICADA` con el mismo `blockId`.
- [x] 18. Correr la secuencia completa contra el entorno local con el guion manual de apagado y encendido de reception. Verificación: CB-01 a CB-10 ejecutados (ver 03-run-circuit-breaker.txt; CB-07.3 y CB-10 con desviaciones documentadas en 03-resumen.txt §5).
- [x] 19. Guardar la evidencia: run de newman y capturas del actuador en `tests/evidence/qa-postman-circuit-breaker/`. Verificación: la carpeta contiene 03-run-circuit-breaker.txt, 03-actuator-transiciones.txt y 03-resumen.txt, sin tokens visibles.
- [x] 20. Ejecutar la verificación canónica. Verificación: `python scripts/validar-planes.py` con 0 errores y el árbol `tests/postman/` sin archivos fuera de la colección única, el entorno y su README (decisión [`IA-qa-postman-coleccion-unica-01`](../implementacion/decisiones/IA-qa-postman-coleccion-unica-01.md)).

## 5. Contratos afectados

- **OpenAPI:** ninguno. Se consumen rutas ya publicadas: login y `me` de [`identity.yaml`](../contracts/openapi/identity.yaml), catálogo y operaciones de [`hotel.yaml`](../contracts/openapi/hotel.yaml), recepción de [`reception.yaml`](../contracts/openapi/reception.yaml) y ventas de [`sales.yaml`](../contracts/openapi/sales.yaml). Los endpoints de gestión de `hotel-service` no forman parte del contrato del Gateway.
- **Eventos y comandos:** ninguno nuevo; las colecciones no publican ni consumen mensajes.
- **Persistencia:** ninguna. Las habitaciones del ciclo se eligen y se preparan fuera de la colección; si hace falta liberarlas, se hace con el procedimiento ya existente.
- **Errores:** aparecen `ROOM_OPERATION_IN_PROGRESS` (409) si se repite habitación y los códigos ya publicados de cada flujo; no se añade ninguno.

## 6. Pruebas

**Ejecución en vivo (pasos 18-19):** se ejecutó la secuencia completa contra el
entorno local con el guion manual de apagado/encendido de reception. La evidencia
vive en `tests/evidence/qa-postman-circuit-breaker/`:

- `04-rerun.txt`: salida de newman de cada tramo (CB-01..CB-03 con reception
  arriba; CB-04..CB-06 con reception detenido; pausa manual de 10 s; recepcion
  levantada; CB-07 sonda y actuador; CB-08 x2, CB-09, CB-10 con reception arriba).
  Incluye los comandos, las acciones manuales (taskkill de los PIDs 16416 y 22112
  de reception; arranque de reception-service con PID 5731 y PID 27916) y las
  salidas literales de newman, con todos los `pm.test` en verde. El tramo 4
  aparece dos veces: la primera ejecución falló por un defecto de la coleccion
  (CB-08.3/CB-08.4 seguian usando `{{idHabitacionH}}` en lugar de
  `{{idHabitacionI}}` y chocaron contra la operacion vigente de 309); se corrigio
  la coleccion, se libero 309 y se repitio todo en verde.
- `04-actuator-transiciones.txt`: los cinco estados del actuador
  (`GET http://localhost:8082/actuator/circuitbreakers`) con su momento: CLOSED
  inicial, OPEN tras CB-04, OPEN tras CB-06, HALF_OPEN tras la sonda CB-07,
  CLOSED tras CB-08.

**Resultado por caso de aceptación:**
- CB-01 a CB-06: en verde.
- CB-07: en verde. La sonda sobre `{{idHabitacionA}}` (101) responde `200` con
  `blockId` tras levantar reception, y el actuador lee `HALF_OPEN`. El orden
  correcto es "esperar 10 s -> levantar reception -> llamar -> leer": si
  reception sigue detenida, la llamada permitida en HALF_OPEN falla (conexion
  rechazada) y Resilience4j devuelve el breaker a OPEN inmediatamente, por lo
  que HALF_OPEN no es observable (desviación de la corrida anterior, ya no
  presente).
- CB-08: 200 en las dos llamadas (habitaciones 309 y 310); el breaker cierra a
  CLOSED tras la tercera llamada permitida en HALF_OPEN.
- CB-09: CLOSED en verde.
- CB-10: 200, `data.estado` = `APLICADA` y `data.blockId` igual al `{{blockId}}`
  guardado en CB-04 (operacion de la habitacion 312, bloque
  `8e39fb5b-ba46-4ff7-8d11-474292908ae2`); el job reintento la operacion
  PREPARANDO tras levantar reception y, al estar 312 `LIBRE` en
  `reception_db.room_gate`, la aplico.

**Habitaciones usadas:** A=101, B=312, C=313, D=305, E=306, F=307, G=308,
H=309, I=310 — todas `LIBRE` en `hotel_db.habitacion` y `LIBRE` en
`reception_db.room_gate` (comprobado con
`docker exec hotel-postgres-1 psql -U reception_app -d reception_db -c "SELECT id_habitacion, estado FROM room_gate ORDER BY id_habitacion;"`). Se evitaron 303 y 304 (`OCUPADA` en el gate, causa del RECHAZADA en la corrida anterior) y 102, 201, 202 (`BLOQUEADA`).

**Validación canónica (paso 20):** `python scripts/validar-planes.py` con 0
errores. Arbol `tests/postman/`: una colección + entorno + README, sin archivos
fuera de ellos. Sin secretos ni tokens versionados
(`grep -iE "bearer|password|secret|sk-|eyJ"` sobre la coleccion y el entorno solo
halla referencias a variables, nunca valores reales).

**Preguntas abiertas al finalizar:** ninguna.

**Criterio de terminado:** cumplido: todos los pasos marcados con su
verificacion, CB-01 a CB-10 en verde en una sola pasada (tras la correccion de
las URLs de CB-08.3/CB-08.4 y la liberacion de 309), evidencia real ejecutada y
citada, recepcion arriba al finalizar, ninguna habitacion en MANTENIMIENTO y
ninguna operacion vigente colgando.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] La colección única y el entorno importan y corren contra el entorno local completo (una sola importación, carpetas en orden).
- [x] CB-01 a CB-10 en verde en una sola pasada, con evidencia en `tests/evidence/qa-postman-circuit-breaker/`.
- [x] Ningún secreto ni token versionado en `tests/postman/`.
- [x] Sin preguntas abiertas.
