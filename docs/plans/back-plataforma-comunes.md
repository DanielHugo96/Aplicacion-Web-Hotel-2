# Plan `back-plataforma-comunes`

| Campo | Valor |
|---|---|
| Plan | `back-plataforma-comunes` |
| Ámbito | `back` |
| Componente | los siete servicios (`services/*-service`) |
| Fase | `F1` |
| Modo | `directo` |
| Estado | `cerrado` |
| Change | `—` |
| Depende de | `infra-compose` |
| Casos | `SEC06` |
| Contenido | convención aplicada en cada servicio: resource server JWT RS256 (iss, aud, exp, scope), envelope y códigos genéricos, correlationId, patrón de idempotencia, /actuator/health y logs estructurados. |
| Spec | [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) · [§ Respuestas](../especificacion/03-contrato-comun.md) · [§ Identidad y sedes](../especificacion/03-contrato-comun.md) · [§ Servicio a servicio](../especificacion/03-contrato-comun.md) · [§ Persistencia transversal](../especificacion/03-contrato-comun.md) · [§ Salud, readiness y resiliencia](../especificacion/03-contrato-comun.md) · [errors.md § Genéricos](../contracts/errors.md) · [01-arquitectura.md §1 Monorepo](../especificacion/01-arquitectura.md) · [01-arquitectura.md §4 Stack](../especificacion/01-arquitectura.md) · [configuracion.md § Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md) · [configuracion.md § Health y readiness por servicio](../especificacion/infraestructura/configuracion.md) · [06-migracion-y-pruebas.md §5 SEC06](../especificacion/06-migracion-y-pruebas.md) · [notification/api.md § Seguridad y operación](../especificacion/microservicios/notification/api.md) · [identity/api.md § token máquina](../especificacion/microservicios/identity/api.md) |

Estructura según [`_plantilla.md`](_plantilla.md); valores de cabecera según la fila `back-plataforma-comunes` de [`catalogo.md` §1](catalogo.md#1-planes).

## 1 Objetivo

Aplicar la misma convención de plataforma en los siete servicios (`identity-service`, `hotel-service`, `reception-service`, `inventory-service`, `sales-service`, `reporting-service`, `notification-service`) sin implementar lógica de negocio de ninguno, de forma que cada servicio valide identidad solo por JWT, devuelva el envelope común, propague correlación, aplique el patrón de idempotencia, exponga salud y emita logs estructurados.

Es verificable cuando: cada servicio responde `401` ante JWT con firma ajena o `aud` incorrecta (caso `SEC06` de [06-migracion-y-pruebas.md §5](../especificacion/06-migracion-y-pruebas.md)); `GET /actuator/health/liveness` y `GET /actuator/health/readiness` responden según [03-contrato-comun.md § Salud](../especificacion/03-contrato-comun.md); y una mutación de prueba con `Idempotency-Key` repetida devuelve el resultado terminal sin repetir efectos según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md).

## 2 Alcance

Incluye, solo como convención común sin negocio:

- Resource server JWT RS256 con validación explícita de `iss`, `aud`, `exp` y `scope` según [03-contrato-comun.md § Identidad y sedes](../especificacion/03-contrato-comun.md) y [§ Servicio a servicio](../especificacion/03-contrato-comun.md); configuración base según [configuracion.md § Comunes](../especificacion/infraestructura/configuracion.md).
- Envelope `success/message/data/meta` y códigos genéricos del catálogo según [03-contrato-comun.md § Respuestas](../especificacion/03-contrato-comun.md) y [errors.md § Genéricos](../contracts/errors.md).
- `X-Correlation-Id` (aceptar, generar si falta, propagar y responder) según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md).
- Patrón de idempotencia con `Idempotency-Key` y tabla `api_idempotency` según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) y [§ Persistencia transversal](../especificacion/03-contrato-comun.md).
- `GET /actuator/health/liveness` y `GET /actuator/health/readiness` según [03-contrato-comun.md § Salud](../especificacion/03-contrato-comun.md) y [configuracion.md § Health](../especificacion/infraestructura/configuracion.md).
- Logs estructurados con `service`, `correlationId` y `eventId`/`commandId` según [configuracion.md § Comunes](../especificacion/infraestructura/configuracion.md).

No incluye:

- Funcionalidad de ningún servicio (sin altas, check-in, ventas, stock, reportes ni notificaciones).
- Mensajería: cero topics, comandos, colas o grupos de consumidor para este plan (la asignación exacta vive en [05-mensajeria.md](../especificacion/05-mensajeria.md); este plan no produce ni consume mensajes).
- Versiones distintas de las fijadas en [01-arquitectura.md §4](../especificacion/01-arquitectura.md): Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1 y RabbitMQ 4.3.6.
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.
- Identidad por cabeceras `X-User-Id` / `X-Role` / `X-Hotel-Id`: prohibido; solo JWT validado según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) y [docs/plans/README.md](../plans/README.md).

## 3 Contexto

Este plan depende de `infra-compose` (Compose, PostgreSQL, Kafka/RabbitMQ y descubrimiento/puerta disponibles y sanos) y se ejecuta en `F1` en modo `directo`. La estructura objetivo de `services/` (agregador Maven, un directorio por servicio, `src/main/resources/db/migration/` propio por servicio) es la de [01-arquitectura.md §1](../especificacion/01-arquitectura.md).

Cada servicio valida el JWT emitido por identity (`iss=hotel-identity`, `aud=hotel-api`, `exp`, `scope`) y el Gateway elimina cabeceras de identidad falsificables, según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) y [§ Identidad y sedes](../especificacion/03-contrato-comun.md). Lo relativo a `/actuator/health` y seguridad de los `api.md` se limita a: salud y operación de notification en [notification/api.md](../especificacion/microservicios/notification/api.md), token máquina en [identity/api.md](../especificacion/microservicios/identity/api.md), y referencias a headers/idempotencia y `scope` en los `api.md` de hotel, reception, inventory y sales. Este plan no toca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

Decisiones de este plan (lo que la spec no fijaba):

- Logs: una línea JSON por evento con `service`, `correlationId` y `eventId`/`commandId` cuando aplique.
- Salud: cuerpo JSON estándar de Spring Boot Actuator tal cual, sin envolverlo en el envelope común.
- Rotación RS256: solape de `kid` (la clave nueva convive en el JWKS con la anterior hasta migrar los servicios; luego se retira la anterior).
- Correlación: filtro común en cada servicio que genera `X-Correlation-Id` si falta y lo propaga a las llamadas Feign, con y sin Gateway.
- Idempotencia: job diario que purga filas COMPLETADA con más de 30 días, conservando las claves de venta/stock/cierre con el historial.

Decisiones tomadas al ejecutar (la spec no las fijaba):

- **Migrador de Flyway.** La spec exige usuario migrador separado del runtime ([`03` § Persistencia transversal](../especificacion/03-contrato-comun.md)) pero no nombra sus variables. Este plan usa SPRING_FLYWAY_USER/SPRING_FLYWAY_PASSWORD (opcionales; si faltan heredan el datasource runtime) para que Flyway cree el esquema con el rol `<servicio>_migrator`. La notificación de estos dos nombres en `.env.example` y `configuracion.md` queda para el plan de infraestructura, porque este plan solo toca `services/*-service/**` (norma §6.4).
- **Componente de idempotencia reutilizable.** Sin endpoints de negocio en este plan, el patrón se implementa como filtro/servicio común (`IdempotencyFilter`, `ApiIdempotencyRepository`) que exige la clave en las mutaciones POST/PUT/DELETE de negocio salvo login/token/social y persiste el resultado terminal; cada plan de dominio solo devuelve su respuesta. La migración `V1__api_idempotency.sql` crea la tabla en las siete bases.
- **Pruebas sin base de datos en la CI.** La CI solo corre `mvn verify` sin PostgreSQL ([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)). Las pruebas de contexto desactivan Flyway y la auto-configuración JPA (`spring.autoconfigure.exclude`) para validar el cableado sin exigir un servidor; la validación de esquema y la salud con base real se comprueban en la ejecución local contra el Compose.

## 4 Pasos

- [x] 1. Confirmar la dependencia `infra-compose` disponible (Compose sano, PostgreSQL acepta conexiones, `.env.example` solo con nombres y valores no sensibles). Verificación: `docker compose -f infra/compose.yaml ps` muestra servicios de infra en estado sano y `grep -R "password\|secret" .env.example` no devuelve secretos reales.
- [x] 2. Crear la estructura base de los siete servicios bajo `services/` con agregador Maven y migraciones propias por servicio, sin entidades ni reglas compartidas entre dominios. Verificación: existen `services/pom.xml` y `services/*/src/main/resources/db/migration/` para los siete servicios y `grep -R "ddl-auto" services/*/src/main/resources/application.yml` devuelve `validate`.
- [x] 3. Configurar en cada servicio el resource server JWT RS256 con `issuer-uri` fijo y `jwk-set-uri` explícito, sin descubrimiento OIDC. Verificación: `grep -R "issuer-uri\|jwk-set-uri" services/*/src/main/resources/application.yml` muestra `hotel-identity` y la URL de JWKS en los siete servicios.
- [x] 4. Aplicar denegación por defecto y autorización desde el JWT validado (rol, sede, propiedad), sin leer `X-User-Id`, `X-Role`, `X-Hotel-Id` ni `idPersona` del cuerpo para autorizar. Verificación: `grep -R "X-User-Id\|X-Role\|X-Hotel-Id" services/*/src/main/java` no devuelve coincidencias y la revisión muestra uso del principal JWT en cada servicio.
- [x] 5. Dejar configurado el mecanismo de token máquina `POST /internal/auth/token` (Basic por TLS, `audience` de la allowlist, TTL 5min, `scope` limitado por audiencia) sin exponerlo por el Gateway. Verificación: la configuración de SERVICE_CLIENT_ID/SERVICE_CLIENT_SECRET existe solo en servicios que llaman rutas /internal/** y una llamada con token humano a una ruta /internal/** responde `403 FORBIDDEN`.
- [x] 6. Aplicar el envelope común (`success/message/data/meta`, listas con `page,size,totalElements`, `204` sin cuerpo) en cada servicio. Verificación: una respuesta de prueba contiene las claves `success`, `message`, `data` y `meta.correlationId`, y `curl -s <servicio>/api/<recurso> | python -c "import json,sys; d=json.load(sys.stdin); assert 'meta' in d and 'correlationId' in d['meta']"` termina con código 0.
- [x] 7. Aplicar solo códigos genéricos del catálogo para los fallos comunes de este plan (`VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `PRECONDITION_FAILED`, `PRECONDITION_REQUIRED`, `IDEMPOTENCY_CONFLICT`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`, más `RATE_LIMITED`/`PAYLOAD_TOO_LARGE` donde la spec los asigna). Verificación: `grep -R "error.code" services/*/src/main/java` solo referencia códigos presentes en `docs/contracts/errors.md § Genéricos` y la UI usa el mensaje del catálogo.
- [x] 8. Aplicar `X-Correlation-Id` (aceptar UUID del cliente, generar si falta, propagar y devolver en `meta` y en respuestas). Verificación: `curl -s -D - <servicio>/actuator/health/liveness -o /dev/null | grep -i X-Correlation-Id` devuelve el identificador y dos llamadas sin cabecera devuelven identificadores distintos.
- [x] 9. Aplicar el patrón de idempotencia (`Idempotency-Key` UUID en POST/PUT/DELETE de negocio salvo login/token, clave única por actor/método/plantillaRuta/key, hash persistido, `202` en curso, `409 IDEMPOTENCY_CONFLICT` ante mismo key con distinto request, retención mínima 30 días, HMAC cuando hay credenciales). Verificación: repetir la misma mutación con la misma clave devuelve mismo `status`/cuerpo sin duplicar efecto y con clave distinta pero mismo cuerpo crea un recurso distinto; `SELECT count(*) FROM api_idempotency` refleja ambas intenciones.
- [x] 10. Exponer `GET /actuator/health/liveness` (200 si el proceso vive, sin dependencias) y `GET /actuator/health/readiness` (200 solo con su DB arriba) en cada servicio, sin exponer /actuator/** por el Gateway. Verificación: `curl -sf http://localhost:<puerto>/actuator/health/liveness` devuelve 200 en los siete servicios y `curl -s <gateway>/actuator/health/liveness` no enruta (404).
- [x] 11. Reflejar dependencias críticas como degradación visible (`DOWN`/OUT_OF_SERVICE) sin habilitar negocio que la necesite (`503 DEPENDENCY_UNAVAILABLE`). Verificación: con la DB detenida, `readiness` deja de dar 200; con Kafka/Rabbit detenidos según la tabla de [configuracion.md § Health](../especificacion/infraestructura/configuracion.md), el health muestra degradación y la operación que la requiere responde `503 DEPENDENCY_UNAVAILABLE`.
- [x] 12. Aplicar logs estructurados con `service`, `correlationId` y `eventId`/`commandId` cuando aplique, sin claves, hashes, tokens, documento ni correo, y sin `stacktrace` ni SQL en respuestas. Verificación: `grep -R "correlationId" services/*/src/main/resources/logback*.xml services/*/src/main/java | head` muestra el campo en los siete servicios y una muestra de log contiene `service` y `correlationId` sin secretos.
- [x] 13. Fijar solo versiones y valores por defecto de la spec (stack del §4, timeouts Feign 2s/5s, circuit breaker 10 llamadas/50%/mínimo 5/10s/3 en half-open, outbox 5s/50 filas, inbox 30 días). Verificación: `grep -R "latest\|SNAPSHOT" services/pom.xml` no devuelve coincidencias y `mvn -q -f services/pom.xml validate` termina con código 0.
- [x] 14. Ejecutar la comprobación `SEC06` en cada servicio (JWT con firma ajena o `aud` incorrecta ante `GET /api/auth/me` y `GET /api/hotel/listar`). Verificación: cada servicio responde `401 UNAUTHENTICATED` y la evidencia queda registrada por servicio (respuestas guardadas, sin datos filtrados).

## 5 Contratos afectados

- Forma del envelope y estados genéricos según [03-contrato-comun.md § Respuestas](../especificacion/03-contrato-comun.md); códigos, HTTP y acción esperada según [errors.md § Genéricos](../contracts/errors.md). Este plan no crea códigos nuevos ni cambia mensajes de UI.
- Cabeceras `Authorization: Bearer`, `X-Correlation-Id`, `Idempotency-Key`, `If-Match`/`ETag` y `Retry-After` según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md); sin `X-User-Id`/`X-Role`/`X-Hotel-Id`.
- Salud `GET /actuator/health/liveness` y `GET /actuator/health/readiness` según [03-contrato-comun.md § Salud](../especificacion/03-contrato-comun.md) y [configuracion.md § Health](../especificacion/infraestructura/configuracion.md); el Gateway no expone /actuator/**.
- Tablas técnicas usadas solo en su forma común (`api_idempotency`, sin negocio) según [03-contrato-comun.md § Persistencia transversal](../especificacion/03-contrato-comun.md).
- Ningún contrato OpenAPI de negocio, ningún `envelope.v1` de eventos y ningún comando RabbitMQ se crea ni se modifica en este plan; mensajería: cero topics, comandos y grupos asignados a este plan.

## 6 Pruebas

- `SEC06` de [06-migracion-y-pruebas.md §5](../especificacion/06-migracion-y-pruebas.md): con JWT de firma ajena o `aud` incorrecta, `GET /api/auth/me` y `GET /api/hotel/listar` deben dar `401 UNAUTHENTICATED` en cada servicio; evidencia: respuestas HTTP por servicio.
- Salud: `GET /actuator/health/liveness` da 200 sin dependencias; `GET /actuator/health/readiness` da 200 solo con su DB arriba; dependencia caída que el negocio necesite da `503 DEPENDENCY_UNAVAILABLE`; evidencia: salidas de `curl` por servicio.
- Humo de convención: envelope con `meta.correlationId` presente; `X-Correlation-Id` propagado; reintento con la misma `Idempotency-Key` devuelve el resultado terminal (`202` si está en curso, mismo cuerpo si completó) y con distinto cuerpo da `409 IDEMPOTENCY_CONFLICT`; evidencia: respuestas y conteos en `api_idempotency`.
- Sin Postman de negocio en este plan: solo colección mínima de salud/seguridad/envelope; sin navegadores, sin datos personales reales y sin secretos en la evidencia.

Evidencia de la ejecución (2026-10-14, sobre `main` actualizado con el Compose de F1 sano):

- **Build backend (comando canónico):** `./mvnw -f services/pom.xml verify` termina en `BUILD SUCCESS` para los nueve módulos (16 pruebas por servicio de dominio: contexto, validador `iss`/`aud`, seguridad/envelope y protocolo de idempotencia; 1 por `api-gateway` y `discovery-server`).
- **`SEC06` (paso 14):** con `Authorization: Bearer` de firma ajena, `GET /api/auth/me` y `GET /api/hotel/listar` responden `401` con envelope `UNAUTHENTICATED` en los siete servicios (200/401 comprobado con `curl` por puerto 8081–8087); la política de `aud`/`iss` queda además cubierta por `JwtValidatorTests` en cada servicio (audiencia ajena o issuer distinto → error; `hotel-api` o el propio servicio → aceptado).
- **Salud (pasos 10–11):** `GET /actuator/health/liveness` da 200 en los siete; `GET /actuator/health/readiness` da 200 con PostgreSQL arriba. Con `docker compose stop postgres`, `readiness` pasa a `503` y `liveness` se mantiene `200`; al reanudar, `readiness` vuelve a `200`.
- **Correlación (paso 8) y logs (paso 12):** dos llamadas sin cabecera devuelven `X-Correlation-Id` distintos; con cabecera se devuelve el mismo valor; la cabecera y `meta.correlationId` coinciden. Muestra de log estructurado: `{"...","level":"WARN","service":"identity-service","correlationId":"f4b9606a-...","message":"authentication rejected: GET /api/hotel/listar"}` (sin secretos).
- **Idempotencia (paso 9):** Flyway aplica `V1__api_idempotency.sql` con el rol `identity_migrator` y `SELECT table_name FROM information_schema.tables WHERE table_name='api_idempotency'` devuelve la tabla en `identity_db` (idéntica migración en las siete bases). `IdempotencyFilterTests` (5 por servicio) cubre replay de resultado terminal, `409 IDEMPOTENCY_CONFLICT` con mismo key y distinto request, `202` en curso, reserva + completado en intención nueva y `400` sin clave. La repetición en vivo con una mutación real se ejercita en los planes de dominio, que son los dueños de los endpoints.
- **Token máquina (paso 5):** `grep -l "SERVICE_CLIENT_ID" services/*/src/main/resources/application.yml` devuelve los cinco servicios que llaman rutas internas (identity, hotel, reception, inventory, sales); en las pruebas, un principal con rol (humano) sin scope ante una ruta interna responde `403 FORBIDDEN` y uno con `SCOPE_hoteles:read` pasa la autorización.

## 7 Preguntas abiertas

Ninguna. Las cinco cuestiones iniciales se decidieron en §3.

## 8 Criterio de terminado

- [x] Los siete servicios compilan con las versiones fijadas y exponen la convención (JWT RS256, envelope y códigos genéricos, `correlationId`, idempotencia, salud y logs) sin lógica de negocio nueva. Evidencia: `./mvnw -f services/pom.xml verify` en `BUILD SUCCESS` de los nueve módulos.
- [x] `SEC06` pasa en cada servicio (`401` ante firma ajena o `aud` incorrecta) con evidencia por servicio. Evidencia: `curl` por puerto 8081–8087 y `JwtValidatorTests` por servicio.
- [x] `GET /actuator/health/liveness` y `GET /actuator/health/readiness` cumplen la semántica de la spec en cada servicio. La exclusión de los endpoints de salud en el Gateway es del plan `infra-gateway-eureka` (se ejecuta en la misma tanda); este plan solo publica salud y no la enruta como ruta de negocio.
- [x] Ningún servicio acepta ni emite `X-User-Id`, `X-Role` ni `X-Hotel-Id` para identidad; no hay reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server; no hay topics, comandos ni grupos nuevos.
- [x] `python scripts/validar-planes.py` da 0 errores desde la raíz; sin avisos.
- [x] Archivo entregado en `docs/plans/back-plataforma-comunes.md`; rama `back-plataforma-comunes` desde `main` actualizado, primer commit `docs: marca en curso el plan back-plataforma-comunes` y commits de implementación, push y PR a `main` sin auto-merge (revisa una persona).
