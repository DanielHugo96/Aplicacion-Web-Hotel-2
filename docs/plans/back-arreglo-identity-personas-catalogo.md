# back-arreglo-identity-personas-catalogo — etiqueta de rol, lectura de EMPLEADO y 428 en personas (F2)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-identity-personas-catalogo` |
| Ámbito | `back` |
| Componente | `identity-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-identity-personas` |
| Repara | `back-identity-personas` |
| Spec | [`identity/api.md` § REST](../especificacion/microservicios/identity/api.md#rest), [§ DTO de entrada](../especificacion/microservicios/identity/api.md#dto-de-entrada), [`identity/base-de-datos.md` § Tablas](../especificacion/microservicios/identity/base-de-datos.md#tablas), [§ DDL de referencia](../especificacion/microservicios/identity/base-de-datos.md#ddl-de-referencia), [`03-contrato-comun.md` § Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [§ Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§ Transporte, notación y tipos](../especificacion/03-contrato-comun.md#transporte-notación-y-tipos), [`identity.yaml`](../contracts/openapi/identity.yaml) y [`errors.md` § Genéricos](../contracts/errors.md) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Con `identity-service` y el Gateway arriba, `PUT /api/tipopersona/actualizar/{id}` responde 200 con
la versión incrementada; `GET /api/persona/listar` y `GET /api/persona/buscar/{id}` con token
EMPLEADO dejan de responder 500 y cumplen la regla de `documento` exacto; y los `PUT`/`DELETE` de
personas y el `PUT` de tipo de persona sin `If-Match` responden 428 `PRECONDITION_REQUIRED`.

Se comprueba repitiendo las tres reproducciones de §3 y dejando sus comandos en verde.

## 2. Alcance

**Entra:** la zona del plan reparado `back-identity-personas`, componente `identity-service`:

- `services/identity-service/src/main/java/pe/hotel/identity/persona/PersonaRepository.java`: retirar
  la escritura de `updated_at` del `UPDATE tipo_persona` de `actualizarTipoPersona`, que es la única
  sentencia que referencia una columna inexistente.
- `services/identity-service/src/main/java/pe/hotel/identity/persona/PersonaController.java`:
  normalizar el claim `sedes` a `List<Integer>` a partir de `Number` (sin cast no comprobado a
  `List<Integer>`) en todos los métodos que lo leen; y declarar `If-Match` como opcional
  (`required = false`) en `PUT /api/persona/actualizar/{id}`,
  `DELETE /api/persona/eliminar/{id}` y `PUT /api/tipopersona/actualizar/{id}`, dejando que el
  servicio existente responda
  `PRECONDITION_REQUIRED`.
- `services/identity-service/src/test/java/pe/hotel/identity/persona/`: pruebas de controlador que
  reproducen el claim `sedes` con valores `Long` y los códigos 200/400/403/428.
- `docs/plans/back-arreglo-identity-personas-catalogo.md` y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- `docs/contracts/**` y `docs/especificacion/**`: no se modifican. Se implementa lo que ya publican
  `identity.yaml`, `identity/api.md` y `errors.md`.
- Migraciones Flyway y el DDL de `tipo_persona`: la spec fija la tabla con `id`, `codigo`,
  `descripcion`, `estado` y `version`, sin `updated_at`; por eso se corrige la sentencia SQL, no se
  añade la columna.
- Las reglas de negocio de personas, roles, sedes, último ADMIN, auditoría, outbox y mensajería.
- `hotel-service` y su filtro por sede, aunque compartan el mismo origen (claims numéricos): se
  corrigen en `back-arreglo-hotel-catalogo-admin`.
- `api-gateway`, `discovery-server`, `infra/**`, `.env`, las versiones fijadas
  (`services/pom.xml`, `apps/*/package.json`, el `README.md` raíz) y cualquier otro servicio o app.

## 3. Contexto

El defecto lo introdujo `back-identity-personas`, plan ya `cerrado`, en el PR #70 (commit
`4bca9e7`, «feat: implementa el plan back-identity-personas (#70)»). Su zona es
`services/identity-service/**`, exactamente la zona de este arreglo. Lo detectó la ejecución de
`front-admin-web-catalogo` al cerrar `/app/tipos-persona` contra el Gateway real; su §9 y la
evidencia `apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md` viven en la rama
`front-admin-web-catalogo`, no en `main`, y se citan aquí sin enlace.

Las reproducciones se ejecutaron desde `main` el 2026-10-19, con `identity-service` en `8081`, el
Gateway en `8080` y los JWT humanos de `admin@hotel.test` (ADMIN) y `recepcion.lima@hotel.test`
(EMPLEADO, `sedes: [1]`).

**Reproducción 1 — `PUT /api/tipopersona/actualizar/{id}` responde 500.**

```text
$ curl -s -i -X PUT "http://localhost:8080/api/tipopersona/actualizar/3" \
    -H "Authorization: Bearer <ADMIN>" -H 'Content-Type: application/json' \
    -H 'If-Match: "1"' -H "Idempotency-Key: <uuid>" -d '{"descripcion":"Cliente"}'

HTTP/1.1 500 Internal Server Error
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"b410ce10-d23e-4907-a1c4-aea250a9424f"}}
```

`GET /api/tipopersona/buscar/{id}` con id 3 sí responde 200 con `ETag: "1"`, así que el fallo es de
escritura.
La sentencia de `PersonaRepository.actualizarTipoPersona` actualiza `updated_at`, columna que la
tabla no tiene:

```text
$ docker exec -e PGPASSWORD=<password> hotel-postgres-1 psql -U identity_app -d identity_db \
    -c "SELECT column_name FROM information_schema.columns WHERE table_name='tipo_persona' ORDER BY ordinal_position;"
 column_name
-------------
 id
 codigo
 descripcion
 estado
 version
(5 rows)

$ docker exec -e PGPASSWORD=<password> hotel-postgres-1 psql -U identity_app -d identity_db \
    -c "UPDATE tipo_persona SET descripcion='Cliente', version=version+1, updated_at=now() WHERE id=3 AND version=1;"
ERROR:  column "updated_at" of relation "tipo_persona" does not exist
LINE 1: ...ona SET descripcion='Cliente', version=version+1, updated_at...
```

La migración `V4__identity_personas.sql` crea `tipo_persona` sin esa columna y la spec
[`identity/base-de-datos.md` § DDL de referencia](../especificacion/microservicios/identity/base-de-datos.md#ddl-de-referencia)
tampoco la declara. La tabla `persona` sí tiene `updated_at`; el error es solo de `tipo_persona`.

**Reproducción 2 — cualquier lectura o mutación de personas con token EMPLEADO responde 500.**

```text
$ curl -s -w '\nHTTP %{http_code}\n' "http://localhost:8080/api/persona/listar?page=0&size=20&documento=90000100" \
    -H "Authorization: Bearer <EMPLEADO>"
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"8c759cc9-bdb4-4109-993c-5050863226d2"}}
HTTP 500

$ curl -s -w '\nHTTP %{http_code}\n' "http://localhost:8080/api/persona/buscar/100?documento=90000100" \
    -H "Authorization: Bearer <EMPLEADO>"
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"cd8a25c1-c925-4836-bff0-1ecf3e0449e0"}}
HTTP 500
```

El log de `identity-service` muestra
`java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Integer` en
`PersonaController`. La causa es el cast no comprobado
`List<Integer> sedes = (List<Integer>) claims.get("sedes")`: el decoder produce números `Long`, el
cast del `List` se borra en ejecución y el fallo aparece al hacer `sedes.get(0)` y asignarlo a
`Integer`. Con ADMIN funciona porque su claim es `[]` y nunca se lee un elemento; con EMPLEADO
`sedes` es `[1]` y el 500 es sistemático. El mismo patrón está en `listar`, `buscar`, `registrar`,
`actualizar`, `eliminar` y `actualizarTipoPersona`.

**Reproducción 3 — `PUT /api/persona/actualizar/{id}` sin `If-Match` responde 500.**

```text
$ curl -s -i -X PUT "http://localhost:8080/api/persona/actualizar/100" \
    -H "Authorization: Bearer <ADMIN>" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: <uuid>" \
    -d '{"nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","idTipoPersona":3,"sedes":[1],"estado":true}'

HTTP/1.1 500 Internal Server Error
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"9dbde70d-a078-4359-8faf-706e997d5745"}}
```

El controlador declara `@RequestHeader(name = "If-Match") String ifMatch` sin `required = false`;
la cabecera ausente lanza `MissingRequestHeaderException` y el manejador la clasifica como
`INTERNAL_ERROR`. El servicio ya sabe responder lo correcto: `ETag.versionDe(null)` devuelve `-1` y
`PersonaService.actualizar` lanza `PRECONDITION_REQUIRED`; el mismo patrón existe en `eliminar` y en
`actualizarTipoPersona`.

**Qué fija la spec y el contrato.** [`identity/api.md` § REST](../especificacion/microservicios/identity/api.md#rest)
exige `documento` exacto para que un EMPLEADO liste o busque personas, con 400 sin él y sin acceso a
ADMIN/EMPLEADO ajenos. [`identity.yaml`](../contracts/openapi/identity.yaml) publica 428 en los
`PUT`/`DELETE` con versión y 412 con versión distinta, y [`errors.md`](../contracts/errors.md)
define `PRECONDITION_REQUIRED`, `VALIDATION_ERROR` y `FORBIDDEN`. No hay regla nueva que decidir.

## 4. Pasos

- [x] 1. Reproducir los tres fallos desde `main` con el Gateway y los tokens de §3. Verificación: se
  observan los 500 y las salidas literales de §3, y `tipo_persona` no tiene `updated_at`.
- [x] 2. Corregir `PersonaRepository.actualizarTipoPersona`: quitar `updated_at = now()` del `UPDATE
  tipo_persona`, conservando `descripcion = ?`, `version = version + 1`, la condición
  `WHERE id = ? AND version = ?` y la excepción de bloqueo optimista. Verificación: la sentencia no
  menciona `updated_at` y compila; el `UPDATE` equivalente contra `identity_db` deja de fallar con
  `column "updated_at" ... does not exist`.
- [x] 3. Añadir a `PersonaController` un helper privado que convierta el claim `sedes` en
  `List<Integer>` aceptando `Number` (`((Number) valor).intValue()`), devolviendo lista vacía si el
  claim no es una lista. Usarlo en `listar`, `buscar`, `registrar`, `actualizar`, `eliminar` y
  `actualizarTipoPersona`, eliminando los cast no comprobados a `List<Integer>`. Verificación:
  `grep -n "List<Integer>) .*get(\"sedes\")"` no encuentra casts directos y la compilación termina
  con código 0.
- [x] 4. Declarar `If-Match` con `required = false` en los `PUT`/`DELETE` de persona y en el `PUT` de
  tipo de persona, sin cambiar la firma del servicio ni las comprobaciones que ya hacen
  `ETag.versionDe` y `PRECONDITION_REQUIRED`. Verificación: sin cabecera, los tres métodos llegan al
  servicio y devuelven 428 en vez de 500.
- [x] 5. Añadir pruebas de controlador en `services/identity-service/src/test/java/pe/hotel/identity/persona/`
  con `MockMvc` y `SecurityMockMvcRequestPostProcessors.jwt()`: claim `sedes` con `List.of(1L)` y rol
  EMPLEADO → `listar` con `documento` 200, sin `documento` 400, `buscar` con `documento` 200 y `PUT`
  de persona 403; ADMIN → `PUT` de persona sin `If-Match` 428 y `PUT` de tipo de persona sin
  `If-Match` 428. Verificación: las pruebas pasan con
  `bash scripts/mvn.sh -f services/pom.xml -pl identity-service test`.
- [x] 6. Rearrancar `identity-service` desde la rama corregida y repetir la reproducción 1:
  `PUT /api/tipopersona/actualizar/{id}` con id 3, `If-Match: "1"`, `Idempotency-Key` y
  `{"descripcion":"Cliente"}`. Verificación: 200, `data.version` pasa de 1 a 2, `ETag` nuevo y el
  log ya no muestra `column "updated_at"`.
- [x] 7. Repetir la reproducción 2 con el token EMPLEADO: `GET /api/persona/listar` con
  `documento=90000100` y `GET /api/persona/buscar/{id}` con id 100 y el mismo `documento`.
  Verificación: 200 en
  ambos, `data.personas` solo contiene CLIENTE y ningún 500; sin `documento`, `listar` responde 400
  `VALIDATION_ERROR` como fija la spec.
- [x] 8. Comprobar las mutaciones con EMPLEADO y el 428 de ADMIN: `PUT /api/persona/actualizar/{id}`
  con id 100 y EMPLEADO → 403 `FORBIDDEN`; el mismo `PUT` con ADMIN y sin `If-Match` → 428
  `PRECONDITION_REQUIRED`; `DELETE /api/persona/eliminar/{id}` sin `If-Match` → 428. Verificación:
  códigos y cuerpos guardados.
- [x] 9. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml -pl identity-service test`,
  `bash scripts/mvn.sh -f services/pom.xml verify` y `python scripts/validar-planes.py`.
  Verificación: `BUILD SUCCESS`, 0 errores del validador y los avisos resueltos o justificados.
- [x] 10. Guardar en *Pruebas* las salidas de los pasos 1, 5, 6, 7, 8 y 9, marcar todos los pasos y
  cambiar `Estado` a `cerrado` en el último commit. Verificación: no quedan pasos sin marcar y las
  tres reproducciones originales están en verde.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/identity.yaml`. Se implementan rutas ya
  publicadas: `GET /api/persona/listar` (200 con `documento`, 400 sin él, 403 por rol),
  `GET /api/persona/buscar/{id}` (200/400/404), `PUT /api/persona/actualizar/{id}` y
  `DELETE /api/persona/eliminar/{id}` (200/412/428) y `PUT /api/tipopersona/actualizar/{id}`
  (200/412/428). No se añade, cambia ni elimina ninguna ruta, schema, `operationId` ni código.
- **Eventos y comandos:** ninguno. No se toca `identity.persona.v1`, el outbox ni
  `docs/contracts/events/`.
- **Persistencia:** ninguna migración. `tipo_persona` conserva el DDL de la spec (`id`, `codigo`,
  `descripcion`, `estado`, `version`); solo se deja de escribir una columna que no existe. `persona`
  mantiene `created_at`/`updated_at` y sus sentencias no cambian.
- **Errores:** desaparece el `INTERNAL_ERROR` de estas rutas y se usan los códigos ya publicados en
  [`errors.md`](../contracts/errors.md): `PRECONDITION_REQUIRED` (428), `PRECONDITION_FAILED` (412),
  `VALIDATION_ERROR` (400), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404) y `UNAUTHENTICATED` (401).
- **Seguridad:** no se cambia el validador JWT, el issuer, la audiencia ni los roles. El claim
  `sedes` se sigue leyendo del token validado, nunca de cabeceras.
- **Configuración y versiones:** ninguna propiedad nueva. No se tocan `services/pom.xml`,
  `apps/*/package.json`, `infra/**`, `.env` ni el `README.md` raíz.

## 6. Pruebas

### Reproducción antes del arreglo (paso 1)

Ejecutado el 2026-10-20 sobre `main` (`46dd5b4`), con `identity-service` en `8081`, el Gateway en
`8080`, Eureka en `8761` y los JWT de `admin@hotel.test` (ADMIN) y `recepcion.lima@hotel.test`
(EMPLEADO, `sedes: [1]`).

```text
$ docker exec -e PGPASSWORD=<variable> hotel-postgres-1 psql -U identity_app -d identity_db \
    -c "SELECT column_name FROM information_schema.columns WHERE table_name='tipo_persona' ORDER BY ordinal_position;"
 column_name
-------------
 id
 codigo
 descripcion
 estado
 version
(5 rows)

$ curl -s -i -X PUT "http://localhost:8080/api/tipopersona/actualizar/3" \
    -H "Authorization: Bearer <ADMIN>" -H 'Content-Type: application/json' \
    -H 'If-Match: "1"' -H "Idempotency-Key: <uuid>" -d '{"descripcion":"Cliente"}'
HTTP/1.1 500 Internal Server Error
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"e5b6a59a-87de-4b15-a235-03510c3c4390"}}

$ curl -s -w '\nHTTP %{http_code}\n' \
    "http://localhost:8080/api/persona/listar?page=0&size=20&documento=90000100" \
    -H "Authorization: Bearer <EMPLEADO>"
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"83783d52-172e-43f2-a541-4bcd68543dcd"}}
HTTP 500

$ curl -s -w '\nHTTP %{http_code}\n' \
    "http://localhost:8080/api/persona/buscar/100?documento=90000100" \
    -H "Authorization: Bearer <EMPLEADO>"
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"4345b84d-09dd-4a99-a0d7-08f72ce16675"}}
HTTP 500

$ curl -s -w '\nHTTP %{http_code}\n' -X PUT "http://localhost:8080/api/persona/actualizar/100" \
    -H "Authorization: Bearer <ADMIN>" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: <uuid>" \
    -d '{"nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","idTipoPersona":3,"sedes":[1],"estado":true}'
{"success":false,"message":"Ocurrió un error inesperado.","data":null,"error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"5e2011eb-0afb-4cab-85ca-bff826e5c759"}}
HTTP 500
```

### Corrección del SQL de `tipo_persona` (paso 2)

`grep -n "updated_at" PersonaRepository.java` no encuentra la sentencia de `tipo_persona`; el
`UPDATE` equivalente contra `identity_db`, dentro de una transacción revertida, ya no falla:

```text
$ docker exec -e PGPASSWORD=<variable> hotel-postgres-1 psql -U identity_app -d identity_db \
    -v ON_ERROR_STOP=1 -c "BEGIN; UPDATE tipo_persona SET descripcion='Cliente', version=version+1 WHERE id=3 AND version=1; ROLLBACK;"
BEGIN
UPDATE 1
ROLLBACK
```

### Normalización del claim `sedes` (paso 3)

El helper `sedesDe(Map)` convierte cada `Number` con `intValue()` y devuelve lista vacía si el claim
no es una lista. Ya no queda ningún cast directo; la compilación del módulo termina con código 0:

```text
$ cd services/identity-service/src/main/java/pe/hotel/identity/persona \
    && grep -n 'List<Integer>) .*get("sedes")' PersonaController.java
$ echo $?
1
$ bash scripts/mvn.sh -f services/pom.xml -pl identity-service -q -DskipTests compile
$ echo $?
0
```

### Verificación después del arreglo (pasos 6, 7 y 8)

Con `identity-service` rearrancado desde la rama corregida y el Gateway en `8080`:

```bash
PW=$(grep '^DEMO_PASSWORD=' .env | cut -d= -f2-)
ADMIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"correo\":\"admin@hotel.test\",\"clave\":\"$PW\"}" | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
EMP=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"correo\":\"recepcion.lima@hotel.test\",\"clave\":\"$PW\"}" | python -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# Reproducción 1: 200 con versión 2
curl -s -i -X PUT "http://localhost:8080/api/tipopersona/actualizar/3" \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H 'If-Match: "1"' -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d '{"descripcion":"Cliente"}'

# Reproducción 2: EMPLEADO con documento -> 200; sin documento -> 400
curl -s -w '\nHTTP %{http_code}\n' \
  "http://localhost:8080/api/persona/listar?page=0&size=20&documento=90000100" \
  -H "Authorization: Bearer $EMP"
curl -s -w '\nHTTP %{http_code}\n' \
  "http://localhost:8080/api/persona/listar?page=0&size=20" -H "Authorization: Bearer $EMP"
curl -s -w '\nHTTP %{http_code}\n' \
  "http://localhost:8080/api/persona/buscar/100?documento=90000100" \
  -H "Authorization: Bearer $EMP"

# Reproducción 3: 428 sin If-Match; 403 con EMPLEADO
curl -s -w '\nHTTP %{http_code}\n' -X PUT "http://localhost:8080/api/persona/actualizar/100" \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d '{"nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","idTipoPersona":3,"sedes":[1],"estado":true}'
curl -s -w '\nHTTP %{http_code}\n' -X PUT "http://localhost:8080/api/persona/actualizar/100" \
  -H "Authorization: Bearer $EMP" -H 'Content-Type: application/json' \
  -H 'If-Match: "1"' -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d '{"nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","idTipoPersona":3,"sedes":[1],"estado":true}'
```

Resultados obtenidos el 2026-10-20 tras rearrancar `identity-service` desde la rama corregida. El
proceso se relanzó con `spring-boot:run` sobre `services/pom.xml` y el log quedó en
`C:/Users/juan2/AppData/Local/Temp/opencode/identity-arreglo.log`.

**Paso 6 — `PUT /api/tipopersona/actualizar/{id}` (id 3):**

```text
HTTP/1.1 200 OK
ETag: "2"
{"success":true,"message":"OK","data":{"idTipoPersona":3,"descripcion":"Cliente","codigo":"CLIENTE","estado":true,"version":2},"meta":{"correlationId":"75ff833b-b537-47dc-ba09-39802c3cfe88"}}

$ grep -c 'column "updated_at"' C:/Users/juan2/AppData/Local/Temp/opencode/identity-arreglo.log
0
$ grep -c 'ClassCastException' C:/Users/juan2/AppData/Local/Temp/opencode/identity-arreglo.log
0
```

**Paso 7 — lecturas con EMPLEADO (`sedes: [1]`):**

```text
GET /api/persona/listar?page=0&size=20&documento=90000100
{"success":true,"message":"OK","data":{"personas":[{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","fotoUrl":null,"idTipoPersona":3,"tipoPersona":"Cliente","sedes":[],"estado":true,"fechaCreacion":"2026-10-18T17:52:02.971456Z","version":5}]},"meta":{"correlationId":"186e27fd-a48b-4a32-9121-8bf12c49c541","page":0,"size":20,"totalElements":1}}
HTTP 200

GET /api/persona/listar?page=0&size=20
{"success":false,"message":"Revisa los datos marcados.","data":null,"error":{"code":"VALIDATION_ERROR","fields":{"documento":"obligatorio para consultar como empleado"}},"meta":{"correlationId":"73d57638-6826-41ad-bf9d-381bcc8e9e2d"}}
HTTP 400

GET /api/persona/buscar/{id}?documento=90000100 (id 100)
{"success":true,"message":"OK","data":{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","fotoUrl":null,"idTipoPersona":3,"tipoPersona":"Cliente","sedes":[],"estado":true,"fechaCreacion":"2026-10-18T17:52:02.971456Z","version":5},"meta":{"correlationId":"2a31ac79-47c6-46be-bbbf-db51901ef9d8"}}
HTTP 200
```

**Paso 8 — mutaciones de persona y 428:**

```text
PUT /api/persona/actualizar/{id} (id 100, EMPLEADO)
{"success":false,"message":"No tienes permiso para esta acción.","data":null,"error":{"code":"FORBIDDEN","fields":{}},"meta":{"correlationId":"cd752ab1-5dfa-45ed-9a41-82569a042755"}}
HTTP 403

PUT /api/persona/actualizar/{id} (id 100, ADMIN, sin If-Match)
{"success":false,"message":"Falta la versión del registro.","data":null,"error":{"code":"PRECONDITION_REQUIRED","fields":{"If-Match":"obligatorio"}},"meta":{"correlationId":"3cd546cb-331c-4079-8d75-fa2595b7a871"}}
HTTP 428

DELETE /api/persona/eliminar/{id} (id 100, ADMIN, sin If-Match)
{"success":false,"message":"Falta la versión del registro.","data":null,"error":{"code":"PRECONDITION_REQUIRED","fields":{"If-Match":"obligatorio"}},"meta":{"correlationId":"10706aeb-b1b9-4e66-beb9-d3328b392202"}}
HTTP 428
```

### Pruebas de controlador y `If-Match` (pasos 4 y 5)

`PersonaControllerTests` usa `MockMvc` con `jwt()` y dos claims distintos:
EMPLEADO con `sedes: [1L]` y ADMIN con `sedes: []`. Cubre los siete casos del paso 5 más el
`DELETE` sin `If-Match`:

```text
$ bash scripts/mvn.sh -f services/pom.xml -pl identity-service -Dtest=PersonaControllerTests test
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Los tres métodos con versión (`PUT /api/persona/actualizar/{id}`,
`DELETE /api/persona/eliminar/{id}` y `PUT /api/tipopersona/actualizar/{id}`) llegan al servicio sin
`If-Match` y responden 428 `PRECONDITION_REQUIRED` en lugar de 500. La suite completa del módulo
también queda en verde:

```text
$ bash scripts/mvn.sh -f services/pom.xml -pl identity-service test
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Unidad y build (pasos 5 y 9)

Ejecutado el 2026-10-20. Salida completa en
`C:/Users/juan2/AppData/Local/Temp/opencode/mvn-verify.log`:

```text
$ bash scripts/mvn.sh -f services/pom.xml -pl identity-service test
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ bash scripts/mvn.sh -f services/pom.xml verify
[INFO] Reactor Summary for Hotel services (agregador) 0.0.1-SNAPSHOT:
...
[INFO] BUILD SUCCESS

$ python scripts/validar-planes.py
30 plan(es) revisado(s); 30 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```

### Reproducción original

Los tres comandos de §3 quedan en 200, 200/400 y 403/428 respectivamente; el de §3.1 deja de
mostrar `INTERNAL_ERROR` y el log de `identity-service` no repite `column "updated_at"` ni
`ClassCastException`. Evidencia: respuestas y log del arranque corregido.

## 7. Preguntas abiertas

Ninguna. La spec fija el DDL de `tipo_persona` sin `updated_at`, así que la corrección es retirar
esa escritura del SQL y no ampliar la tabla. El contrato publica el 428 y la regla de `documento`
exacto para EMPLEADO; el arreglo solo alinea el código con ellos. La decisión de entorno sobre las
credenciales de Eureka no aplica a este plan y se trata en `back-arreglo-hotel-catalogo-admin`.

## 8. Criterio de terminado

- [x] Todos los pasos de §4 marcados con su verificación.
- [x] `PUT /api/tipopersona/actualizar/{id}` responde 200 con versión incrementada y sin el error de
  `updated_at`.
- [x] Las lecturas y mutaciones de personas con EMPLEADO responden 200/400/403 según la spec, sin
  ningún 500 por `ClassCastException`.
- [x] `PUT`/`DELETE` de personas y `PUT` de tipo de persona sin `If-Match` responden 428
  `PRECONDITION_REQUIRED`.
- [x] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS` y
  `python scripts/validar-planes.py` con 0 errores.
- [x] La reproducción original de §3 está en verde con evidencia adjunta al PR y sin preguntas
  abiertas.
