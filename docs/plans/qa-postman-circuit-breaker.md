# qa-postman-circuit-breaker — colecciones Postman por flujo y ciclo del breaker con evidencia (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-postman-circuit-breaker` |
| Ámbito | `qa` |
| Componente | `tests` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `propuesto` |
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

- [ ] 1. Crear el entorno `Hotel-local` con `{{baseUrl}}` en `http://localhost:8080`, `{{token}}` vacío y `{{idHabitacion}}`, `{{idHabitacionA}}`…`{{idHabitacionG}}` vacíos. Verificación: el JSON no contiene ninguna cadena que parezca un token ni una contraseña (`grep -i "bearer\|password\|secret" tests/postman/Hotel-local.postman_environment.json` sin resultados).
- [ ] 2. Crear la colección `identidad` con login y `me`, y un `pm.test` en login que guarda el token en `{{token}}` desde el cuerpo de la respuesta. Verificación: correr login y `me` en cadena devuelve 200 en ambos y `{{token}}` queda poblado solo en memoria del entorno.
- [ ] 3. Crear la colección `catalogo-hotelero` (sedes, habitaciones, detalle) contra `{{baseUrl}}`. Verificación: las peticiones devuelven 200 y hay al menos una habitación cuyo id se anota para el ciclo.
- [ ] 4. Crear la colección `recepcion` (check-in, estadías). Verificación: el check-in sobre una habitación libre devuelve el código que fija el contrato y la estadía aparece en el listado.
- [ ] 5. Crear la colección `ventas` (carrito, alta, pago) con `Idempotency-Key` propia por petición. Verificación: alta y pago siguen el ciclo publicado (202 mientras la operación está en curso, terminal después) sin repetir efectos al reejecutar con la misma clave.
- [ ] 6. Crear la colección `reportes` contra las rutas de reportes del Gateway. Verificación: responde 200 con datos, o el código de «no listo» que fija el contrato si la proyección aún no está al día; en ambos casos la aserción pasa.
- [ ] 7. Describir en cada colección, en la `description` de su carpeta raíz, el flujo que cubre, su precondición y el orden de ejecución. Verificación: las cinco colecciones tienen descripción no vacía y ninguna depende de otra sin decirlo.
- [ ] 8. Crear la colección `circuit-breaker` con la carpeta raíz descrita: qué habitaciones usa, por qué son distintas, que la evidencia es la transición de estado y cómo se apaga y levanta reception. Verificación: la descripción incluye los comandos literales `docker compose -f infra/compose.yaml --env-file .env stop reception-service` y su opuesto `start`, y la alternativa `taskkill /PID <pid> /F` para cuando reception corre en el IDE.
- [ ] 9. CB-01: login y guardar `{{token}}`. Verificación: 200 y `{{token}}` poblado.
- [ ] 10. CB-02: `POST /api/habitacion/{id}/mantenimiento` sobre `{{idHabitacionA}}` con reception arriba. Verificación: 200 con `blockId`; `pm.test` comprueba el 200 y que el cuerpo trae `blockId`.
- [ ] 11. CB-03: `GET http://localhost:8082/actuator/circuitbreakers` y `pm.test` de estado CLOSED. Verificación: 200 y al menos un breaker en `CLOSED`; se aserta el id que reporta Feign.
- [ ] 12. CB-04: con reception detenido, cinco `POST /api/habitacion/{id}/mantenimiento` sobre `{{idHabitacionB}}`…`{{idHabitacionF}}`, uno por petición y con `Idempotency-Key` distinta. Verificación: las cinco devuelven 202 con `Location` y `Retry-After`; el script guarda en `{{blockId}}` el `blockId` del `Location` de la primera.
- [ ] 13. CB-05: `GET http://localhost:8082/actuator/circuitbreakers` y `pm.test` de estado OPEN. Verificación: el breaker está en `OPEN`; esta es la prueba del ciclo. Si sigue CLOSED, se falla la petición con el mensaje de que la tanda no alcanzó el mínimo de llamadas.
- [ ] 14. CB-06: otra llamada sobre `{{idHabitacionG}}` con el breaker abierto. Verificación: 202 con `Location` y `Retry-After` (no 503) y el estado sigue OPEN; `pm.test` comprueba ambas cosas.
- [ ] 15. CB-07: esperar 10 s y leer el actuator; `pm.test` de estado half-open. Verificación: 200 y estado half-open. La espera se documenta como pausa manual o `setTimeout` explícito del script, nunca como aserción de tiempo de respuesta.
- [ ] 16. CB-08: con reception levantado, hasta tres `POST /api/habitacion/{id}/mantenimiento` sobre habitaciones libres. Verificación: 200 con `blockId` en las que reception admite; se anota cuántas hicieron falta.
- [ ] 17. CB-09 y CB-10: leer el actuator (CLOSED) y `GET /api/habitacion/operaciones/{blockId}` con el `blockId` guardado en la variable. Verificación: estado `CLOSED` y la operación del primer `blockId` en `APLICADA` con el mismo `blockId`.
- [ ] 18. Correr la secuencia completa contra el entorno local con el guion manual de apagado y encendido de reception. Verificación: CB-01 a CB-10 en verde en una sola pasada, con las habitaciones asignadas en el entorno.
- [ ] 19. Guardar la evidencia: exportar el run de Postman y una captura por transición (cerrado, abierto, half-open, cerrado) en `tests/evidence/qa-postman-circuit-breaker/`. Verificación: la carpeta contiene el run y las cuatro transiciones identificables, sin tokens visibles en las capturas.
- [ ] 20. Ejecutar la verificación canónica. Verificación: `python scripts/validar-planes.py` con 0 errores y el árbol `tests/postman/` sin archivos fuera de las seis colecciones y el entorno.

## 5. Contratos afectados

- **OpenAPI:** ninguno. Se consumen rutas ya publicadas: login y `me` de [`identity.yaml`](../contracts/openapi/identity.yaml), catálogo y operaciones de [`hotel.yaml`](../contracts/openapi/hotel.yaml), recepción de [`reception.yaml`](../contracts/openapi/reception.yaml) y ventas de [`sales.yaml`](../contracts/openapi/sales.yaml). Los endpoints de gestión de `hotel-service` no forman parte del contrato del Gateway.
- **Eventos y comandos:** ninguno nuevo; las colecciones no publican ni consumen mensajes.
- **Persistencia:** ninguna. Las habitaciones del ciclo se eligen y se preparan fuera de la colección; si hace falta liberarlas, se hace con el procedimiento ya existente.
- **Errores:** aparecen `ROOM_OPERATION_IN_PROGRESS` (409) si se repite habitación y los códigos ya publicados de cada flujo; no se añade ninguno.

## 6. Pruebas

Evidencia en `tests/evidence/qa-postman-circuit-breaker/`: export del run y capturas de las cuatro transiciones.

- CB-01 login → 200, `{{token}}` poblado.
- CB-02 mantenimiento con reception arriba → 200 con `blockId`.
- CB-03 actuator → `CLOSED` con el id de Feign.
- CB-04 cinco mantenimientos sobre cinco habitaciones distintas → 202 con `Location` y `Retry-After`; `{{blockId}}` guardado.
- CB-05 actuator → `OPEN` (la prueba del ciclo).
- CB-06 sexta llamada → 202 y sigue `OPEN`.
- CB-07 a los 10 s → half-open.
- CB-08 con reception arriba, hasta tres correctas → 200.
- CB-09 actuator → `CLOSED`.
- CB-10 `GET /api/habitacion/operaciones/{blockId}` con el valor guardado → `APLICADA` con el mismo `blockId`.
- Precondición registrada en las descripciones: cinco habitaciones libres distintas por el índice `uq_operacion_vigente`; la evidencia es la transición de estado, no el cronómetro.
- Comandos canónicos: los de la sección «Verificación» del `README.md` raíz; `python scripts/validar-planes.py` para este PR.

## 7. Preguntas abiertas

Ninguna. Las rutas, los estados y los códigos están publicados; el orden del guion manual se documenta en las `description`.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Las seis colecciones y el entorno importan y corren contra el entorno local completo.
- [ ] CB-01 a CB-10 en verde en una sola pasada, con evidencia en `tests/evidence/qa-postman-circuit-breaker/`.
- [ ] Ningún secreto ni token versionado en `tests/postman/`.
- [ ] Sin preguntas abiertas.
