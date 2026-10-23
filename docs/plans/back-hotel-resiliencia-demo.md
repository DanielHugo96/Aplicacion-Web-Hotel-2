# back-hotel-resiliencia-demo — el circuit breaker de hotel-service se configura, se expone y se demuestra (F5)

| Campo | Valor |
|---|---|
| Plan | `back-hotel-resiliencia-demo` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-operaciones`, `back-plataforma-comunes` |
| Spec | [`configuracion.md` § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) y [§ Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio); [`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) y [§ Salud, readiness y resiliencia](../especificacion/03-contrato-comun.md#salud-readiness-y-resiliencia); [`hotel/api.md` § Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico); [`06` §7 Entregables](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación); [`errors.md`](../contracts/errors.md) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

`hotel-service` ejecuta su circuit breaker de Feign con los valores publicados (ventana 10, umbral 50 %, mínimo 5, abierto 10 000 ms, 3 en half-open) y un TimeLimiter de 6 000 ms, y publica su estado en el puerto de gestión para que se pueda ver el ciclo CLOSED → OPEN → HALF_OPEN → CLOSED. Se comprueba consultando el estado del breaker en 8082 antes, durante y después de cinco mantenimientos fallidos con reception detenido.

## 2. Alcance

**Entra:** la zona `services/hotel-service/`, componente `hotel-service`:

- `services/hotel-service/src/main/resources/application.yml`: mover la configuración de Resilience4j de claves de primer nivel a `resilience4j.circuitbreaker.configs.default.*`, añadir `resilience4j.timelimiter.configs.default.timeout-duration` con su variable, y ampliar `management.endpoints.web.exposure.include` con los dos endpoints de diagnóstico del breaker.
- `services/hotel-service/src/main/java/pe/hotel/hotel/platform/SecurityConfig.java`: permitir sin token las dos rutas de diagnóstico del breaker, junto a las de salud que ya están permitidas.
- `services/hotel-service/src/test/java/pe/hotel/hotel/**`: prueba de contexto o de binding que fije los seis valores y la exposición de los endpoints.
- Este plan y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/OperacionHabitacionService.java`: **no se toca**. El `202 PREPARANDO` con `Location` y `Retry-After` que devuelve hoy cuando la admisión no se confirma es el comportamiento correcto y se mantiene, también con el breaker abierto ([`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio)). El circuit breaker no introduce un `503` nuevo en esa ruta.
- Los otros seis servicios: no se configura circuit breaker ni se exponen sus endpoints. Solo `hotel-service` declara el breaker ([`configuracion.md` § Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio)).
- `docs/contracts/**`, `docs/especificacion/**`, `apps/**`, `tests/**`, `infra/**`, `.env.example` (su variable nueva es zona de `infra-kafka-ui`) y las versiones fijadas.
- Rutas, eventos, códigos de error, tablas y migraciones: no se añade ninguno. La demo usa las rutas y los códigos ya publicados.

## 3. Contexto

El PR #148 de la especificación enmienda la tabla de [§ Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios): las propiedades del breaker pasan a `resilience4j.circuitbreaker.configs.default.*` y aparece `resilience4j.timelimiter.configs.default.timeout-duration` con la variable nueva y default `6000`. La misma sección explica el motivo: en Resilience4j 2.3.0 las claves de primer nivel **se descartan en silencio** y el breaker corre con los defaults de la librería (ventana 100, mínimo 100 llamadas), con lo que nunca se abriría en una demo de cinco llamadas. El id del breaker de Feign se deriva de `Feign.configKey(...)` alfanumerizado, así que la configuración por instancia no es viable: se usa `configs.default`, que `findCircuitBreakerProperties(id)` aplica como respaldo y que `CircuitBreakerRegistry.getDefaultConfig()` consume.

**Los valores de negocio no cambian**: 10 llamadas de ventana, 50 % de umbral, mínimo 5 llamadas, 10 000 ms abierto y 3 llamadas de prueba en half-open.

**Por qué el TimeLimiter importa.** Spring Cloud OpenFeign envuelve cada llamada en un `TimeLimiter` cuando el breaker de Feign está activo. El default de Resilience4j es **1 s**, muy por debajo del `read-timeout` de Feign de la misma aplicación (valor inicial 5 000 ms en § Comunes). Sin `configs.default.timeout-duration`, cualquier llamada a reception que tarde entre 1 y 5 segundos se aborta como timeout del TimeLimiter, cuenta como fallo del breaker y el breaker se abre por llamadas que eran sanas. Con 6 000 ms el breaker deja que el propio cliente decida primero, y un fallo de red real sigue contando como fallo porque Feign agota su read-timeout antes.

La enmienda también fija ([§ Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio)) que el estado del breaker es diagnóstico interno y se consulta en el puerto de gestión del servicio que lo declara, **nunca por el Gateway**, y que en la demo lo declara solo `hotel-service` en 8082. El `SecurityConfig` actual solo libera las rutas de salud, así que hay que liberar también las dos rutas del breaker o el `curl` de la demo devolvería 401.

La restricción de las cinco habitaciones distintas es del esquema ya cerrado: `V5__hotel_operaciones.sql` crea el índice único parcial `uq_operacion_vigente` sobre `operacion_habitacion (id_habitacion)`, y `OperacionHabitacionService.iniciar` rechaza antes de llamar a Feign con `409 ROOM_OPERATION_IN_PROGRESS` si ya hay una operación vigente sobre esa habitación. Un segundo mantenimiento sobre la misma habitación no genera tráfico hacia reception y no cuenta para el breaker, así que la reproducción usa seis o más habitaciones libres, o limpia entre tandas con `sp_habitacion_habilitar` (también en `V5`).

Este plan no decide nada nuevo: aplica una configuración que la spec ya fija y la demuestra con las rutas publicadas.

## 4. Pasos

- [x] 1. Mover las cinco claves de `resilience4j.circuitbreaker.*` a `resilience4j.circuitbreaker.configs.default.*` sin cambiar ningún valor ni el nombre de sus variables de entorno. Verificación: `grep -n "resilience4j" -A 14 services/hotel-service/src/main/resources/application.yml` muestra las cinco claves bajo `configs.default` y ninguna clave de breaker en primer nivel.
- [x] 2. Añadir `resilience4j.timelimiter.configs.default.timeout-duration` con `${CB_TIMEOUT_MS:6000}`. Verificación: la misma salida del paso 1 muestra el bloque `timelimiter` con `configs.default.timeout-duration` y el default 6000.
- [x] 3. Ampliar `management.endpoints.web.exposure.include` de `health` a `health,circuitbreakers,circuitbreakerevents`. Verificación: `grep -n "include" -A 1 services/hotel-service/src/main/resources/application.yml` devuelve los tres nombres.
- [x] 4. Añadir al `SecurityConfig` las dos rutas de diagnóstico como `permitAll` justo después de las rutas de salud, sin tocar ninguna otra regla de autorización. Verificación: `grep -n "requestMatchers" -A 3 services/hotel-service/src/main/java/pe/hotel/hotel/platform/SecurityConfig.java` muestra las rutas del breaker como permitidas y las reglas del catálogo público, de las rutas internas y la denegación por defecto intactas.
- [x] 5. Añadir una prueba que fije el contrato de configuración: los cinco valores del breaker, el `timeout-duration` en 6000 y la exposición de los dos endpoints. Verificación: `bash scripts/mvn.sh -f services/pom.xml -pl hotel-service test` en verde.
- [x] 6. Arrancar el stack y comprobar la exposición: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/actuator/circuitbreakers`. Verificación: 200 y cuerpo JSON con `circuitBreakers` (antes del cambio era 404 y, sin el permiso, 401).
- [x] 7. Confirmar que el breaker nace CLOSED y anotar el id que reporta. Verificación: la misma salida trae `"state":"CLOSED"` y el id derivado de la clave de Feign de `ReceptionClient`; se registra el id literal en §6. No se renombra el breaker para «mejorar» la demo: el id lo fija Feign.
- [x] 8. Confirmar que existen al menos seis habitaciones libres para la reproducción. Verificación: `SELECT id_habitacion, numero FROM habitacion WHERE estado ORDER BY id_habitacion LIMIT 10;` devuelve al menos seis filas sin operación vigente.
- [x] 9. Con reception arriba, un mantenimiento correcto sobre una habitación libre. Verificación: 200 con `blockId` en la respuesta; es la llamada que deja el breaker funcionando antes de la caída.
- [x] 10. Detener reception y hacer cinco mantenimientos sobre **cinco habitaciones distintas**, con `Idempotency-Key` distinta en cada uno. Verificación: cinco respuestas 202 con `Location` y `Retry-After`; ninguna 409. Si alguna devuelve `409 ROOM_OPERATION_IN_PROGRESS`, se limpió mal la tanda: repetir con otra habitación.
- [x] 11. Leer el estado tras la quinta llamada fallida. Verificación: `curl -s http://localhost:8082/actuator/circuitbreakers` devuelve `"state":"OPEN"`, `failureRate` ≥ 50 y `bufferedCalls` 5.
- [x] 12. Hacer una sexta llamada sobre una habitación más. Verificación: 202 con `Location` y `Retry-After` (no 503), `notPermittedCalls` sube a 1 y `bufferedCalls` sigue en 5: la llamada de red no se intentó.
- [x] 13. Esperar 10 s y volver a leer. Verificación: `"state":"HALF_OPEN"` y `permittedCalls` con las llamadas de prueba en curso o completadas (el límite es 3). *Nota de la corrida: la lectura de solo lectura a los 10 s sigue en OPEN; la librería evalúa la transición en la primera llamada posterior, y ahí se lee HALF_OPEN. Queda registrado en §6.1 y en [`IA-back-hotel-resiliencia-demo-01`](../implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md).*
- [x] 14. Levantar reception y hacer hasta tres llamadas correctas sobre habitaciones libres. Verificación: 200 con `blockId` en las que admita reception; la siguiente lectura devuelve `"state":"CLOSED"` y `bufferedCalls` reiniciado.
- [x] 15. Comprobar que `OperacionHabitacionService` no cambió y que el 202 sigue igual. Verificación: `git diff --stat main -- services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/` no muestra cambios, y las respuestas de los pasos 10 y 12 conservan código 202, `Location` y `Retry-After`.
- [x] 16. Dejar en §6 la evidencia literal del ciclo: los seis `curl` (estado inicial, tras la quinta, tras la sexta, half-open, tras las correctas) con sus códigos HTTP y el id del breaker. Verificación: §6 contiene las salidas pegadas y no solo la descripción del resultado.
- [x] 17. Ejecutar la verificación canónica del backend. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS` y `python scripts/validar-planes.py` sale con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. No se añaden rutas: los mantenimientos usan `POST /api/habitacion/{id}/mantenimiento`, la consulta de la operación usa `GET /api/habitacion/operaciones/{blockId}` y los estados 200/202/409 ya están en [`hotel/api.md` § Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico). Los endpoints de diagnóstico son de gestión, no forman parte de los contratos de negocio ni del Gateway.
- **Eventos y comandos:** ninguno.
- **Persistencia:** ninguna. Se usan `operacion_habitacion` y el índice parcial `uq_operacion_vigente` de `V5__hotel_operaciones.sql`, ya aplicados; `sp_habitacion_habilitar` se usa solo para preparar datos de prueba.
- **Errores:** `ROOM_OPERATION_IN_PROGRESS` (409) puede aparecer si la tanda repite habitación; no se añade ni cambia ningún código.

## 6. Pruebas

Evidencia en esta sección del plan: la ejecutora pega las salidas literales de los `curl` de los pasos 6 a 14 con fecha de la corrida.

- Exposición: `curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/actuator/circuitbreakers` → 200. Antes del cambio: 404 (endpoint no expuesto) o 401 (sin permiso).
- Nacimiento: `curl -s http://localhost:8082/actuator/circuitbreakers` → `"state":"CLOSED"` y el id de Feign de reception.
- Apertura: tras cinco 202 seguidos con reception detenido, `"state":"OPEN"` con `bufferedCalls: 5`.
- Corte sin red: la sexta llamada → 202 con `Location` y `Retry-After`, `notPermittedCalls` incrementado y `bufferedCalls` sin crecer.
- Half-open: a los 10 s, `"state":"HALF_OPEN"`.
- Cierre: con reception arriba y hasta tres llamadas correctas, `"state":"CLOSED"`.
- Comandos canónicos: los de la sección «Verificación» del `README.md` raíz (`bash scripts/mvn.sh -f services/pom.xml verify`) y `python scripts/validar-planes.py`.

### 6.1 Corrida literal — 2026-10-23 17:20:08 -0500

Stack: `discovery-server` (8761), `identity-service` (8081), `hotel-service` (8082) y `reception-service`
(8083) levantados con `spring-boot:run` vía `scripts/mvn.sh`; PostgreSQL, Kafka y RabbitMQ de
`infra/compose.yaml` ya arriba. Las llamadas al mantenimiento se hacen **contra `hotel-service`
directamente en 8082**, no por el Gateway, porque el diagnóstico del breaker vive en el puerto de
gestión del propio servicio.

Dos preparaciones, dichas explícitamente para que la evidencia no engañe:

1. `hotel-service` se arranca con `OPERACION_RECOVERY_INTERVAL_MS=600000` (valor de entorno de la
   corrida; el default del servicio, 15000 ms, no cambia en el código). El job RB3 reintenta las
   operaciones PREPARANDO cada 15 s y ese tráfico extra movería los contadores del breaker en mitad
   de la demo.
2. Entre el paso 9 y el paso 10 se reinicia `hotel-service`. La llamada sana del paso 9 demuestra que
   el breaker funciona con reception arriba, pero deja 1 llamada en la ventana de 10; reiniciar deja
   la ventana vacía para que los cinco fallos del paso 10 se cuenten desde cero, como fijan los
   valores publicados (mínimo 5). Es preparación de la corrida, no una vuelta de tuerca a los valores.

**Paso 6 — exposición** (antes del cambio el endpoint no existía para el cliente: 404/401):

```text
$ curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/actuator/circuitbreakers
200
$ curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/actuator/circuitbreakerevents
200
$ curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/actuator/health/liveness
200
$ curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/api/hotel/listar    # sin token
401
```

**Paso 7 — id del breaker y nacimiento CLOSED.** El id lo fija Feign
(`Feign.configKey(...)` alfanumerizado) y es `ReceptionClientbloquearintBloqueoRequest`. Con el
registro recién arrancado está vacío porque el breaker se crea con la primera llamada:

```text
### PASO 7 · el breaker nace CLOSED (aún sin llamadas: el id aparece con la primera)
{
  "circuitBreakers": {}
}
```

**Paso 8 — habitaciones libres:**

```text
id_habitacion | numero
          101 | 101
          102 | 102
          201 | 101
          202 | 102
          301 | 101
          302 | 102
```

**Paso 9 — mantenimiento correcto con reception arriba:**

```text
POST /api/habitacion/101/mantenimiento -> HTTP 200 {'etag': '"12"'}
  blockId=d0de67ad-cfde-48be-83ca-ead46b1468de version=12 estadoFisico=MANTENIMIENTO
PUT /api/habitacion/101/mantenimiento-completado -> HTTP 200 (libera la habitación)
estado tras la llamada sana:
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 1,
      "failedCalls": 0,
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0,
      "slowCallRate": "-1.0%",
      "slowCallRateThreshold": "100.0%",
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "state": "CLOSED"
    }
  }
}
```

**Paso 10 — reception detenido y cinco mantenimientos sobre cinco habitaciones distintas**
(`102`, `201`, `202`, `301`, `302`; `Idempotency-Key` distinta en cada uno). Ninguna respuesta es
`409`:

```text
reception-service detenido (puerto 8083 abierto = False)

POST /api/habitacion/102/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/9b2cf0c0-f948-4868-b66d-c8ea11c97add', 'retry-after': '2'}
POST /api/habitacion/201/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/fb67c23c-64da-45f5-8e19-184f2439fcd5', 'retry-after': '2'}
POST /api/habitacion/202/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/f1f05e31-cd1c-4df1-ac61-b883edd91b88', 'retry-after': '2'}
POST /api/habitacion/301/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/9e245e41-3e18-4723-a450-b64c7a01fdf3', 'retry-after': '2'}
POST /api/habitacion/302/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/0602329c-a728-487e-a1cf-df87df6579bb', 'retry-after': '2'}
```

**Paso 11 — tras la quinta llamada fallida, el breaker está abierto.** `bufferedCalls` 5,
`failedCalls` 5, `failureRate` 100 % (umbral 50 %):

```text
### PASO 11 · estado tras la quinta llamada fallida
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 5,
      "failedCalls": 5,
      "failureRate": "100.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0,
      "slowCallRate": "0.0%",
      "slowCallRateThreshold": "100.0%",
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "state": "OPEN"
    }
  }
}
```

**Paso 12 — sexta llamada sobre otra habitación (101): 202, no 503, y sin red.** `notPermittedCalls`
sube a 1 y `bufferedCalls` se queda en 5: la llamada de red no se intentó.

```text
### PASO 12 · sexta llamada sobre otra habitación (101)
POST /api/habitacion/101/mantenimiento -> HTTP 202 {'location': '/api/habitacion/operaciones/2c4cd303-40ca-496f-8aad-96cc32b9d808', 'retry-after': '2'}
estado tras la sexta llamada:
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 5,
      "failedCalls": 5,
      "failureRate": "100.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 1,
      "slowCallRate": "0.0%",
      "slowCallRateThreshold": "100.0%",
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "state": "OPEN"
    }
  }
}
```

**Paso 13 — a los 10 s.** Aquí hay que ser exacto: la **lectura de solo lectura** a los 10 s sigue
devolviendo OPEN porque en Resilience4j 2.3.0 el paso a HALF_OPEN no es un reloj que cambie el
estado consultado (`OpenState.getState()` devuelve siempre OPEN), sino que se evalúa en
`OpenState.tryAcquirePermission()` **cuando llega una llamada** y el intervalo ya venció. No se
activó `automatic-transition-from-open-to-half-open-enabled`: la spec no lo fija y añadirlo sería
inventar configuración (decisión en
[`IA-back-hotel-resiliencia-demo-01`](../implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md)).
La transición se ve, literalmente, en la primera lectura posterior a una llamada:

```text
### PASO 13 · esperar 10 s y volver a leer
estado a los 10 s (esperados 10.5 s):
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 5,
      "failedCalls": 5,
      "failureRate": "100.0%",
      ...
      "notPermittedCalls": 1,
      ...
      "state": "OPEN"
    }
  }
}
```

**Paso 14 — levantar reception y hasta tres llamadas correctas.** La primera llamada consumida
después de los 10 s ya muestra HALF_OPEN; la tercera completa la prueba y devuelve el breaker a
CLOSED con `bufferedCalls` reiniciado a 0. `permittedCalls` en half-open es 3 (el valor publicado)
y se ve en `bufferedCalls` 1 → 2 → cierre:

```text
reception-service arriba (puerto 8083 abierto = True)

POST /api/habitacion/102/mantenimiento -> HTTP 200 {'etag': '"4"'}
  blockId=48d6b1fa-a677-44ef-94a3-afc4eea8ffbc version=4
  estado del breaker:
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 1,
      "failedCalls": 0,
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0,
      ...
      "state": "HALF_OPEN"
    }
  }
}

POST /api/habitacion/201/mantenimiento -> HTTP 200 {'etag': '"4"'}
  blockId=d77ddc00-dc4f-4650-90ca-3647b8cd5be8 version=4
  estado del breaker:
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 2,
      "failedCalls": 0,
      ...
      "state": "HALF_OPEN"
    }
  }
}

POST /api/habitacion/202/mantenimiento -> HTTP 200 {'etag': '"4"'}
  blockId=998dbda8-a96f-4ddd-ba90-da21c0c42445 version=4
  estado del breaker:
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 0,
      "failedCalls": 0,
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0,
      ...
      "state": "CLOSED"
    }
  }
}
```

**Cierre — estado final:**

```text
{
  "circuitBreakers": {
    "ReceptionClientbloquearintBloqueoRequest": {
      "bufferedCalls": 0,
      "failedCalls": 0,
      "failureRate": "-1.0%",
      "failureRateThreshold": "50.0%",
      "notPermittedCalls": 0,
      "slowCallRate": "-1.0%",
      "slowCallRateThreshold": "100.0%",
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "state": "CLOSED"
    }
  }
}
```

**Paso 15 — el 202 sigue igual.** `git diff --stat main -- services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/`
no devuelve nada, y las respuestas de los pasos 10 y 12 conservan `202`, `Location` y `Retry-After`
(arriba). El circuit breaker no introduce un 503 en esa ruta.

**Comandos canónicos:** `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS` en los
nueve módulos; `python scripts/validar-planes.py` → `42 plan(es) revisado(s); … 0 error(es), 0 aviso(s)`.

## 7. Preguntas abiertas

Ninguna. La spec fija la ubicación de la configuración, los valores, la variable nueva y el puerto de diagnóstico; el comportamiento del 202 ya está publicado y este plan lo declara invariante.

Ninguna (resuelto por IA: el `curl` de solo lectura a los 10 s sigue devolviendo OPEN porque Resilience4j 2.3.0 evalúa la transición a HALF_OPEN en la primera llamada posterior al intervalo y no por reloj; se lee HALF_OPEN en esa llamada y **no** se activa el planificador automático, que la spec no fija. Detalle en [`IA-back-hotel-resiliencia-demo-01`](../implementacion/decisiones/IA-back-hotel-resiliencia-demo-01.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Las salidas literales del ciclo CLOSED → OPEN → HALF_OPEN → CLOSED están en §6.
- [x] `bash scripts/mvn.sh -f services/pom.xml verify` en verde y `python scripts/validar-planes.py` con 0 errores.
- [x] `OperacionHabitacionService` y los otros seis servicios sin cambios.
- [x] Sin preguntas abiertas.
