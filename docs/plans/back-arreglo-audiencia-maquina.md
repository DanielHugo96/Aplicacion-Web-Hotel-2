# back-arreglo-audiencia-maquina — los validadores aceptan el destino corto del token máquina (F1)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-audiencia-maquina` |
| Ámbito | `back` |
| Componente | `services/*-service` |
| Fase | `F1` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-plataforma-comunes` |
| Repara | `back-plataforma-comunes` |
| Spec | [`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio), [`identity/api.md` § REST](../especificacion/microservicios/identity/api.md#rest), [`hotel/api.md` § Feign interno](../especificacion/microservicios/hotel/api.md#feign-interno), [`configuracion.md` § Por servicio](../especificacion/infraestructura/configuracion.md#por-servicio) y [`06-migracion-y-pruebas.md` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Los validadores JWT de los siete servicios aceptan el destino corto que identity emite en el token máquina (`aud=hotel`, `identity`, `reception`, `inventory`, `sales`, además de `hotel-api`), de modo que `GET /internal/hoteles/{id}` con el token de `inventory-service` responde 200 en vez de 401. Se comprueba repitiendo la reproducción de §3 y las llamadas máquina `inventory → hotel`, `identity → hotel` y `reception → identity`.

## 2. Alcance

**Entra:** los siete validadores de plataforma y sus pruebas unitarias, que son la zona del plan reparado (`back-plataforma-comunes`, componente `services/*-service`):

- `services/hotel-service/src/main/java/pe/hotel/hotel/platform/SecurityConfig.java` y `services/hotel-service/src/test/java/pe/hotel/hotel/platform/JwtValidatorTests.java`.
- `services/identity-service/src/main/java/pe/hotel/identity/platform/SecurityConfig.java` y `services/identity-service/src/test/java/pe/hotel/identity/platform/JwtValidatorTests.java`.
- `services/reception-service/src/main/java/pe/hotel/reception/platform/SecurityConfig.java` y `services/reception-service/src/test/java/pe/hotel/reception/platform/JwtValidatorTests.java`.
- `services/inventory-service/src/main/java/pe/hotel/inventory/platform/SecurityConfig.java` y `services/inventory-service/src/test/java/pe/hotel/inventory/platform/JwtValidatorTests.java`.
- `services/sales-service/src/main/java/pe/hotel/sales/platform/SecurityConfig.java` y `services/sales-service/src/test/java/pe/hotel/sales/platform/JwtValidatorTests.java`.
- `services/reporting-service/src/main/java/pe/hotel/reporting/platform/SecurityConfig.java` y `services/reporting-service/src/test/java/pe/hotel/reporting/platform/JwtValidatorTests.java`.
- `services/notification-service/src/main/java/pe/hotel/notification/platform/SecurityConfig.java` y `services/notification-service/src/test/java/pe/hotel/notification/platform/JwtValidatorTests.java`.
- `docs/plans/back-arreglo-audiencia-maquina.md`, la fila de este plan en `docs/plans/catalogo.md` y la bitácora de la decisión de nivel 2 en `docs/implementacion/decisiones/IA-back-arreglo-audiencia-maquina-01.md`.

**Fuera de alcance:**

- `identity-service` como emisor: `MachineTokenService`, `TokenIssuer`, `ServiceClientRepository`, `DemoDataSeeder`, la migración de `service_client` y la allowlist sembrada. La spec fija destinos cortos y el emisor ya los emite; no se cambian.
- `docs/contracts/**`, `docs/especificacion/**`, `docs/plans/README.md`, `docs/plans/_plantilla.md` y `docs/guia-git.md`.
- `api-gateway`, `discovery-server`, `infra/**`, las versiones fijadas (`services/pom.xml`, `apps/*/package.json`, `README.md` raíz) y cualquier configuración de Eureka o de arranque.
- Lógica de negocio, endpoints, eventos, scopes, códigos de error o migraciones nuevas. La allowlist, los scopes y los contratos se quedan como están.
- El trabajo pendiente de `back-inventory-stock` y de los demás planes bloqueados: este arreglo solo les devuelve las llamadas máquina.

## 3. Contexto

### La spec fija destinos cortos

[`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) fija la forma del token máquina: `POST /internal/auth/token` con `grant_type=client_credentials&audience=<destino>`, y el emisor devuelve `sub`=nombre del servicio emisor y `aud`=destino, con el scope limitado por audiencia. Los destinos que la misma sección usa al enumerar los scopes son cortos: `identity`, `hotel`, `sales`, `reception` e `inventory`.

[`identity.yaml`](../contracts/openapi/identity.yaml) lo concreta en `TokenRequest.audience`: «p. ej. hotel, reception, sales, inventory» y `example: hotel`. La allowlist sembrada por `back-identity-auth` (PR #61) sigue la misma convención:

```text
identity_db=# SELECT client_id, audiencias_scopes FROM service_client ORDER BY client_id;
hotel-service     | {"reception": ["bloqueos:write"]}
identity-service  | {"hotel": ["hoteles:read"]}
inventory-service | {"hotel": ["hoteles:read"]}
reception-service | {"hotel": ["habitaciones:read"], "sales": ["cierres:write"], "identity": ["clientes:read"]}
sales-service     | {"inventory": ["stock:read", "stock:debit"], "reception": ["recepciones:read", "operaciones:write"]}
```

Por tanto, el emisor y la allowlist **no** están mal: el defecto está en los validadores, que comparan `aud` contra `spring.application.name` (`hotel-service`, `identity-service`, etc.) en vez de contra el destino corto que la spec fija.

### Reproducción literal

Con `identity-service` (8081) y `hotel-service` (8082) arrancados desde `main`, y la allowlist anterior:

```text
$ curl -s -u "inventory-service:$SERVICE_CLIENT_INVENTORY_SECRET" \
    -d 'grant_type=client_credentials&audience=hotel' \
    http://localhost:8081/internal/auth/token
{"access_token":"eyJ...","token_type":"Bearer","expires_in":300}

# payload del token (decodificado):
{"sub":"inventory-service","aud":"hotel","scope":"hoteles:read","iss":"hotel-identity","exp":...}

$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8082/internal/hoteles/1
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,
 "error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"..."}}
HTTP 401

$ curl -s -u "inventory-service:$SERVICE_CLIENT_INVENTORY_SECRET" \
    -d 'grant_type=client_credentials&audience=hotel-service' \
    http://localhost:8081/internal/auth/token
{"success":false,"message":"Revisa los datos marcados.","data":null,
 "error":{"code":"VALIDATION_ERROR","fields":{"audience":"destino no autorizado para este cliente"}},
 "meta":{"correlationId":"..."}}
HTTP 400
```

El mismo fallo se reproduce en el resto de llamadas máquina, no solo en hotel:

```text
# identity -> hotel, también con aud=hotel
$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8082/internal/hoteles/1
HTTP 401 {"error":{"code":"UNAUTHENTICATED"}}

# reception -> identity, con aud=identity
$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/internal/clientes/100
HTTP 401 {"error":{"code":"UNAUTHENTICATED"}}
```

### Origen del defecto

El patrón está en los siete servicios, en el validador de plataforma:

```text
services/hotel-service/.../platform/SecurityConfig.java:123
    Set<String> audiences = Set.of(HUMAN_AUDIENCE, serviceName);

services/identity-service/.../platform/SecurityConfig.java:141
services/reception-service/.../platform/SecurityConfig.java:120
services/inventory-service/.../platform/SecurityConfig.java:120
services/sales-service/.../platform/SecurityConfig.java:120
services/reporting-service/.../platform/SecurityConfig.java:120
services/notification-service/.../platform/SecurityConfig.java:120
    Set<String> audiences = Set.of(HUMAN_AUDIENCE, serviceName);
```

`spring.application.name` es `hotel-service`, `identity-service`, etc. El validador acepta el nombre largo y rechaza el destino corto que identity emite. `git blame` sitúa esa línea en `ea2defe` («aplica la convención de plataforma común en los siete servicios»), el commit de `back-plataforma-comunes` (PR #46). `back-hotel-catalogo` (PR #66) y `back-identity-auth` (PR #61) solo consumieron la convención; el plan que posee la zona rota es `back-plataforma-comunes`.

### Por qué un solo plan

La corrección es una sola: añadir el destino corto al conjunto de audiencias aceptadas en la convención de plataforma. Reparar solo `hotel-service` desbloquearía `back-inventory-stock`, pero dejaría el mismo 401 en `identity`, `reception`, `sales`, `inventory`, `reporting` y `notification`, y obligaría a un segundo plan de arreglo para el mismo fallo. La zona del plan reparado es exactamente `services/*-service` (componente de `back-plataforma-comunes`), así que este plan la corrige completa, sin lógica de negocio y sin tocar el emisor ni la allowlist. Es la lectura de [`README.md` §6.11.5](README.md#611-ejecución-por-ia-campaña-automática) que evita siete planes para una sola convención.

## 4. Pasos

- [x] 1. Reproducir el fallo desde `main`: arrancar `identity-service` y `hotel-service`, obtener el token de `inventory-service` con `audience=hotel`, llamar a `GET /internal/hoteles/{id}` y comprobar el 401; pedir `audience=hotel-service` y comprobar el 400. Verificación: salidas literales de §3 guardadas en la evidencia.
- [x] 2. Confirmar en la spec que el destino es el nombre corto (`03-contrato-comun.md` § Servicio a servicio y `identity.yaml` `TokenRequest.audience`) y escribir la decisión de nivel 2 en `docs/implementacion/decisiones/IA-back-arreglo-audiencia-maquina-01.md`: se corrige el validador, no la allowlist, y se mantiene el nombre largo como compatibilidad aditiva. Verificación: el archivo existe, cita las dos fuentes y explica por qué no se toca `DemoDataSeeder`.
- [x] 3. Corregir `services/hotel-service/src/main/java/pe/hotel/hotel/platform/SecurityConfig.java`: el conjunto `audiences` debe contener la audiencia humana (`hotel-api`), `spring.application.name` y el destino corto derivado de `spring.application.name` quitando el sufijo `-service` (`hotel-service` → `hotel`). Actualizar el Javadoc para citar el destino corto. Verificación: `JwtValidatorTests` de hotel acepta `aud=hotel` y sigue rechazando `aud=otro-servicio`.
- [x] 4. Aplicar la misma corrección en `services/identity-service/.../platform/SecurityConfig.java` y actualizar su Javadoc. Verificación: `JwtValidatorTests` de identity acepta `aud=identity` y rechaza una audiencia ajena.
- [x] 5. Aplicar la misma corrección en `reception-service`, `inventory-service`, `sales-service`, `reporting-service` y `notification-service` (cinco `SecurityConfig`). Verificación: cada validador contiene el destino corto derivado de `spring.application.name` y el patrón antiguo ya no aparece:

```bash
grep -R "Set.of(HUMAN_AUDIENCE, serviceName)" services/*/src/main/java
# sin coincidencias
```
- [x] 6. Actualizar las siete clases `JwtValidatorTests` para cubrir: `hotel-api` aceptado, destino corto aceptado, nombre largo aceptado (compatibilidad), `otro-servicio` rechazado e issuer distinto rechazado. Verificación: `bash scripts/mvn.sh -f services/pom.xml -pl identity-service,hotel-service,reception-service,inventory-service,sales-service,reporting-service,notification-service test` termina en `BUILD SUCCESS`.
- [x] 7. Ejecutar el build completo: `bash scripts/mvn.sh -f services/pom.xml verify`. Verificación: `BUILD SUCCESS` de los nueve módulos.
- [x] 8. Repetir la reproducción: token de `inventory-service` con `audience=hotel` → `GET /internal/hoteles/{id}` responde 200; token de `identity-service` con `audience=hotel` → 200; token de `reception-service` con `audience=identity` → `GET /internal/clientes/{id}` responde 200. Verificación: códigos HTTP y cuerpos guardados en la evidencia.
- [x] 9. Comprobar que no se relaja la seguridad: `audience=hotel-service` sigue devolviendo 400 en identity (la allowlist no cambia) y un token con `aud` ajena sigue devolviendo 401 en hotel e identity, incluido el caso `SEC06`. Verificación: respuestas 400/401 guardadas y `JwtValidatorTests` en verde.
- [x] 10. Ejecutar `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`. Verificación: 0 errores y ningún contrato eliminado o renombrado.
- [x] 11. Registrar en *Pruebas* la salida de los comandos de los pasos 1, 8 y 9, marcar los pasos y cerrar el plan. Verificación: todos los pasos `- [x]`, evidencia enlazada y *Preguntas abiertas* con `Ninguna`.

## 5. Contratos afectados

- OpenAPI: ninguno. `identity.yaml` ya documenta `audience=<destino>` con `hotel` como ejemplo; no se añade ni cambia ningún schema, ruta u `operationId`.
- Eventos y comandos: ninguno.
- Persistencia: ninguna migración ni cambio de seed. `service_client` se queda con los destinos cortos que ya tiene; no se toca `V4__identity_personas.sql` ni `DemoDataSeeder`.
- Errores: se mantienen `UNAUTHENTICATED` (401) y `VALIDATION_ERROR` (400) de `errors.md`; no se añaden códigos.
- Configuración: no se añade ninguna propiedad. El destino corto se deriva de `spring.application.name`, que ya es la fuente del nombre en Eureka. No se cambia `spring.application.name` en ningún servicio.
- Seguridad: el validador acepta una audiencia más (el destino corto) y mantiene el nombre largo y `hotel-api`; cualquier otra `aud` sigue rechazada. `SEC06` no cambia.

## 6. Pruebas

### Reproducción antes del arreglo (paso 1)

Ejecutado el 2026-10-19 con `identity-service` en `8081` y `hotel-service` en `8082`, ambos
arrancados desde esta rama (idéntica a `main`, sin cambios de código todavía), y la allowlist
sembrada por `back-identity-auth`.

```text
$ curl -s -u "inventory-service:$SERVICE_CLIENT_INVENTORY_SECRET" \
    -d 'grant_type=client_credentials&audience=hotel' \
    http://localhost:8081/internal/auth/token
(token obtenido, 601 chars)
$ payload del token (decodificado):
{"sub": "inventory-service", "aud": "hotel", "scope": "hoteles:read", "iss": "hotel-identity"}
$ curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
    http://localhost:8082/internal/hoteles/1
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,"error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"0f361f12-1b7d-4706-876d-0a5c0965d275"}}
HTTP 401
$ curl -s -w "\nHTTP %{http_code}\n" -u "inventory-service:$SERVICE_CLIENT_INVENTORY_SECRET" \
    -d 'grant_type=client_credentials&audience=hotel-service' \
    http://localhost:8081/internal/auth/token
{"success":false,"message":"Revisa los datos marcados.","data":null,"error":{"code":"VALIDATION_ERROR","fields":{"audience":"destino no autorizado para este cliente"}},"meta":{"correlationId":"9b4f44e4-a108-4b38-af51-47a17bc26a32"}}
HTTP 400
$ # identity -> hotel con aud=hotel
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,"error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"7385cca9-b36a-4dd1-bb08-a8935b3669fb"}}
HTTP 401
$ # reception -> identity con aud=identity
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,"error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"df6d13e2-9999-465d-ae26-ca3c0d911c22"}}
HTTP 401
```

Salida completa guardada en `/tmp/repro-before.txt` de la sesión de ejecución; reproducida
literalmente arriba.

### Pruebas unitarias de los siete validadores (paso 6)

```text
$ bash scripts/mvn.sh -f services/pom.xml -pl identity-service,hotel-service,reception-service,inventory-service,sales-service,reporting-service,notification-service test
...
[INFO] Reactor Summary for identity-service 0.0.1-SNAPSHOT:
[INFO] identity-service ................................... SUCCESS [ 34.485 s]
[INFO] hotel-service ...................................... SUCCESS [  9.288 s]
[INFO] reception-service .................................. SUCCESS [  7.984 s]
[INFO] inventory-service .................................. SUCCESS [  7.639 s]
[INFO] sales-service ...................................... SUCCESS [  7.518 s]
[INFO] reporting-service .................................. SUCCESS [  6.549 s]
[INFO] notification-service ............................... SUCCESS [ 13.630 s]
[INFO] BUILD SUCCESS

# JwtValidatorTests de cada módulo (5 casos: hotel-api, destino corto, nombre largo, otro-servicio, issuer distinto)
identity        Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
hotel           Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
reception       Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
inventory       Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
sales           Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
reporting       Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
notification    Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

### Build completo (paso 7)

```text
$ bash scripts/mvn.sh -f services/pom.xml verify
[INFO] Reactor Summary for Hotel services (agregador) 0.0.1-SNAPSHOT:
[INFO] Hotel services (agregador) ......................... SUCCESS [  0.002 s]
[INFO] identity-service ................................... SUCCESS [ 30.754 s]
[INFO] hotel-service ...................................... SUCCESS [  7.110 s]
[INFO] reception-service .................................. SUCCESS [  5.731 s]
[INFO] inventory-service .................................. SUCCESS [  6.600 s]
[INFO] sales-service ...................................... SUCCESS [  6.137 s]
[INFO] reporting-service .................................. SUCCESS [  5.992 s]
[INFO] notification-service ............................... SUCCESS [ 12.112 s]
[INFO] api-gateway ........................................ SUCCESS [ 41.189 s]
[INFO] discovery-server ................................... SUCCESS [ 14.878 s]
[INFO] BUILD SUCCESS
```

### Reproducción después del arreglo (paso 8)

Servicios rearrancados desde esta rama con el código corregido (`identity-service` en `8081`,
`hotel-service` en `8082`).

```text
### inventory -> hotel, aud=hotel
$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8082/internal/hoteles/1
payload: {"sub": "inventory-service", "aud": "hotel", "scope": "hoteles:read", "iss": "hotel-identity"}
{"success":true,"message":"OK","data":{"idHotel":1,"nombre":"Hotel Demo Lima Centro","estado":true,"version":1},"meta":{"correlationId":"afda0fc6-34d2-44e4-b3b3-24635d1f2fbd"}}
HTTP 200

### identity -> hotel, aud=hotel
{"success":true,"message":"OK","data":{"idHotel":1,"nombre":"Hotel Demo Lima Centro","estado":true,"version":1},"meta":{"correlationId":"53cb1e0f-34f1-40a2-bc76-0cd15b82d54e"}}
HTTP 200

### reception -> identity, aud=identity
payload: {"sub": "reception-service", "aud": "identity", "scope": "clientes:read", "iss": "hotel-identity"}
$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/internal/clientes/{id}
{"success":false,"message":"No encontramos lo que buscas.","data":null,"error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"3f49d52c-e79d-4ff7-8160-8f4fb2c933eb"}}
HTTP 404
# La ruta publicada en el contrato está montada en identity bajo `/api` (defecto de
# back-identity-personas, fuera de la zona de este arreglo; ver IA-back-arreglo-audiencia-maquina-02).
$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/internal/clientes/{id}
{"success":true,"message":"OK","data":{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","estado":true,"idTipoPersona":3,"version":1},"meta":{"correlationId":"f3f26685-7a6e-4931-a549-930f2e0dfaca"}}
HTTP 200
```

El token de `reception` con `aud=identity` ya no recibe 401: el validador lo acepta y la petición
llega al controlador. El 404 es de enrutado (`PersonaController` cuelga de `/api`), no de
audiencia; queda registrado para un arreglo de `back-identity-personas` y no se presenta como 200
en la ruta del contrato.

### Controles de seguridad (paso 9)

```text
$ # allowlist intacta: identity sigue rechazando audience=hotel-service
$ curl -s -w '\nHTTP %{http_code}\n' -u "inventory-service:$SERVICE_CLIENT_INVENTORY_SECRET" \
    -d 'grant_type=client_credentials&audience=hotel-service' \
    http://localhost:8081/internal/auth/token
{"success":false,"message":"Revisa los datos marcados.","data":null,"error":{"code":"VALIDATION_ERROR","fields":{"audience":"destino no autorizado para este cliente"}},"meta":{"correlationId":"83dfb9ae-f3d0-43fb-8ff4-30080bf3180f"}}
HTTP 400

$ # aud ajena: token con aud=hotel contra identity
$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/internal/clientes/{id}
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,"error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"6c5681df-eba7-4dd0-9402-b6d66f658003"}}
HTTP 401

$ # aud ajena: token con aud=identity contra hotel
$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8082/internal/hoteles/1
{"success":false,"message":"Tu sesión expiró. Vuelve a iniciar sesión.","data":null,"error":{"code":"UNAUTHENTICATED","fields":{}},"meta":{"correlationId":"13dfe83c-7874-493f-bdbc-d6699197fb4a"}}
HTTP 401
```

Los siete `JwtValidatorTests` cubren además `otro-servicio` e issuer distinto (5 casos por módulo,
en verde en la evidencia del paso 6). Salida de esta reproducción guardada en
`/tmp/repro-after.txt` de la sesión de ejecución.

### Validadores (paso 10)

```text
$ python scripts/validar-planes.py
27 plan(es) revisado(s); 27 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```

- **Reproducción antes del arreglo:** los comandos de §3; resultado esperado `401 UNAUTHENTICATED` en hotel e identity y `400 VALIDATION_ERROR` al pedir `audience=hotel-service`. Evidencia: salidas literales en esta sección y en la bitácora.
- **Reproducción después del arreglo:** mismos comandos; resultado esperado `200` en `GET /internal/hoteles/{id}` con `aud=hotel`, `200` en `GET /internal/clientes/{id}` con `aud=identity`, y `400`/`401` en los controles negativos. Evidencia: códigos y cuerpos guardados por la ejecutora.
- **Unidad:** `JwtValidatorTests` de los siete módulos, con los cinco casos del paso 6. Evidencia: salida de `bash scripts/mvn.sh -f services/pom.xml ... test`.
- **Build:** `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS`. Evidencia: salida del comando.
- **Regresión `SEC06`:** token con `aud` ajena o firma/issuer distinto → `401` en cada servicio; no se acepta por defecto. Evidencia: pruebas unitarias y, si el entorno está levantado, una llamada con `aud=otro-servicio`.
- **Validadores:** `python scripts/validar-planes.py` → 0 errores; `python scripts/validar-contratos.py --base origin/main` → sin cambios de contrato.

## 7. Preguntas abiertas

Ninguna (resuelto por IA: la spec fija destinos cortos; se corrigen los validadores de forma aditiva y se mantiene la allowlist y el emisor — [`IA-back-arreglo-audiencia-maquina-01.md`](../implementacion/decisiones/IA-back-arreglo-audiencia-maquina-01.md) —. La ruta interna de clientes está montada bajo `/api` por un defecto de `back-identity-personas`, fuera de la zona de este arreglo: se verifica en su ruta real y queda registrada en [`IA-back-arreglo-audiencia-maquina-02.md`](../implementacion/decisiones/IA-back-arreglo-audiencia-maquina-02.md)).

## 8. Criterio de terminado

- [x] Todos los pasos de §4 marcados con su verificación.
- [x] Los siete validadores aceptan el destino corto y sus pruebas unitarias están en verde.
- [x] La reproducción original (`GET /internal/hoteles/{id}` con `aud=hotel`) responde 200.
- [x] `audience=hotel-service` sigue devolviendo 400 y una `aud` ajena sigue devolviendo 401.
- [x] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`.
- [x] `python scripts/validar-planes.py` termina con 0 errores.
- [x] Sin preguntas abiertas.
