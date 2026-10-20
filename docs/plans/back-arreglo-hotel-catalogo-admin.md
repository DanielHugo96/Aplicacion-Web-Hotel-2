# back-arreglo-hotel-catalogo-admin — alcance por sede, paginación, estado, galería y Eureka (F2)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-hotel-catalogo-admin` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-catalogo` |
| Repara | `back-hotel-catalogo` |
| Spec | [`hotel/api.md` § Sedes, pisos y categorías](../especificacion/microservicios/hotel/api.md#sedes-pisos-y-categorías), [§ Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico), [§ DTO](../especificacion/microservicios/hotel/api.md#dto), [`hotel/base-de-datos.md` § Tablas](../especificacion/microservicios/hotel/base-de-datos.md#tablas), [`03-contrato-comun.md` § Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§ Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [`configuracion.md` § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), [`hotel.yaml`](../contracts/openapi/hotel.yaml), [`admin-web.yaml`](../contracts/openapi/admin-web.yaml) y [`errors.md` § Genéricos](../contracts/errors.md) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Con `hotel-service` y el Gateway arriba, un token EMPLEADO de la sede 1 ve su sede en
`GET /api/hotel/listar` y puede listar y buscar sus pisos y habitaciones; los cuatro listados
devuelven `meta` con `page`, `size` y `totalElements`; `PUT /api/estadohabitacion/actualizar/{id}`
funciona con el `requestBody` del contrato; `GET /api/habitacion/buscar/{id}` devuelve
`urlsImagenes`; y `hotel-service` se registra solo en Eureka, de modo que el Gateway lo enruta sin
registro manual.

Se comprueba repitiendo las reproducciones de §3 y dejando sus comandos en verde.

## 2. Alcance

**Entra:** la zona del plan reparado `back-hotel-catalogo`, componente `hotel-service`:

- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/security/CurrentUser.java`:
  convertir a `Integer` los elementos numéricos del claim `sedes`, sin descartar los `Long` que
  entrega el decoder.
- `services/hotel-service/src/main/java/pe/hotel/hotel/web/HotelController.java`,
  `PisoController.java`, `CategoriaController.java` y `HabitacionController.java`: aceptar `page` y
  `size` y devolver `Meta` de página en los cuatro listados.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/HotelService.java`,
  `PisoService.java`, `CategoriaService.java` y `HabitacionService.java`: listar con página estable
  por id y total real del filtro, sin cambiar las reglas de alcance ni de estado.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/repository/HotelRepository.java`,
  `PisoRepository.java`, `CategoriaRepository.java` y `HabitacionRepository.java`: consultas con
  `Pageable`/`Page` para esos listados; conservar las consultas existentes que usan otros flujos.
- `services/hotel-service/src/main/java/pe/hotel/hotel/web/EstadoHabitacionController.java` y, si
  hace falta, un DTO de entrada nuevo: leer `descripcion` del `requestBody` JSON del contrato y no
  de un query param.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/dto/HabitacionView.java` y
  `HabitacionService.java`: añadir `urlsImagenes` y poblarlo desde `imagen_habitacion` ordenado por
  `orden`, con un DTO de salida de imagen.
- `services/hotel-service/pom.xml`: añadir `spring-cloud-starter-netflix-eureka-client` sin
  versión (la fija `services/pom.xml`).
- `services/hotel-service/src/main/resources/application.yml`: añadir el bloque `eureka` con la misma
  forma que `identity-service`.
- `services/hotel-service/src/test/java/pe/hotel/hotel/`: pruebas del claim `sedes` numérico, de la
  paginación, del `requestBody` de estado y de la galería.
- `docs/plans/back-arreglo-hotel-catalogo-admin.md` y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- `docs/contracts/**` y `docs/especificacion/**`: no se modifican. Se implementan `Page`, `Size`,
  `ApiMetaPage`, el `requestBody` de `PUT /api/estadohabitacion/actualizar/{id}` y
  `Habitacion.urlsImagenes` ya publicados.
- `infra/**`, `services/api-gateway/**`, `services/discovery-server/**`, `.env` y `.env.example`: no
  se tocan. El servidor Eureka, el Gateway y las credenciales locales siguen como están.
- `services/pom.xml` y las versiones fijadas (`apps/*/package.json`, el `README.md` raíz): la
  dependencia nueva no lleva versión.
- `identity-service` y su `ClassCastException` de `sedes`: se corrige en
  `back-arreglo-identity-personas-catalogo`.
- Migraciones, tablas, columnas, seeds y eventos: la galería se lee de `imagen_habitacion`, que ya
  existe, y no se toca `hotel.sede.v1` ni `hotel.habitacion.v1`.
- Reglas de negocio de mantenimiento, limpieza, ocupación y baja con `blockId`
  (`back-hotel-operaciones`), y cualquier cambio de seguridad, audiencia o scopes.

## 3. Contexto

El defecto lo introdujo `back-hotel-catalogo`, plan ya `cerrado`, en el PR #66 (commit `a2a1f3c`,
«feat(hotel): implementa el plan back-hotel-catalogo con 19 endpoints y seeds»), y persistió tras
sus arreglos #76 (`back-arreglo-hotel-arranque`) y #79 (`back-arreglo-audiencia-maquina`). Su zona
es `services/hotel-service/**`, exactamente la zona de este arreglo. Lo detectó la ejecución de
`front-admin-web-catalogo` al cerrar sus pantallas contra el Gateway real; su §9 y la evidencia
`apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md` viven en la rama
`front-admin-web-catalogo`, no en `main`, y se citan aquí sin enlace.

Las reproducciones se ejecutaron desde `main` el 2026-10-19, con `hotel-service` en `8082`, el
Gateway en `8080` y los JWT humanos de `admin@hotel.test` (ADMIN) y `recepcion.lima@hotel.test`
(EMPLEADO, `sedes: [1]`).

**Reproducción 1 — el EMPLEADO no ve su sede.**

```text
$ curl -s -i "http://localhost:8080/api/hotel/listar?page=0&size=100" \
    -H "Authorization: Bearer <EMPLEADO>"
HTTP/1.1 200 OK
{"success":true,"message":"OK","data":[],"meta":{"correlationId":"5aed9c23-e3f5-4723-a2b7-9aebfc2e28b3"}}
```

Con ADMIN el mismo listado devuelve las cinco sedes. El JWT del EMPLEADO sí lleva `sedes: [1]`.
`CurrentUser.sedes()` filtra `Integer.class::isInstance`, pero el decoder entrega `Long`; el
resultado es una lista vacía y, con ella, el alcance vacío. El mismo origen explica los 404 de
`GET /api/piso/listar?idHotel=1`, `GET /api/habitacion/listar?idHotel=1` y
`GET /api/hotel/buscar/{id}` con id 1, aunque la sede 1 esté asignada:

```text
$ curl -s -w '\nHTTP %{http_code}\n' "http://localhost:8080/api/piso/listar?idHotel=1&page=0&size=20" \
    -H "Authorization: Bearer <EMPLEADO>"
{"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"10f10f96-de0f-46b2-a09a-3d485400c461"}}
HTTP 404
```

**Reproducción 2 — los listados no devuelven la paginación del contrato.**

```text
$ curl -s "http://localhost:8080/api/hotel/listar?page=0&size=2" -H "Authorization: Bearer <ADMIN>"
{"success":true,"message":"OK","data":[...5 sedes...],"meta":{"correlationId":"9a0016cd-d6e4-48df-821c-0e7617ab0571"}}
```

`meta` solo trae `correlationId`: no incluye `page`, `size` ni `totalElements`, que
[`ApiMetaPage`](../contracts/openapi/hotel.yaml) declara obligatorios, y el `size=2` se ignora
porque los controladores ni siquiera declaran `page`/`size`. Ocurre igual en `/api/piso/listar`,
`/api/categoria/listar` y `/api/habitacion/listar`.

**Reproducción 3 — el PUT de estado físico ignora el cuerpo.**

```text
$ curl -s -i -X PUT "http://localhost:8080/api/estadohabitacion/actualizar/1" \
    -H "Authorization: Bearer <ADMIN>" -H 'Content-Type: application/json' \
    -H 'If-Match: "1"' -H "Idempotency-Key: <uuid>" -d '{"descripcion":"Lista"}'
HTTP/1.1 500 Internal Server Error
{"success":false,"message":"Ocurrió un error inesperado.","error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"c2623659-4644-48b0-866e-b355a586e872"}}
```

Sin query param, Spring no encuentra `descripcion` y responde 500. Con
`?descripcion=Lista` y el mismo cuerpo responde 200, lo que confirma que el `requestBody` no se
lee. El contrato declara el cuerpo `{"descripcion": ...}` como `required` y no declara ese query
param.

**Reproducción 4 — la búsqueda de habitación no devuelve la galería.**

```text
$ curl -s "http://localhost:8080/api/habitacion/buscar/101" -H "Authorization: Bearer <ADMIN>"
{"success":true,"message":"OK","data":{"idHabitacion":101,...,"ocupacion":{...},"blockId":null,"endpoint":null,"version":1},"meta":{"correlationId":"e316c80f-db3c-440a-81f8-044db96618eb"}}
```

No aparece `urlsImagenes`, aunque `imagen_habitacion` tiene la fila
`101 | /assets/demo/hab-101-1.jpg | Habitacion de demostracion | 1` y el schema `Habitacion` de
[`hotel.yaml`](../contracts/openapi/hotel.yaml) y de [`admin-web.yaml`](../contracts/openapi/admin-web.yaml)
la declara. El formulario de edición de `admin-web` parte de una galería vacía y no puede conservar
las imágenes existentes.

**Reproducción 5 — `hotel-service` no se registra en Eureka por sí solo.**

```text
$ grep -c "eureka" services/hotel-service/pom.xml
0
$ grep -c "eureka" services/hotel-service/src/main/resources/application.yml
0
$ grep -c "eureka" services/identity-service/pom.xml
1
$ grep -c "eureka" services/identity-service/src/main/resources/application.yml
3
```

El módulo no tiene el cliente de Eureka ni el bloque `eureka`; el resto de servicios de dominio sí.
Por eso el Gateway, que enruta con `lb://hotel-service`, no resuelve la instancia y responde 503
`DEPENDENCY_UNAVAILABLE` en un arranque limpio. Para la corrida de `front-admin-web-catalogo` se
registró la instancia a mano; la prueba es el `hostName` local:

```text
$ curl -s -u "<EUREKA_USERNAME>:<EUREKA_PASSWORD>" -H 'Accept: application/json' \
    http://localhost:8761/eureka/apps/HOTEL-SERVICE
{"application":{"name":"HOTEL-SERVICE","instance":[{"instanceId":"localhost:hotel-service:8082","hostName":"localhost","ipAddr":"127.0.0.1",...}]}}

$ # identity, con cliente y prefer-ip-address
localhost:identity-service:8081 | 192.168.56.1 | 192.168.56.1
```

**Delimitación código/entorno.** La ausencia del cliente y de su configuración es un **defecto de
código** de `hotel-service`: ninguna variable de entorno puede poner una dependencia en el
classpath. Se corrige en este plan, dentro de `services/hotel-service/**`. El **401 de Eureka** es
otra cosa: [`IA-back-identity-auth-09.md`](../implementacion/decisiones/IA-back-identity-auth-09.md)
registró que los clientes de Spring Cloud de esta versión toman las credenciales del userinfo de
EUREKA_URL, no de `eureka.client.username/password`, y que la corrida local usa
EUREKA_URL=http://&lt;usuario&gt;:&lt;clave&gt;@localhost:8761/eureka en el `.env` ignorado por Git. Eso es
**entorno local**, ya resuelto, y este plan no lo cambia ni toca `.env` ni `infra/**`: la
verificación se ejecuta con esa forma de EUREKA_URL.

**Qué fija la spec y el contrato.** [`hotel/api.md` § Sedes, pisos y categorías](../especificacion/microservicios/hotel/api.md#sedes-pisos-y-categorías)
y [`03-contrato-comun.md` § Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes)
fijan que el EMPLEADO solo ve sus sedes del JWT. [`03-contrato-comun.md` § Respuestas](../especificacion/03-contrato-comun.md#respuestas)
y [`hotel.yaml`](../contracts/openapi/hotel.yaml) exigen `page`, `size` y `totalElements` en los
listados, con `page` 0-based y `size` máximo 100. El cuerpo de estado y `Habitacion.urlsImagenes`
están publicados en el mismo OpenAPI. No hay regla nueva que decidir.

## 4. Pasos

- [x] 1. Reproducir los cinco fallos desde `main` con el Gateway y los tokens de §3. Verificación: se
  observan los `data: []`, los `meta` incompletos, el 500 del estado, la ausencia de
  `urlsImagenes`, el `grep` sin `eureka` y la instancia manual con `hostName: localhost`.
- [x] 2. Corregir `CurrentUser.sedes()`: recorrer la lista del claim y convertir cada elemento
  `Number` con `intValue()`, conservando el orden y devolviendo lista vacía si el claim no es una
  lista. No cambiar `isAdmin`, `isEmpleado`, `puedeAccederASede` ni el origen de la identidad (JWT
  validado). Verificación: un EMPLEADO de la sede 1 deja de obtener lista vacía y `puedeAccederASede(1)`
  devuelve `true` en la prueba; `puedeAccederASede(2)` sigue en `false`.
- [x] 3. Añadir paginación a los cuatro listados: `page` con default 0 y mínimo 0, `size` con default
  20 y rango 1..100, orden estable por id ascendente y `totalElements` del filtro aplicado. Usar
  `Page`/`Pageable` en los repositorios y devolver
  `Meta.of(CorrelationId.current(), page, size, totalElements)` en `HotelController`,
  `PisoController`, `CategoriaController` y `HabitacionController`. No cambiar los filtros
  existentes (`estado`, `idHotel`, `idCategoria`) ni las reglas de sede. Validar el rango con una
  comprobación explícita que lance `ApiException(ErrorCode.VALIDATION_ERROR)` con `fields.page` o
  `fields.size` (o manejar `ConstraintViolationException` en `GlobalExceptionHandler`), para que un
  valor fuera de rango no termine en 500. Verificación: cada listado
  devuelve `meta.page`, `meta.size` y `meta.totalElements`; `page=-1`, `size=0` y `size=101`
  responden 400 `VALIDATION_ERROR`.
- [x] 4. Corregir `PUT /api/estadohabitacion/actualizar/{id}`: retirar `@RequestParam String
  descripcion`, declarar un `@RequestBody` con el campo `descripcion` del contrato y pasarlo al
  `EstadoHabitacionService` existente. Mantener `If-Match`, `Idempotency-Key`, 428 y 412.
  Verificación: con el cuerpo `{"descripcion":"Lista"}` y sin query param responde 200; sin
  `If-Match` sigue en 428; con versión vieja, 412.
- [x] 5. Añadir `urlsImagenes` a `HabitacionView` y poblarlo en `HabitacionService.toView` desde
  `ImagenHabitacionRepository.findByHabitacionIdOrderByOrdenAsc`, con `url`, `alt` y `orden`. Se
  aplica a `buscar`, `listar`, `registrar` y `actualizar`; si el listado lo necesita, resolver las
  imágenes por lote para no multiplicar consultas. Verificación: `GET /api/habitacion/buscar/{id}`
  con id 101 devuelve `urlsImagenes` con `/assets/demo/hab-101-1.jpg`, `alt` y `orden: 1`; una
  habitación sin imágenes devuelve `[]`, nunca `null`.
- [x] 6. Añadir a `services/hotel-service/pom.xml` la dependencia
  `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` sin `<version>` y, en
  `application.yml`, el bloque `eureka` de `identity-service`: `client.enabled: true`,
  `register-with-eureka: true`, `fetch-registry: true`,
  `service-url.defaultZone` con el valor por defecto `http://localhost:8761/eureka` tomado de la
  variable de entorno EUREKA_URL,
  `username: ${EUREKA_USERNAME:}`, `password: ${EUREKA_PASSWORD:}` e
  `instance.prefer-ip-address: true`. Verificación: los dos `grep -c "eureka"` de §3 dejan de ser 0,
  la dependencia resuelve sin versión explícita y el `application.yml` conserva el puerto 8082 y el
  resto de la configuración.
- [x] 7. Añadir pruebas en `services/hotel-service/src/test/java/pe/hotel/hotel/`: `CurrentUser` con
  claim `sedes` de `Long`; listados con `page`/`size` y `meta` completo; PUT de estado con cuerpo y
  sin query; y `buscar` con `urlsImagenes` ordenadas. Verificación: pasan con
  `bash scripts/mvn.sh -f services/pom.xml -pl hotel-service test`.
- [x] 8. Rearrancar Eureka/Gateway y `hotel-service` desde la rama corregida, con el `.env` local que
  lleva el userinfo en la variable EUREKA_URL (decisión `IA-back-identity-auth-09`), sin registrar la
  instancia a mano. Verificación: `GET /eureka/apps/HOTEL-SERVICE` muestra una sola instancia nueva
  con `hostName` igual a la IP del host (no `localhost`) e `ipAddr` distinta de `127.0.0.1`; el log
  de `hotel-service` no repite errores de registro.
- [x] 9. Repetir la reproducción 1 con el token EMPLEADO: `GET /api/hotel/listar?page=0&size=100`,
  `GET /api/hotel/buscar/{id}` con id 1, `GET /api/piso/listar?idHotel=1&page=0&size=20` y
  `GET /api/habitacion/listar?idHotel=1&page=0&size=20`. Verificación: 200 con la sede 1 y sus
  pisos/habitaciones; una sede no asignada (por ejemplo `idHotel=2`) sigue devolviendo 404
  `RESOURCE_NOT_FOUND`.
- [x] 10. Repetir la reproducción 2 con ADMIN: los cuatro listados con `page=0&size=2`.
  Verificación: `data` trae como máximo dos filas, `meta.page=0`, `meta.size=2` y
  `meta.totalElements` es el total del filtro; `page=-1`, `size=0` y `size=101` responden 400.
- [x] 11. Repetir las reproducciones 3 y 4: `PUT /api/estadohabitacion/actualizar/{id}` con id 1
  solo con cuerpo → 200 con `version` incrementada; `GET /api/habitacion/buscar/{id}` con id 101 →
  200 con `urlsImagenes`.
  Verificación: cuerpos y `ETag` guardados; sin `If-Match` el PUT sigue en 428.
- [x] 12. Comprobar el enrutado limpio por el Gateway sin registro manual: reiniciar `api-gateway`
  (o esperar el refresco del registro) y ejecutar `GET /api/hotel/listar` con ADMIN. Verificación:
  200, sin 503 `DEPENDENCY_UNAVAILABLE`, y la instancia de Eureka es la que publicó el propio
  servicio.
- [x] 13. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml verify`,
  `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`.
  Verificación: `BUILD SUCCESS` de los nueve módulos, 0 errores del validador y «Los contratos solo
  se han ampliado. Correcto.».
- [x] 14. Guardar en *Pruebas* las salidas de los pasos 1, 7, 8, 9, 10, 11, 12 y 13, marcar todos
  los pasos y cambiar `Estado` a `cerrado` en el último commit. Verificación: no quedan pasos sin
  marcar y las cinco reproducciones originales están en verde.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/hotel.yaml` ni
  `docs/contracts/openapi/admin-web.yaml`. Se implementan `Page`/`Size` y `ApiMetaPage` en
  `GET /api/hotel/listar`, `GET /api/piso/listar`, `GET /api/categoria/listar` y
  `GET /api/habitacion/listar`; el `requestBody` de `PUT /api/estadohabitacion/actualizar/{id}`; y
  `Habitacion.urlsImagenes` en `GET /api/habitacion/buscar/{id}` y en las respuestas de habitación.
  No se añade, cambia ni elimina ninguna ruta, parámetro, schema, `operationId` ni código.
- **Eventos y comandos:** ninguno. No se tocan `hotel.sede.v1`, `hotel.habitacion.v1`, el outbox ni
  `docs/contracts/events/`. La corrección de Eureka solo afecta al transporte del Gateway.
- **Persistencia:** ninguna migración ni cambio de tabla. `imagen_habitacion` ya existe y se lee con
  su orden; no se añaden columnas ni índices. Las consultas paginadas reutilizan las tablas y los
  seeds actuales.
- **Errores:** se usan los códigos ya publicados en [`errors.md`](../contracts/errors.md):
  `VALIDATION_ERROR` (400), `RESOURCE_NOT_FOUND` (404), `PRECONDITION_REQUIRED` (428) y
  `PRECONDITION_FAILED` (412). No se añade ningún código.
- **Configuración:** solo `services/hotel-service/pom.xml` (dependencia sin versión) y
  `services/hotel-service/src/main/resources/application.yml` (bloque `eureka`). No se tocan
  `services/pom.xml`, `infra/**`, `.env`, `.env.example`, el Gateway ni el servidor Eureka.
- **Seguridad:** no se cambia el validador JWT, la audiencia, el issuer ni los scopes. El claim
  `sedes` sigue saliendo del token validado; no se aceptan cabeceras de identidad.

## 6. Pruebas

### Reproducción antes del arreglo (paso 1)

Ejecutado el 2026-10-20 sobre `main` (`e1b5b96`) con `hotel-service` en `8082`, el Gateway en
`8080`, Eureka en `8761` y los JWT humanos de `admin@hotel.test` (ADMIN) y
`recepcion.lima@hotel.test` (EMPLEADO, `sedes: [1]`). Salida literal (correlationIds incluidos):

```text
### GET /api/hotel/listar?page=0&size=100 (EMPLEADO)
{"success":true,"message":"OK","data":[],"meta":{"correlationId":"ed8b5407-4182-4673-af08-eff3c354ca90"}}
HTTP 200
### GET /api/hotel/buscar/1 (EMPLEADO)
{"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"c5927881-5176-437b-944b-e9a121359c31"}}
HTTP 404
### GET /api/piso/listar?idHotel=1&page=0&size=20 (EMPLEADO)
{"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"442cf5a6-58a2-416a-8fac-a1f257c49344"}}
HTTP 404
### GET /api/habitacion/listar?idHotel=1&page=0&size=20 (EMPLEADO)
{"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"c274fd88-c250-4718-b00a-8a7ec5f8fd07"}}
HTTP 404
```

```text
### Listados con ADMIN, page=0&size=2
/api/hotel/listar           data_len=5 meta={'correlationId': 'fc9f758e-563f-48b0-bd0e-9072e9974643'}
/api/piso/listar?idHotel=1  data_len=3 meta={'correlationId': '21852f03-f29c-47d5-b783-29133754c5f5'}
/api/categoria/listar       data_len=6 meta={'correlationId': '2f85fba1-f7cf-4b2f-b6ac-a72b0ee7d25a'}
/api/habitacion/listar      data_len=5 meta={'correlationId': '9132fe88-38dc-4131-9b10-cb0c2561f0d5'}
```

```text
### PUT /api/estadohabitacion/actualizar/1 (ADMIN, solo cuerpo {"descripcion":"Lista"})
{"success":false,"message":"Ocurrió un error inesperado.","error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"543c9eb3-845d-493b-b515-88075b556f8f"}}
HTTP 500
### GET /api/habitacion/buscar/101 (ADMIN)
claves=['idHabitacion','idHotel','numero','idPiso','pisoNombre','idCategoria','categoriaNombre','detalle','precio','publicada','estado','idEstadoHabitacion','estadoFisico','ocupacion','blockId','endpoint','version']
urlsImagenes presente: False
### grep
services/hotel-service/pom.xml = 0
services/hotel-service/src/main/resources/application.yml = 0
services/identity-service/pom.xml = 1
services/identity-service/src/main/resources/application.yml = 3
### Eureka
[('localhost:hotel-service:8082', 'localhost', '127.0.0.1')]
```

Los cinco fallos de §3 quedan reproducidos. Evidencia: esta salida literal, transcrita en este
plan y adjunta al PR de ejecución; sin tokens ni credenciales.

### Verificación después del arreglo (pasos 8 a 12)

```bash
PW=$(grep '^DEMO_PASSWORD=' .env | cut -d= -f2-)
ADMIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"correo\":\"admin@hotel.test\",\"clave\":\"$PW\"}" | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
EMP=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"correo\":\"recepcion.lima@hotel.test\",\"clave\":\"$PW\"}" | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# Reproducción 1: EMPLEADO de la sede 1
for URL in \
  "http://localhost:8080/api/hotel/listar?page=0&size=100" \
  "http://localhost:8080/api/hotel/buscar/1" \
  "http://localhost:8080/api/piso/listar?idHotel=1&page=0&size=20" \
  "http://localhost:8080/api/habitacion/listar?idHotel=1&page=0&size=20"; do
  curl -s -w '\nHTTP %{http_code}\n' "$URL" -H "Authorization: Bearer $EMP"
done

# Reproducción 2: meta de página con ADMIN
for URL in \
  "http://localhost:8080/api/hotel/listar?page=0&size=2" \
  "http://localhost:8080/api/piso/listar?idHotel=1&page=0&size=2" \
  "http://localhost:8080/api/categoria/listar?page=0&size=2" \
  "http://localhost:8080/api/habitacion/listar?idHotel=1&page=0&size=2"; do
  curl -s -w '\nHTTP %{http_code}\n' "$URL" -H "Authorization: Bearer $ADMIN"
done

# Reproducción 3: estado físico solo con cuerpo
curl -s -i -X PUT "http://localhost:8080/api/estadohabitacion/actualizar/1" \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H 'If-Match: "1"' -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d '{"descripcion":"Lista"}'

# Reproducción 4: galería
curl -s "http://localhost:8080/api/habitacion/buscar/101" -H "Authorization: Bearer $ADMIN"

# Reproducción 5: Eureka
U=$(grep '^EUREKA_USERNAME=' .env | cut -d= -f2-)
P=$(grep '^EUREKA_PASSWORD=' .env | cut -d= -f2-)
curl -s -u "$U:$P" -H 'Accept: application/json' http://localhost:8761/eureka/apps/HOTEL-SERVICE

# Fuera de alcance sigue en 404
curl -s -w '\nHTTP %{http_code}\n' \
  "http://localhost:8080/api/habitacion/listar?idHotel=2&page=0&size=20" \
  -H "Authorization: Bearer $EMP"
```

Resultados esperados: 200 con la sede 1 y sus pisos/habitaciones en las cuatro llamadas del
EMPLEADO; 404 `RESOURCE_NOT_FOUND` al pedir la sede 2; `meta.page`, `meta.size` y
`meta.totalElements` en los cuatro listados de ADMIN, con como máximo dos filas; 200 en el PUT de
estado con `version` incrementada; `urlsImagenes` con la imagen de la habitación 101; y una
instancia de Eureka publicada por el servicio, con `hostName` de host y no `localhost`.
Evidencia: los cuerpos completos, en el PR de ejecución y en la bitácora del plan.

### Evidencia de ejecución del arreglo (pasos 2 a 13)

Ejecutado el 2026-10-20 desde la rama `back-arreglo-hotel-catalogo-admin`, con `hotel-service`
reiniciado desde el código corregido, Eureka en `8761` y el Gateway reiniciado en `8080`. Tokens
humanos obtenidos por `POST /api/auth/login` (no se transcriben). Salidas literales; en los cuerpos
muy largos se abrevia con `…` solo la parte repetida, los campos verificados van completos:

**Paso 2 — `CurrentUser.sedes()` con `Long`.**

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in pe.hotel.hotel.domain.security.CurrentUserTests
```

**Paso 3 y 10 — paginación y meta de página (ADMIN, `page=0&size=2`).**

```text
/api/hotel/listar           filas=2 meta={'correlationId':'bc5dddce-…','page':0,'size':2,'totalElements':5}  HTTP 200
/api/piso/listar?idHotel=1  filas=2 meta={'correlationId':'e3c57fbf-…','page':0,'size':2,'totalElements':3}  HTTP 200
/api/categoria/listar       filas=2 meta={'correlationId':'97c569c6-…','page':0,'size':2,'totalElements':6}  HTTP 200
/api/habitacion/listar      filas=2 meta={'correlationId':'b31f729c-…','page':0,'size':2,'totalElements':5}  HTTP 200

GET /api/hotel/listar?page=-1&size=20
{"success":false,"message":"Revisa los datos marcados.","error":{"code":"VALIDATION_ERROR","fields":{"page":"page debe ser mayor o igual que 0"}},"meta":{"correlationId":"e78f62d9-b185-4409-be89-a2d053e97cf2"}}  HTTP 400
GET /api/hotel/listar?page=0&size=0
{"success":false,"message":"Revisa los datos marcados.","error":{"code":"VALIDATION_ERROR","fields":{"size":"size debe estar entre 1 y 100"}},"meta":{"correlationId":"4aa25280-c9ad-4808-9244-750d7c9555ed"}}  HTTP 400
GET /api/hotel/listar?page=0&size=101
{"success":false,"message":"Revisa los datos marcados.","error":{"code":"VALIDATION_ERROR","fields":{"size":"size debe estar entre 1 y 100"}},"meta":{"correlationId":"ec8cf727-5e65-4239-808f-dbbde2594beb"}}  HTTP 400
```

**Pasos 4 y 11 — estado físico solo con el cuerpo del contrato.**

```text
PUT /api/estadohabitacion/actualizar/1  If-Match: "2"  {"descripcion":"Lista"}
HTTP/1.1 200 OK
ETag: "3"
{"success":true,"message":"OK","data":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":3},"meta":{"correlationId":"5b1c4eed-1ddf-4787-b68f-59c589b7259d"}}

PUT sin If-Match  {"descripcion":"Lista"}
{"success":false,"message":"Falta la versión del registro.","error":{"code":"PRECONDITION_REQUIRED","fields":{}},"meta":{"correlationId":"5d4bd3d9-fb7d-4901-b18d-f7703917c215"}}  HTTP 428

PUT con If-Match "1" (versión vieja)
{"success":false,"message":"Otra persona modificó este registro. Recarga para ver los cambios.","error":{"code":"PRECONDITION_FAILED","fields":{}},"meta":{"correlationId":"2155a0e0-f0fb-4c44-b0bc-06b42bd679b3"}}  HTTP 412
```

**Paso 5 y 11 — galería en la búsqueda de habitación.**

```text
GET /api/habitacion/buscar/101
urlsImagenes= [{'url': '/assets/demo/hab-101-1.jpg', 'alt': 'Habitacion de demostracion', 'orden': 1}]
```

**Paso 6 — dependencia y bloque `eureka`.**

```text
services/hotel-service/pom.xml = 1
services/hotel-service/src/main/resources/application.yml = 3
services/identity-service/pom.xml = 1
services/identity-service/src/main/resources/application.yml = 3
```

El `application.yml` conserva `server.port: 8082` y el resto de la configuración; el bloque añadido
es el mismo de `identity-service` (`client.enabled`, `register-with-eureka`, `fetch-registry`,
`service-url.defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}`, `username`, `password`,
`instance.prefer-ip-address: true`), y la dependencia va sin `<version>`.

**Paso 7 — pruebas del módulo.**

```text
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Paso 8 — registro propio en Eureka.**

```text
DiscoveryClient_HOTEL-SERVICE/localhost:hotel-service:8082 - registration status: 204
GET /eureka/apps/HOTEL-SERVICE
[('localhost:hotel-service:8082', '192.168.56.1', '192.168.56.1', 'UP')]
```

Una sola instancia, `hostName` = IP del host (no `localhost`) e `ipAddr` distinta de `127.0.0.1`.
El log de arranque no repite errores de registro (`grep -c "registration failed|status code 401"`
= 0). Se arrancó con EUREKA_URL con userinfo, forma de `IA-back-identity-auth-09`, sin tocar
`.env` ni `infra/**`.

**Paso 9 — reproducción 1 con EMPLEADO (`sedes: [1]`).**

```text
GET /api/hotel/listar?page=0&size=100
{"success":true,...,"data":[{"idHotel":1,"slug":"lima-centro",...}],"meta":{"correlationId":"f6a717e4-…","page":0,"size":100,"totalElements":1}}  HTTP 200
GET /api/hotel/buscar/1
{"success":true,...,"data":{"idHotel":1,"slug":"lima-centro",...}}  HTTP 200
GET /api/piso/listar?idHotel=1&page=0&size=20
{"success":true,...,"data":[{"idPiso":11,...},{"idPiso":33,...},{"idPiso":35,...}],"meta":{"page":0,"size":20,"totalElements":3}}  HTTP 200
GET /api/habitacion/listar?idHotel=1&page=0&size=20
{"success":true,...,"data":[{"idHabitacion":101,...},{"idHabitacion":102,...},...],"meta":{"page":0,"size":20,"totalElements":5}}  HTTP 200
GET /api/habitacion/listar?idHotel=2&page=0&size=20
{"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"5c61b6cd-0572-4ccd-8ef3-6d8f0d442872"}}  HTTP 404
```

**Paso 12 — Gateway reiniciado, sin registro manual.**

```text
GET /api/hotel/listar?page=0&size=100  (ADMIN, Gateway recién arrancado)
{"success":true,"message":"OK","data":[{"idHotel":1,...},{"idHotel":2,...},...]}  HTTP 200
GET /eureka/apps/HOTEL-SERVICE
[('localhost:hotel-service:8082', '192.168.56.1', '192.168.56.1', 'UP')]
```

Sin 503 `DEPENDENCY_UNAVAILABLE`; la instancia es la que publicó el propio servicio.

**Paso 13 — comandos canónicos.**

```text
$ bash scripts/mvn.sh -f services/pom.xml verify
[INFO] identity-service ......... SUCCESS
[INFO] hotel-service ............ SUCCESS
[INFO] reception-service ........ SUCCESS
[INFO] inventory-service ........ SUCCESS
[INFO] sales-service ............ SUCCESS
[INFO] reporting-service ........ SUCCESS
[INFO] notification-service ..... SUCCESS
[INFO] api-gateway .............. SUCCESS
[INFO] discovery-server ......... SUCCESS
[INFO] BUILD SUCCESS

$ python scripts/validar-planes.py
30 plan(es) revisado(s); 30 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```

### Unidad y build (pasos 7 y 13)

```bash
bash scripts/mvn.sh -f services/pom.xml -pl hotel-service test
bash scripts/mvn.sh -f services/pom.xml verify
python scripts/validar-planes.py
python scripts/validar-contratos.py --base origin/main
```

Resultado esperado: `BUILD SUCCESS` del módulo y de los nueve módulos, con las pruebas nuevas en
verde; 0 errores del validador; contratos solo ampliados. Evidencia: salidas adjuntas al PR.

### Reproducción original

Los cinco comandos de §3 quedan en: EMPLEADO con su sede (200), `meta` con página y total (200),
estado con cuerpo (200), búsqueda con `urlsImagenes` (200) y registro propio en Eureka. El 503
`DEPENDENCY_UNAVAILABLE` del Gateway desaparece en un arranque limpio. Evidencia: respuestas y log
de arranque corregido.

## 7. Preguntas abiertas

Ninguna. El alcance por sede, la paginación, el cuerpo de estado y `urlsImagenes` están publicados
en la spec y en los OpenAPI; el arreglo solo alinea el código. Sobre Eureka: el defecto de código es
la ausencia del cliente y su configuración, que se corrige aquí; el 401 por credenciales en el
userinfo de EUREKA_URL es entorno local y ya está resuelto por
[`IA-back-identity-auth-09.md`](../implementacion/decisiones/IA-back-identity-auth-09.md), sin
cambios en `.env` ni en `infra/**`.

## 8. Criterio de terminado

- [x] Todos los pasos de §4 marcados con su verificación.
- [x] Un EMPLEADO de la sede 1 ve su sede, sus pisos y sus habitaciones; las sedes ajenas siguen en
  404.
- [x] Los cuatro listados devuelven `meta.page`, `meta.size` y `meta.totalElements`, con `page`/`size`
  fuera de rango en 400.
- [x] `PUT /api/estadohabitacion/actualizar/{id}` funciona solo con el `requestBody` del contrato y
  conserva 428/412.
- [x] `GET /api/habitacion/buscar/{id}` devuelve `urlsImagenes` ordenadas, y `[]` cuando no hay
  imágenes.
- [x] `hotel-service` se registra solo en Eureka y el Gateway lo enruta sin registro manual ni 503.
- [x] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`;
  `python scripts/validar-planes.py` con 0 errores y `python scripts/validar-contratos.py --base
  origin/main` sin cambios de contrato.
- [x] La reproducción original de §3 está en verde con evidencia adjunta al PR y sin preguntas
  abiertas.
