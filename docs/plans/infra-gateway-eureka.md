# infra-gateway-eureka — Gateway y Eureka (F1)

| Campo | Valor |
|---|---|
| Plan | `infra-gateway-eureka` |
| Ámbito | `infra` |
| Componente | `infra` |
| Fase | `F1` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `infra-compose` |
| Spec | [`01-arquitectura.md` §3 Frontends y entrada](../especificacion/01-arquitectura.md#3-frontends-y-entrada) y [§4 Stack](../especificacion/01-arquitectura.md#4-stack); [`gateway-eureka-docker.md` §API Gateway](../especificacion/infraestructura/gateway-eureka-docker.md#api-gateway), [§Discovery server](../especificacion/infraestructura/gateway-eureka-docker.md#discovery-server), [§Compose y puertos propuestos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos) y [§Construcción y arranque futuro](../especificacion/infraestructura/gateway-eureka-docker.md#construcción-y-arranque-futuro); [`configuracion.md` §Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), [§Por servicio](../especificacion/infraestructura/configuracion.md#por-servicio) y [§Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio); [`03-contrato-comun.md` §Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [§Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) y [§Salud, readiness y resiliencia](../especificacion/03-contrato-comun.md#salud-readiness-y-resiliencia); [`errors.md` §Reporting y Gateway](../contracts/errors.md#reporting-y-gateway); [`contracts/README.md` §1 Reglas transversales](../contracts/README.md#1-reglas-transversales); [`06-migracion-y-pruebas.md` §2](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) y [`07-revision-y-arranque.md` §Primer incremento](../especificacion/07-revision-y-arranque.md#primer-incremento-implementable) |
| Casos | `SEC08` |
| Change | `—` |

## 1. Objetivo

El Gateway en `services/api-gateway/` y Eureka en `services/discovery-server/` funcionan con las versiones fijadas: el Gateway enruta por prefijo a los seis servicios de negocio, responde 401 `UNAUTHENTICATED` sin token, aplica el rate limit público de 60/min por IP, los timeouts de la tabla y el CORS de la allowlist, y no expone rutas internas, Eureka ni actuator; Eureka registra solo con credenciales propias. Criterio: los comandos canónicos en verde y `SEC08` con evidencia adjunta al PR.

## 2. Alcance

**Entra:**

- `services/discovery-server/`: servidor Eureka en el puerto 8761 (red interna; localhost para depuración), sin autorregistro en la demo single-node, registro protegido con credenciales propias (variables EUREKA_USERNAME y EUREKA_PASSWORD, distintas de los usuarios del hotel) y liveness/readiness internos.
- `services/api-gateway/`: aplicación reactiva en el puerto 8080 con las rutas explícitas por prefijo de la [tabla de la spec](../especificacion/infraestructura/gateway-eureka-docker.md#api-gateway) hacia los seis servicios de negocio; validación de JWT; correlationId; eliminación de cabeceras de identidad falsificables; límite de cuerpo 1MiB; rate limit público 60/min; timeouts por ruta; CORS de la allowlist; actuator sin exponer al exterior.
- Configuración por variables de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) (comunes y propias de api-gateway), con sus nombres en `.env.example` y sin valores reales.
- Dockerfiles multi-stage de los dos módulos y sus entradas en `infra/compose.yaml` (perfil `full`) con healthchecks.
- Pruebas de módulo (rutas, 401, CORS, 413, 429, timeouts, correlationId) y evidencia del caso `SEC08`.

**Fuera de alcance:**

- El Compose de infraestructura (PostgreSQL, brokers, Mailpit) y su orden de arranque → plan `infra-compose`; este plan solo añade los dos contenedores de Gateway y Eureka.
- La convención común en los servicios de negocio (resource server, envelope y códigos, idempotencia, health y logs) → plan `back-plataforma-comunes`; los límites de login/registro de identity y los 410 de rutas retiradas se implementan en sus servicios.
- Autorización por rol, scope o sede, e idempotencia de negocio: cada servicio la aplica; el Gateway no decide acceso sobre datos ni implementa stock ni joins.
- Mensajería: el catálogo no asigna a este plan ningún topic, comando ni grupo; no se crea nada de ese alcance.
- Retiro de rutas `LEGACY`: se mantienen durante todo el MVP; este plan no retira ninguna ruta.
- Contratos y especificación: no se editan `docs/contracts/**` (OpenAPI, errores, schemas) ni `docs/especificacion/**`.
- Frontends, servicios de dominio, migraciones y seeds → planes `front-*` y `back-*`.
- Alcance cerrado de la [especificación](../especificacion/README.md#alcance-cerrado): sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server; el contador del rate limit vive en memoria de una instancia.

## 3. Contexto

- Este plan es la pieza «Gateway/Eureka» del paso 1 del [orden de implementación](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) y del punto 2 del [primer incremento](../especificacion/07-revision-y-arranque.md#primer-incremento-implementable). Junto con `infra-compose` habilita el criterio de salida de F1 que le toca: el Gateway enruta por prefijo y responde 401 sin token; rutas internas no expuestas ([norma §2](README.md#2-fases)).
- Lo que la spec ya fija y este plan no repite: la tabla de prefijos por servicio y las prohibiciones de rutas; el rate limit público y los timeouts con sus valores; la configuración CORS exacta; las reglas de Eureka (puerto, alcance, credenciales propias, sin autorregistro); los puertos de Compose; los nombres de variables; la forma del envelope, los códigos de error del Gateway y las reglas de headers e identidad (todo enlazado por sección en la cabecera).
- Identidad: solo por JWT validado ([`03` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes)). Nunca se propagan cabeceras `X-User-Id`, `X-Role` ni `X-Hotel-Id`; el Gateway las elimina y ningún paso de este plan las envía.
- Decisiones de este plan (la spec no las fija):
  - el límite público se implementa en memoria del proceso, con clave por IP remota (una instancia; sin Redis, como fija la spec para el MVP);
  - el límite público cubre las rutas públicas documentadas (prefijo `/api/public` y login, registro, social y JWKS, marcadas P en los `api.md`); los límites propios de identity (login 5/min, registro 3/min) se aplican además y no se sustituyen;
  - el registro de Eureka se protege con HTTP Basic usando EUREKA_USERNAME y EUREKA_PASSWORD (los mismos nombres que fija `configuracion.md` para los clientes), sin valores por defecto sensibles;
  - los endpoints de actuator se publican en un puerto de gestión interno no expuesto (p. ej. 8090) para los healthchecks; el puerto 8080 externo no los responde;
  - en desarrollo las apps siguen arrancando desde el IDE ([`gateway-eureka-docker.md` §Compose](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos)); los contenedores de este plan existen para el perfil `full`;
  - no se añade circuit breaker propio al Gateway: destino caído o timeout se traduce en 503 `DEPENDENCY_UNAVAILABLE` ([`errors.md`](../contracts/errors.md#reporting-y-gateway)), sin retry ciego;
  - el Gateway valida algoritmo, issuer, audience y exp; roles, scopes y sede quedan en los servicios.
- Versiones: solo las fijadas por [`01` §4](../especificacion/01-arquitectura.md#4-stack) (Java 25, Boot 4.1.1, Cloud 2025.1.3); la re-verificación de compatibilidad de Cloud se hace en el paso 1.

## 4. Pasos

- [x] 1. Re-verificar la compatibilidad Boot 4.1.1 ↔ Cloud 2025.1.3 que exige [`01` §4](../especificacion/01-arquitectura.md#4-stack) y fijar en los poms de los dos módulos las dependencias necesarias sin versiones flotantes: Gateway reactivo, cliente Eureka + LoadBalancer, resource server para validar JWT y Actuator; para discovery-server, servidor Eureka, seguridad y Actuator. Verificación: `./mvnw -f services/pom.xml -pl :api-gateway,:discovery-server -am dependency:tree` resuelve dentro de Cloud 2025.1.3 y Boot 4.1.1 (sin `latest`) y el PR registra la re-verificación de la tabla oficial.
- [x] 2. Poner en marcha `services/discovery-server/`: puerto 8761, sin autorregistro en la demo single-node, acceso solo por red interna y localhost para depuración, registro con credenciales propias (EUREKA_USERNAME y EUREKA_PASSWORD) y liveness/readiness internos. Verificación: el proceso arranca; `curl -u "$EUREKA_USERNAME:$EUREKA_PASSWORD" http://localhost:8761/eureka/apps` responde 200 y sin credenciales responde 401; el registro de sí mismo está deshabilitado.
- [x] 3. Poner en marcha `services/api-gateway/` en el puerto 8080: registro en Eureka con EUREKA_URL y las credenciales propias; validación JWT RS256 con issuer y jwk-set-uri explícitos (JWT_ISSUER y JWK_SET_URI de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), sin descubrimiento OIDC) y sin impedir el arranque cuando identity aún no responde. Verificación: el proceso arranca sin identity; con la clave de Eureka incorrecta no aparece en el registro y el fallo queda visible en logs; con la correcta aparece.
- [x] 4. Configurar las rutas explícitas por prefijo de la [tabla de la spec](../especificacion/infraestructura/gateway-eureka-docker.md#api-gateway) hacia los service ids de Eureka: `/api/auth`, `/api/persona`, `/api/tipopersona` y `/.well-known/jwks.json` a identity; `/api/public`, `/api/hotel`, `/api/piso`, `/api/categoria`, `/api/habitacion` y `/api/estadohabitacion` a hotel; `/api/recepcion` a reception; `/api/producto` a inventory; `/api/carrito` y `/api/venta` a sales; `/api/reportes` a reporting. Sin discovery-locator y sin ruta hacia notification, hacia `/internal`, hacia Eureka ni hacia `/actuator`. Verificación: la prueba de rutas afirma el destino de cada prefijo; por el 8080 una ruta pública llega al servicio destino y una ruta no documentada responde 404.
- [x] 5. Exigir JWT válido en las rutas privadas del Gateway: firma RS256, issuer y audience fijos, exp, contra el jwk-set-uri explícito; sin token, solo pasan las rutas públicas documentadas (prefijo `/api/public` y login, registro, social —que conserva el 410 del servicio— y JWKS); con token inválido o expirado, 401 `UNAUTHENTICATED`. Verificación: sin token, una ruta privada devuelve 401 con envelope; las públicas no devuelven 401 por falta de token; un token con firma o audience manipuladas devuelve 401.
- [x] 6. Filtrar cabeceras de identidad falsificables (`X-User-Id`, `X-Role`, `X-Hotel-Id`) de todo request antes de reenviarlo; generar `X-Correlation-Id` (UUID) cuando el cliente no lo envía, propagarlo aguas abajo y devolverlo en la respuesta; sin retry automático y sin caché de respuestas autenticadas. Verificación: una petición con esas cabeceras falsas no las ve el servicio destino (prueba de captura de headers); el cliente recibe el mismo `X-Correlation-Id` que envió o uno nuevo; la configuración no declara filtros de reintento ni de caché.
- [x] 7. Limitar el cuerpo de la petición a 1MiB (variable GATEWAY_MAX_BODY_BYTES, default 1048576) y responder 413 `PAYLOAD_TOO_LARGE` con envelope al excederse. Verificación: un POST de más de 1MiB a una ruta enrutada devuelve 413 con `error.code` PAYLOAD_TOO_LARGE y meta.correlationId; un cuerpo menor supera el filtro.
- [x] 8. Aplicar el rate limit público de 60 req/min por IP (variable RATE_LIMIT_PUBLIC_RPM) sobre las rutas públicas documentadas; al excederse, 429 `RATE_LIMITED` con `Retry-After`. Verificación: la petición 61 al mismo recurso público en el mismo minuto responde 429 con `Retry-After`; antes del límite las públicas responden normal; una ruta privada no consume este contador.
- [x] 9. Configurar timeouts por ruta: conectar 2s / leer 5s por defecto (variables ROUTE_CONNECT_TIMEOUT_MS y ROUTE_READ_TIMEOUT_MS) y 10s de lectura en la ruta de reportes; sin retry ciego. Verificación: con el servicio destino detenido, una llamada por el 8080 responde 503 `DEPENDENCY_UNAVAILABLE` en el orden del timeout configurado; la prueba de configuración confirma 10000 ms de lectura solo en reportes.
- [x] 10. Responder los errores propios del Gateway (401, 413, 429 y 503) con la forma del envelope de [`03` §Respuestas](../especificacion/03-contrato-comun.md#respuestas), los códigos de [`errors.md` §Reporting y Gateway](../contracts/errors.md#reporting-y-gateway) y meta.correlationId, sin stacktrace ni SQL. Verificación: `curl` a cada caso muestra `success:false`, `data:null`, el `error.code` esperado y el meta.correlationId igual al `X-Correlation-Id` devuelto.
- [x] 11. Configurar CORS con la allowlist de desarrollo (http://localhost:4200 y http://localhost:4300), métodos GET, POST, PUT, DELETE y OPTIONS, allowed headers Authorization, Content-Type, Accept, Idempotency-Key, If-Match y X-Correlation-Id, exposed headers ETag, Location, Retry-After y X-Correlation-Id, allowCredentials=false y maxAge=600; preflight de origen permitido sin JWT; sin wildcard. Verificación: el preflight con origen permitido y `Access-Control-Request-Headers` con If-Match e Idempotency-Key responde permitido; con origen ajeno no aparece `Access-Control-Allow-Origin` (evidencia de `SEC08`).
- [x] 12. Aislar los endpoints de actuator del puerto externo 8080 (gestión en puerto interno no publicado) y usarlos solo para liveness/readiness de los healthchecks. Verificación: `curl -i http://localhost:8080/actuator/health` desde el host devuelve 404 sin datos; el healthcheck interno del contenedor queda en verde (paso 14).
- [x] 13. Crear un Dockerfile multi-stage por módulo: build con el Maven Wrapper y runtime con imagen Java 25 de tag y digest fijos, usuario no root, sin secretos ni `.env` dentro de la imagen. Verificación: `docker build` de las dos imágenes termina bien; `docker image inspect` muestra la referencia fija (sin `latest`); la revisión de capas no encuentra claves ni `.env`.
- [x] 14. Añadir a `infra/compose.yaml` los servicios discovery-server y api-gateway en el perfil `full`: puertos publicados solo en 127.0.0.1 (8761 y 8080), red privada común, healthchecks contra los endpoints internos y dependencia de Eureka sana; variables por nombre, sin valores reales. Verificación: `docker compose -f infra/compose.yaml config` sin errores; `docker compose -f infra/compose.yaml --profile full up -d` deja ambos contenedores `healthy`; `docker inspect` confirma publicaciones solo en localhost.
- [x] 15. Escribir las pruebas de los dos módulos: destino de cada prefijo, 401 sin token, CORS permitido y denegado, 413, 429 con Retry-After, timeouts, correlationId y eliminación de cabeceras falsificables. Verificación: `./mvnw -f services/pom.xml verify` en verde con el reporte de pruebas de ambos módulos adjunto al PR.
- [x] 16. Verificación final y evidencia: revisar que `.env.example` lista por nombre las variables que usan los dos módulos (sin valores); ejecutar los comandos canónicos (`./mvnw -f services/pom.xml verify`, `docker compose -f infra/compose.yaml config`, `python scripts/validar-planes.py`) y el smoke de rutas públicas, 401 sin token y `SEC08`; adjuntar las salidas al PR. Verificación: build y Compose en verde, validador con 0 errores y evidencia de `SEC08` completa.

## 5. Contratos afectados

- OpenAPI: ninguno; el Gateway no añade ni cambia rutas de negocio y expone por prefijo las ya documentadas en los `api.md` ([catálogo §3](catalogo.md#3-endpoints)).
- Eventos y comandos: ninguno; el catálogo no asigna mensajería a este plan ([catálogo §2](catalogo.md#2-mensajería)).
- Persistencia: ninguna; la spec fija «BD/procedimientos/seeds: ninguno» para Gateway y Eureka ([§API Gateway](../especificacion/infraestructura/gateway-eureka-docker.md#api-gateway) y [§Discovery server](../especificacion/infraestructura/gateway-eureka-docker.md#discovery-server)).
- Errores: `UNAUTHENTICATED` (401), `PAYLOAD_TOO_LARGE` (413), `RATE_LIMITED` (429) y `DEPENDENCY_UNAVAILABLE` (503) de [`errors.md` §Reporting y Gateway](../contracts/errors.md#reporting-y-gateway); el resto de códigos atraviesa el Gateway sin reescritura desde los servicios.
- Configuración: variables comunes y propias de api-gateway de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) (EUREKA_URL, EUREKA_USERNAME, EUREKA_PASSWORD, JWT_ISSUER, JWK_SET_URI, RATE_LIMIT_PUBLIC_RPM, ROUTE_CONNECT_TIMEOUT_MS, ROUTE_READ_TIMEOUT_MS, GATEWAY_MAX_BODY_BYTES), por nombre en `.env.example`, sin secretos en Git.

## 6. Pruebas

- Caso `SEC08` (CORS y headers para el frontend): con el Gateway en marcha y origen permitido http://localhost:4200, el preflight `OPTIONS` con `Access-Control-Request-Headers: If-Match, Idempotency-Key` se responde como permitido; una respuesta real que atraviesa el Gateway conserva `ETag`, `Location` y `Retry-After` legibles para el navegador (exposed headers, con stub en la prueba de integración hasta que los servicios los emitan); el mismo preflight desde http://evil.test no recibe `Access-Control-Allow-Origin`. Evidencia: salida de `curl` de preflight y respuesta real + reporte de pruebas del módulo, adjuntos al PR de ejecución.
- Comandos canónicos de la sección «Verificación» del `README.md` raíz (creados por `infra-monorepo`): `./mvnw -f services/pom.xml verify` (BUILD SUCCESS), `docker compose -f infra/compose.yaml config` (sin errores) y `python scripts/validar-planes.py` (0 errores); evidencia en el PR de ejecución.
- Criterio de salida F1 de este plan ([norma §2](README.md#2-fases)): el Gateway enruta por prefijo y responde 401 `UNAUTHENTICATED` sin token, y las rutas internas no están expuestas; evidencia: respuesta HTTP + log del Gateway con correlationId.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] El Gateway enruta por prefijo y responde 401 sin token; rutas internas, Eureka y actuator no expuestos por el 8080.
- [ ] Rate limit público de 60/min, timeouts y CORS verificados con evidencia; `SEC08` con evidencia adjunta.
- [ ] Eureka con credenciales propias (sin credenciales 401; con credenciales registra) y contenedores del perfil `full` sanos.
- [ ] `./mvnw -f services/pom.xml verify` en verde, `docker compose -f infra/compose.yaml config` válido y `python scripts/validar-planes.py` sin errores.
- [ ] Sin preguntas abiertas.
