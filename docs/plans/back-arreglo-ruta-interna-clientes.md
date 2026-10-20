# back-arreglo-ruta-interna-clientes — `GET /internal/clientes/{id}` responde en la ruta publicada (F2)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-ruta-interna-clientes` |
| Ámbito | `back` |
| Componente | `identity-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `propuesto` |
| Depende de | `back-identity-personas` |
| Repara | `back-identity-personas` |
| Spec | [`identity/api.md` § REST](../especificacion/microservicios/identity/api.md#rest), [`03-contrato-comun.md` § Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio), [`identity.yaml`](../contracts/openapi/identity.yaml) y [`06-migracion-y-pruebas.md` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

`GET http://localhost:8081/internal/clientes/{id}` con un token máquina de `reception-service` y
`audience=identity` responde `200` con el mismo cuerpo de `ClienteVigente` que hoy devuelve la ruta
con prefijo `/api`, y las rutas públicas de personas y tipos de persona siguen respondiendo donde
la spec las publica.

## 2. Alcance

**Entra:** la zona del plan reparado `back-identity-personas`, componente `identity-service`, en lo
que respecta al montaje de la lectura máquina de clientes:

- `services/identity-service/src/main/java/pe/hotel/identity/persona/PersonaController.java`: retirar
  de esta clase el handler de lectura máquina y el helper de scope que solo él usa, sin tocar las
  rutas humanas que ya cuelgan de `/api`.
- `services/identity-service/src/main/java/pe/hotel/identity/persona/ClienteInternoController.java`:
  controlador nuevo que monta `GET /internal/clientes/{id}` con el mismo `PersonaService`,
  la misma vista `ClienteVigenteView`, el mismo envelope y la misma comprobación de `clientes:read`.
- `services/identity-service/src/test/java/pe/hotel/identity/persona/`: prueba de enrutado del
  controlador interno (200 con scope, 403 sin scope, 404 en la ruta antigua).
- `docs/plans/back-arreglo-ruta-interna-clientes.md` y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- Cualquier cambio de ruta, método, código de respuesta, schema o `operationId` en
  `docs/contracts/**` y `docs/especificacion/**`. `identity.yaml` ya publica
  `GET /internal/clientes/{id}` y no se toca.
- Las rutas públicas de personas y tipos de persona: `GET /api/persona/listar`,
  `GET /api/persona/buscar/{id}`, `POST /api/persona/registrar`,
  `PUT /api/persona/actualizar/{id}`, `DELETE /api/persona/eliminar/{id}`,
  `GET /api/tipopersona/listar`, `GET /api/tipopersona/buscar/{id}`,
  `PUT /api/tipopersona/actualizar/{id}`, `POST /api/tipopersona/registrar` y
  `DELETE /api/tipopersona/eliminar/{id}` se quedan exactamente como están.
- `SecurityConfig` y la convención de seguridad: el matcher de las rutas internas ya exige token
  máquina y el endpoint conserva la comprobación de `clientes:read`. No se relaja ni se cambia
  ninguna audiencia, issuer o scope.
- Lógica de negocio, `PersonaService`, consultas, tablas, migraciones, eventos, outbox, seeds,
  `DemoDataSeeder`, `MachineTokenController`, `MachineTokenService` y la allowlist de clientes.
- `api-gateway`, `discovery-server`, `infra/**`, `.env`, las versiones fijadas (`services/pom.xml`,
  `apps/*/package.json`, el `README.md` raíz) y cualquier configuración de arranque.

## 3. Contexto

### La spec y el contrato publican la ruta sin `/api`

[`identity/api.md` § REST](../especificacion/microservicios/identity/api.md#rest) lista
`GET /internal/clientes/{id}` con token máquina de `reception` y scope `clientes:read`, y
[`identity.yaml`](../contracts/openapi/identity.yaml) publica esa misma ruta bajo la etiqueta
`interno`. El plan `back-identity-personas` también la declara así en sus pasos 9 y en su sección
de contratos afectados.

La convención del repositorio es la misma en los demás servicios: `HotelInternoController` monta
`/internal`, `ProductoInternoController` y `StockInternoController` montan `/internal/...`, y el
propio `MachineTokenController` de identity monta `/internal/auth`. Ningún servicio publica sus
rutas internas bajo `/api`.

### El defecto

`PersonaController` declara `@RequestMapping("/api")` a nivel de clase y el método de lectura
máquina declara `@GetMapping("/internal/clientes/{id}")`. La ruta efectiva queda con el prefijo
`/api` delante de la ruta del método, fuera de la que publican la spec y el contrato. `git blame`
sitúa ambas líneas en `4bca9e7` («feat: implementa el plan back-identity-personas (#70)»), el PR
#70 del plan `back-identity-personas`.

La decisión [`IA-back-arreglo-audiencia-maquina-02.md`](../implementacion/decisiones/IA-back-arreglo-audiencia-maquina-02.md)
registró el 404 como defecto de `back-identity-personas` y usó la ruta con prefijo `/api` solo
como comprobación temporal del arreglo de audiencia. Este plan es la corrección de ese defecto; no
cambia ninguna decisión de seguridad ni de audiencia.

### Reproducción literal (ejecutada desde `main`, 2026-10-19)

Con `identity-service` en `8081`, la allowlist sembrada y un token máquina de `reception-service`
con `audience=identity`:

```text
$ TOKEN=$(curl -s -u "reception-service:$SERVICE_CLIENT_RECEPTION_SECRET" \
    -d 'grant_type=client_credentials&audience=identity' \
    http://localhost:8081/internal/auth/token | ...)
$ # token obtenido, 606 chars

$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/internal/clientes/100
{"success":false,"message":"No encontramos lo que buscas.","data":null,"error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"1b3d8897-16d9-4f3f-ac5d-74996918a196"}}
HTTP 404

$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/api/internal/clientes/100
{"success":true,"message":"OK","data":{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","estado":true,"idTipoPersona":3,"version":1},"meta":{"correlationId":"75b35446-a71e-4b45-9738-fc8114608575"}}
HTTP 200
```

El 404 no es de seguridad: el token se acepta y la petición llega al controlador. Es de enrutado.
La ruta con prefijo `/api` no está en `identity.yaml`, no aparece en ningún consumidor del código
(`grep -R "api/internal" services apps` solo encuentra la bitácora y la evidencia del plan de
audiencia) y fue una comprobación temporal, no un contrato.

## 4. Pasos

- [ ] 1. Reproducir el fallo desde `main` con `identity-service` arrancado: obtener el token de
  `reception-service` con `audience=identity`, llamar a `GET /internal/clientes/{id}` y comprobar
  el 404, y llamar a la ruta con prefijo `/api` y comprobar el 200. Verificación: salidas literales
  de §3 guardadas en *Pruebas*.
- [ ] 2. Confirmar en la spec y en el contrato que la ruta publicada es sin `/api` y que la
  convención de los demás servicios también lo es:
  `grep -n "@RequestMapping" services/*/src/main/java/**/*Controller.java` debe mostrar
  `/internal` en hotel, inventory e identity (`/internal/auth`), sin ningún controlador interno
  montado bajo `/api`; y
  `grep -R "api/internal" services apps` no debe encontrar consumidores. Verificación: salidas
  anotadas en *Pruebas*.
- [ ] 3. Crear `ClienteInternoController` en `pe.hotel.identity.persona` con
  `@RestController` y `@RequestMapping("/internal")`, un `@GetMapping("/clientes/{id}")` que
  inyecte `PersonaService`, valide el token máquina con scope `clientes:read` y devuelva el mismo
  `ClienteVigenteView` dentro de `ApiResponse.ok(..., Meta.of(CorrelationId.current()))` que hoy
  devuelve el método de `PersonaController`. Verificación: la ruta del handler es
  `/internal/clientes/{id}` y el cuerpo compila.
- [ ] 4. Retirar de `PersonaController` el método de lectura máquina y el helper
  `validarTokenMaquinaConScope` si queda sin uso, sin tocar ninguna anotación de las rutas humanas
  ni el `@RequestMapping("/api")` de la clase. Verificación: `git diff` solo elimina el bloque
  interno; `GET /api/persona/listar` y las demás rutas humanas conservan su mapping.
- [ ] 5. Añadir una prueba de enrutado y seguridad del controlador interno con `MockMvc` y
  `SecurityMockMvcRequestPostProcessors.jwt()`: token máquina con `scope=clientes:read` →
  `200`; token máquina sin ese scope → `403 FORBIDDEN`; petición sin token → `401`; y la ruta
  antigua con prefijo `/api` → `404`. Verificación: la prueba pasa con
  `bash scripts/mvn.sh -f services/pom.xml -pl identity-service test`.
- [ ] 6. Ejecutar las pruebas del módulo completo:
  `bash scripts/mvn.sh -f services/pom.xml -pl identity-service test`. Verificación:
  `BUILD SUCCESS` y la nueva prueba en verde.
- [ ] 7. Ejecutar el build completo: `bash scripts/mvn.sh -f services/pom.xml verify`.
  Verificación: `BUILD SUCCESS` de los nueve módulos.
- [ ] 8. Rearrancar `identity-service` desde la rama corregida y repetir la reproducción:
  `GET /internal/clientes/{id}` con el token de `reception-service` y `audience=identity` debe
  responder `200` con el mismo cuerpo que devolvía la ruta con prefijo `/api` en el paso 1.
  Verificación: códigos y cuerpos guardados; el `data` de ambas respuestas es idéntico.
- [ ] 9. Comprobar que la ruta antigua deja de servir y que ningún consumidor dependía de ella:
  la ruta con prefijo `/api` responde `404` y el `grep` del paso 2 sigue sin consumidores.
  Verificación: código HTTP y salida del `grep`.
- [ ] 10. Comprobar la regresión de las rutas públicas y de la seguridad: sin token,
  `GET /api/persona/listar` sigue respondiendo `401 UNAUTHENTICATED` (no 404); con un JWT humano de
  ADMIN sigue respondiendo `200`; un token humano contra `/internal/clientes/{id}` responde `403`;
  un token máquina con un scope distinto responde `403`. Verificación: códigos guardados en
  *Pruebas*.
- [ ] 11. Ejecutar `python scripts/validar-planes.py` y
  `python scripts/validar-contratos.py --base origin/main`. Verificación: 0 errores y ningún
  contrato eliminado o renombrado.
- [ ] 12. Registrar en *Pruebas* la salida de los pasos 1, 2, 5, 6, 8, 9, 10 y 11, marcar los
  pasos y cerrar el plan. Verificación: todos los pasos `- [x]`, evidencia enlazada y *Preguntas
  abiertas* con `Ninguna`.

## 5. Contratos afectados

- OpenAPI: ninguno. `identity.yaml` ya publica `GET /internal/clientes/{id}` con
  `machineToken: [clientes:read]`; no se añade, cambia ni elimina ninguna ruta, schema,
  `operationId` ni código de respuesta. La ruta con prefijo `/api` no está en el contrato y deja
  de existir; no tiene consumidores en `services/` ni `apps/`.
- Eventos y comandos: ninguno. No se toca `identity.persona.v1`, el outbox ni ningún schema de
  `contracts/events/`.
- Persistencia: ninguna migración, tabla, función ni seed. `fn_cliente_vigente` y el acceso a datos
  se reutilizan sin cambios.
- Errores: se mantienen `UNAUTHENTICATED` (401), `FORBIDDEN` (403) y `RESOURCE_NOT_FOUND` (404) de
  [`errors.md`](../contracts/errors.md); no se añade ningún código.
- Seguridad: `SecurityConfig` no se toca. El matcher existente de las rutas internas sigue
  exigiendo un token máquina (autoridad `SCOPE_*`) y el controlador conserva la comprobación de
  `clientes:read`;
  un token humano sigue sin poder leer la ruta interna. No se cambia la audiencia ni el issuer.
- Configuración: ninguna propiedad nueva. El puerto `8081`, `spring.application.name` y la
  allowlist se quedan como están.

## 6. Pruebas

### Reproducción antes del arreglo (paso 1)

```text
$ TOKEN=$(curl -s -u "reception-service:$SERVICE_CLIENT_RECEPTION_SECRET" \
    -d 'grant_type=client_credentials&audience=identity' \
    http://localhost:8081/internal/auth/token | ...)
$ # token obtenido, 606 chars

$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/internal/clientes/100
{"success":false,"message":"No encontramos lo que buscas.","data":null,"error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"1b3d8897-16d9-4f3f-ac5d-74996918a196"}}
HTTP 404

$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/api/internal/clientes/100
{"success":true,"message":"OK","data":{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","estado":true,"idTipoPersona":3,"version":1},"meta":{"correlationId":"75b35446-a71e-4b45-9738-fc8114608575"}}
HTTP 200
```

### Reproducción después del arreglo (pasos 8, 9 y 10)

Resultado esperado, con el servicio rearrancado desde la rama corregida:

```text
$ curl -s -w '\nHTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/internal/clientes/100
{"success":true,"message":"OK","data":{"idPersona":100,"tipoDocumento":"DNI","documento":"90000100","nombre":"Cliente","apellido":"Uno","correo":"cliente.uno@hotel.test","estado":true,"idTipoPersona":3,"version":1},"meta":{"correlationId":"..."}}
HTTP 200

$ curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8081/api/internal/clientes/100
404

$ curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/persona/listar
401

$ curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $HUMAN_TOKEN" \
    http://localhost:8081/internal/clientes/100
403
```

- **Reproducción original:** el comando de §3 y de esta sección responde `200` con el `data` de
  `ClienteVigente` idéntico al que devolvía la ruta con prefijo `/api` antes del arreglo. Evidencia:
  salidas literales en este archivo.
- **Ruta antigua:** la ruta con prefijo `/api` responde `404`; `grep -R "api/internal" services
  apps` no encuentra consumidores. Evidencia: código HTTP y salida del `grep`.
- **Regresión pública:** `GET /api/persona/listar` sin token sigue en `401 UNAUTHENTICATED` y con
  JWT humano de ADMIN en `200`; las rutas de tipos de persona conservan su mapping. Evidencia:
  códigos HTTP y diff del controlador.
- **Seguridad:** token humano en la ruta interna → `403`; token máquina con scope distinto → `403`;
  sin token → `401`. Evidencia: respuestas y prueba `MockMvc`.
- **Unidad:** `bash scripts/mvn.sh -f services/pom.xml -pl identity-service test` → `BUILD SUCCESS`,
  con la prueba nueva en verde.
- **Build:** `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS` de los nueve
  módulos.
- **Validadores:** `python scripts/validar-planes.py` → 0 errores;
  `python scripts/validar-contratos.py --base origin/main` → sin cambios de contrato.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos de §4 marcados con su verificación.
- [ ] La reproducción original, `GET /internal/clientes/{id}` con el token de `reception-service`
  y `audience=identity`, responde `200` con el mismo `ClienteVigente` que devolvía la ruta con
  prefijo `/api`.
- [ ] La ruta con prefijo `/api` responde `404` y no queda ningún consumidor que la use.
- [ ] Las rutas públicas de personas y tipos de persona que cuelgan de `/api` y la convención de
  seguridad siguen sin cambios (401/403 donde correspondía).
- [ ] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`.
- [ ] `python scripts/validar-planes.py` termina con 0 errores y
  `python scripts/validar-contratos.py --base origin/main` no detecta cambios de contrato.
- [ ] Sin preguntas abiertas.
