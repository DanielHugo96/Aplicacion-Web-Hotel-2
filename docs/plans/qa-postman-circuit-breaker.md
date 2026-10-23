# qa-postman-circuit-breaker — colecciones Postman por flujo y ciclo del breaker con evidencia (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-postman-circuit-breaker` |
| Ámbito | `qa` |
| Componente | `tests` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `back-hotel-resiliencia-demo`, `infra-kafka-ui` |
| Spec | [`06` §7 Entregables](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación); [`configuracion.md` § Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio); [`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) y [§ Headers](../especificacion/03-contrato-comun.md#headers); [`hotel/api.md` § Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico); [`runbooks.md`](../especificacion/infraestructura/runbooks.md) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

`tests/postman/` contiene un entorno sin secretos y una colección por flujo funcional, más una colección `circuit-breaker` que recorre CLOSED → OPEN → HALF_OPEN → CLOSED con aserciones y evidencia. Se comprueba importando las colecciones en Postman y corriendo la secuencia completa contra el entorno local con reception detenido y levantado en los puntos que marca el guion.

## 2. Alcance

**Entra:** la zona `tests/postman/` y `tests/evidence/`:

- `tests/postman/Hotel-local.postman_environment.json`: variables `{{baseUrl}}`, `{{token}}`, `{{idHabitacion}}` y las habitaciones del ciclo (`{{idHabitacionA}}`…`{{idHabitacionG}}`). Sin secretos: el token nunca se escribe en el archivo, se obtiene con login.
- Una colección por flujo: `identidad`, `catalogo-hotelero`, `recepcion`, `ventas` y `reportes`, cada una con sus peticiones y una `description` de carpeta que dice qué flujo cubre y su precondición.
- `tests/postman/circuit-breaker.postman_collection.json`: la secuencia CB-01 a CB-10 con `pm.test` y carpetas descritas.
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
- [ ] 18. Correr la secuencia completa contra el entorno local con el guion manual de apagado y encendido de reception. Verificación: CB-01 a CB-10 en verde en una sola pasada, con las habitaciones asignadas en el entorno.
- [ ] 19. Guardar la evidencia: exportar el run de Postman y una captura por transición (cerrado, abierto, half-open, cerrado) en `tests/evidence/qa-postman-circuit-breaker/`. Verificación: la carpeta contiene el run y las cuatro transiciones identificables, sin tokens visibles en las capturas.
- [x] 20. Ejecutar la verificación canónica. Verificación: `python scripts/validar-planes.py` con 0 errores y el árbol `tests/postman/` sin archivos fuera de las seis colecciones y el entorno.

## 5. Contratos afectados

- **OpenAPI:** ninguno. Se consumen rutas ya publicadas: login y `me` de [`identity.yaml`](../contracts/openapi/identity.yaml), catálogo y operaciones de [`hotel.yaml`](../contracts/openapi/hotel.yaml), recepción de [`reception.yaml`](../contracts/openapi/reception.yaml) y ventas de [`sales.yaml`](../contracts/openapi/sales.yaml). Los endpoints de gestión de `hotel-service` no forman parte del contrato del Gateway.
- **Eventos y comandos:** ninguno nuevo; las colecciones no publican ni consumen mensajes.
- **Persistencia:** ninguna. Las habitaciones del ciclo se eligen y se preparan fuera de la colección; si hace falta liberarlas, se hace con el procedimiento ya existente.
- **Errores:** aparecen `ROOM_OPERATION_IN_PROGRESS` (409) si se repite habitación y los códigos ya publicados de cada flujo; no se añade ninguno.

## 6. Pruebas

Evidencia en `tests/evidence/qa-postman-circuit-breaker/02-coleccion-circuit-breaker.txt` (validación estática de la colección; la ejecución en vivo de la secuencia no se ha corrido en esta sesión — véase nota al final).

**Comando de validación:** `python -c "import json; json.load(open('tests/postman/circuit-breaker.postman_collection.json'))"` + comprobaciones de estructura sobre el JSON parseado.

- Archivo: `tests/postman/circuit-breaker.postman_collection.json`, Postman v2.1.0, nombre `Circuit breaker — hotel-service (CLOSED → OPEN → HALF_OPEN → CLOSED)`.
- 10 carpetas `CB-01` … `CB-10` en orden; 26 peticiones en total (CB-04 agrupa las cinco llamadas con sus `buscar` previos; CB-02 incluye búsqueda, mantenimiento y liberación; CB-06 y CB-07 incluyen la lectura del actuator).
- `pm.test` en **todas** las 26 peticiones (estatus del contrato, cuerpo con `blockId`, headers `Location`/`Retry-After`, estado del breaker en el actuator, igualdad de `blockId` en `operaciones/{blockId}`).
- Peticiones del actuator con URL literal `http://localhost:8082/actuator/circuitbreakers` (puerto de gestión, nunca el Gateway): CB-03 (`CLOSED`), CB-05 (`OPEN`), CB-06.2 (sigue `OPEN`), CB-07.3 (`HALF_OPEN` tras sonda), CB-09 (`CLOSED`).
- Cabecera `Idempotency-Key` con UUID fijo y distinto por petición de mantenimiento; `If-Match` con `{{versionX}}` leído de `GET /api/habitacion/buscar/{id}` ejecutado justo antes.
- Sin secretos versionados: `grep -iE "bearer|password|secret|sk-|eyJ"` sobre la colección solo encuentra `Bearer {{token}}` (referencia a variable, valor vacío en el archivo) y menciones descriptivas; ningún token ni contraseña real.

**Descripciones obligatorias (carpeta raíz):** incluyen por qué cinco habitaciones distintas (índice `uq_operacion_vigente` de `V5__hotel_operaciones.sql`: repetir habitación devuelve `409 ROOM_OPERATION_IN_PROGRESS` antes de Feign y no cuenta como fallo del breaker), que la evidencia es la transición de estado en `/actuator/circuitbreakers` y no el cronómetro (reception detenido rechaza la conexión en milisegundos), los comandos literales `docker compose -f infra/compose.yaml --env-file .env stop reception-service` y `... start reception-service`, la alternativa `taskkill /PID <pid> /F` para reception en el IDE, y que Postman no puede apagar servicios (guion manual, la colección lo asume). La carpeta CB-07 cita la decisión de nivel 2 `docs/implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md` (Resilience4j 2.3.0: OPEN → HALF_OPEN no es reloj, se evalúa al llegar una llamada tras el intervalo; sin `automatic-transition-from-open-to-half-open-enabled`) y documenta la pausa manual de 10 s antes de la sonda y la aserción `HALF_OPEN` después.

**Inventario de peticiones (orden de ejecución):**
- CB-01: `POST {{baseUrl}}/api/auth/login` → guarda `{{token}}`.
- CB-02: `GET /api/habitacion/buscar/{{idHabitacionA}}` → `{{versionA}}`; `POST .../{{idHabitacionA}}/mantenimiento` → 200 con `blockId` (`{{blockIdCb02}}`); `PUT .../mantenimiento-completado` → libera.
- CB-03: `GET http://localhost:8082/actuator/circuitbreakers` → `CLOSED`.
- CB-04: cinco parejas (buscar + `POST .../mantenimiento`) sobre `{{idHabitacionB}}`…`{{idHabitacionF}}` → 202 con `Location` y `Retry-After`; `{{blockId}}` del `Location` de la primera.
- CB-05: `GET http://localhost:8082/actuator/circuitbreakers` → `OPEN` (prueba del ciclo).
- CB-06: buscar + `POST .../{{idHabitacionG}}/mantenimiento` → 202; actuator → sigue `OPEN`.
- CB-07: pausa manual 10 s; buscar + `POST .../{{idHabitacionA}}/mantenimiento` → 202 (sonda); actuator → `HALF_OPEN`.
- CB-08: buscar + `POST .../mantenimiento` sobre habitación libre ({{idHabitacionH} o altra libre) → 200 con `blockId` (repetir hasta tres veces; anotar cuántas hicieron falta).
- CB-09: `GET http://localhost:8082/actuator/circuitbreakers` → `CLOSED`.
- CB-10: `GET /api/habitacion/operaciones/{{blockId}}` → `APLICADA` con el mismo `blockId`.

**Validación canónica (paso 20):** `python scripts/validar-planes.py` → 0 errores, 0 avisos. Árbol `tests/postman/`: seis colecciones (`identidad`, `catalogo-hotelero`, `recepcion`, `ventas`, `reportes`, `circuit-breaker`) + `Hotel-local.postman_environment.json` + `.gitkeep`; sin archivos fuera de ellas.

**Ejecución en vivo (pasos 18-19):** no se ejecutó en esta sesión. No hay `hotel-service`, `api-gateway` ni `reception-service` levantados (solo contenedores de infra: postgres, kafka, rabbitmq, mailpit, kafka-ui), por lo que no es posible correr CB-01..CB-10 ni capturar el run ni las cuatro transiciones. `npx newman --version` → 6.2.3 (instalado), pero la corrida requiere el cluster local completo y el apagado/encendido manual de `reception-service` (`docker compose -f infra/compose.yaml --env-file .env stop reception-service` / `start reception-service`). No se inventan salidas; los pasos 18 y 19 quedan sin marcar hasta que la tanda de cierre ejecute el guion completo.

## 7. Preguntas abiertas

Ninguna. Las rutas, los estados y los códigos están publicados; el orden del guion manual se documenta en las `description`.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Las seis colecciones y el entorno importan y corren contra el entorno local completo.
- [ ] CB-01 a CB-10 en verde en una sola pasada, con evidencia en `tests/evidence/qa-postman-circuit-breaker/`.
- [ ] Ningún secreto ni token versionado en `tests/postman/`.
- [ ] Sin preguntas abiertas.
