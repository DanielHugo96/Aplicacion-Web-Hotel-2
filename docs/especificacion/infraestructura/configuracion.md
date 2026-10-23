# Configuración por servicio

Reemplaza la lista general de variables de [gateway-eureka-docker.md](gateway-eureka-docker.md). Convención: propiedades Spring en kebab-case con valor `${VARIABLE_DE_ENTORNO:default}`; los nombres de la columna "Variable" son los del entorno. Sin secretos reales en Git (`.env.example` solo documenta nombres y defaults no sensibles).

## Comunes a todos los servicios

| Propiedad (`application.yml`) | Variable de entorno | Default | Notas |
|---|---|---|---|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev` | Seeds solo en `dev`/`demo`; nunca en producción |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:55432/<db>` | Base propia de cada servicio; sin acceso cruzado |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `<servicio>_app` | Usuario runtime sin DDL; el migrador es aparte |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | — | Obligatorio; fuera de Git |
| `eureka.client.service-url.defaultZone` | `EUREKA_URL` | `http://localhost:8761/eureka` | Registro con credenciales propias |
| `eureka.client.username/password` | `EUREKA_USERNAME`, `EUREKA_PASSWORD` | — | Distintas de usuarios del hotel |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Solo si el servicio usa Kafka |
| `spring.rabbitmq.host/port` | `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5673` | Solo si usa Rabbit |
| `spring.rabbitmq.username/password/virtual-host` | `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | `<servicio>`, —, `/hotel-demo` | Permisos por vhost/producer/consumer |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `JWT_ISSUER` | `hotel-identity` | Validación local de issuer |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `JWK_SET_URI` | `http://identity-service/.well-known/jwks.json` | Valor explícito; sin descubrimiento OIDC |
| `hotel.security.machine.client-id/secret` | `SERVICE_CLIENT_ID`, `SERVICE_CLIENT_SECRET` | — | Token máquina; solo servicios que llaman `/internal/**` |
| `hotel.feign.connect-timeout-ms` | `FEIGN_CONNECT_TIMEOUT_MS` | `2000` | |
| `hotel.feign.read-timeout-ms` | `FEIGN_READ_TIMEOUT_MS` | `5000` | `10000` en lecturas de reportes |
| `resilience4j.circuitbreaker.configs.default.sliding-window-size` | `CB_WINDOW_SIZE` | `10` | 10 llamadas |
| `resilience4j.circuitbreaker.configs.default.failure-rate-threshold` | `CB_FAILURE_RATE` | `50` | Umbral de fallo % |
| `resilience4j.circuitbreaker.configs.default.minimum-number-of-calls` | `CB_MIN_CALLS` | `5` | Mínimo para evaluar |
| `resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state` | `CB_OPEN_MS` | `10000` | 10s abierto |
| `resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state` | `CB_HALF_OPEN_CALLS` | `3` | Pruebas en half-open |
| `resilience4j.timelimiter.configs.default.timeout-duration` | `CB_TIMEOUT_MS` | `6000` | TimeLimiter del breaker. Debe superar el read-timeout de Feign (5000 ms) para que el breaker no corte antes que la propia llamada. El default de Resilience4j es **1 s**, que abortaría llamadas sanas. |
| `hotel.outbox.poll-interval-ms` | `OUTBOX_POLL_INTERVAL_MS` | `5000` | Poller cada 5s |
| `hotel.outbox.batch-size` | `OUTBOX_BATCH_SIZE` | `50` | Filas por ciclo |
| `hotel.outbox.alert-attempts` | `OUTBOX_ALERT_ATTEMPTS` | `10` | Alerta si `attempts` supera el valor |
| `hotel.outbox.alert-pending-minutes` | `OUTBOX_ALERT_PENDING_MINUTES` | `15` | Alerta si pendiente > 15min |
| `hotel.inbox.retention-days` | `INBOX_RETENTION_DAYS` | `30` | Alineada con idempotencia |
| `hotel.demo.password` | `DEMO_PASSWORD` | — | Solo seeds; sin ella no se siembran usuarios |
| `logging.level.root` | `LOG_LEVEL` | `INFO` | Logs estructurados con `service`, `correlationId`, `eventId`/`commandId` |

Las propiedades de Resilience4j viven bajo `resilience4j.circuitbreaker.configs.default.*` y `resilience4j.timelimiter.configs.default.*`, **no** en claves de primer nivel: en Resilience4j 2.3.0 las claves de primer nivel se descartan en silencio y el breaker corre con los defaults de la librería (ventana 100, mínimo 100 llamadas), con lo que nunca se abriría. El breaker de Feign se construye con un id derivado de `Feign.configKey(...)` alfanumerizado, así que la configuración por instancia no es viable: se usa `configs.default`, que `CommonCircuitBreakerConfigurationProperties.findCircuitBreakerProperties(id)` aplica como fallback y que `CircuitBreakerRegistry.getDefaultConfig()` consume. Los **valores no cambian**: 10 / 50 / 5 / 10000 / 3.

## Por servicio

| Servicio | Propiedad | Variable | Default | Notas |
|---|---|---|---|---|
| api-gateway | `hotel.gateway.rate-limit.public-rpm` | `RATE_LIMIT_PUBLIC_RPM` | `60` | Por IP en rutas públicas |
| api-gateway | `hotel.gateway.route.default-connect-timeout-ms` / `read-timeout-ms` | `ROUTE_CONNECT_TIMEOUT_MS`, `ROUTE_READ_TIMEOUT_MS` | `2000`, `5000` | Ruta de reportes: `10000` de lectura |
| api-gateway | `hotel.gateway.max-body-bytes` | `GATEWAY_MAX_BODY_BYTES` | `1048576` | 1MiB |
| identity | `hotel.identity.signing-key-file` | `IDENTITY_SIGNING_KEY_FILE` | — | Clave privada RS256 fuera de Git |
| identity | `hotel.identity.jwk-kid` | `IDENTITY_JWK_KID` | `v1` | Rotación documentada |
| identity | `hotel.identity.token-ttl-minutes` | `TOKEN_TTL_MINUTES` | `15` | Sin refresh en MVP |
| identity | `hotel.identity.machine-token-ttl-minutes` | `MACHINE_TOKEN_TTL_MINUTES` | `5` | `sub`=servicio, `aud`=destino |
| identity | `hotel.identity.login-rate-limit-per-minute` | `LOGIN_RATE_LIMIT_PER_MINUTE` | `5` | Por IP+cuenta |
| identity | `hotel.identity.registro-rate-limit-per-minute` | `REGISTRO_RATE_LIMIT_PER_MINUTE` | `3` | Por IP |
| identity | `hotel.identity.anonimizar-inactivos-anios` | `ANONIMIZAR_INACTIVOS_ANIOS` | `5` | Procedimiento manual auditado |
| hotel | `hotel.catalogo.public-cache-max-age-seconds` | `PUBLIC_CACHE_MAX_AGE_SECONDS` | `60` | `Cache-Control` de rutas públicas |
| hotel | `hotel.imagenes.url-allowlist` | `IMAGE_URL_ALLOWLIST` | `/assets/,https://` | Sin `file://` ni fetch arbitrario |
| reception | `hotel.reception.closure-recovery-interval-ms` | `CLOSURE_RECOVERY_INTERVAL_MS` | `30000` | Recuperación de cierres cada 30s |
| sales | `hotel.sales.recovery-interval-ms` | `SALE_RECOVERY_INTERVAL_MS` | `30000` | Job de ventas cada 30s |
| sales | `hotel.sales.pending-reject-age-seconds` | `SALE_PENDING_REJECT_AGE_SECONDS` | `120` | PENDIENTE > 120s con operación PREPARANDO se rechaza |
| sales | `hotel.sales.max-lineas` | `SALE_MAX_LINEAS` | `50` | IDs distintos por venta |
| inventory | `hotel.stock.bajo-stock-limite` | `STOCK_BAJO_LIMITE` | `5` | Reporte y proyección |
| reporting | `hotel.reporting.max-range-days` | `REPORT_MAX_RANGE_DAYS` | `366` | Intervalo `[inicio, fin)` |
| reporting | `hotel.reporting.degraded-timeout-minutes` | `REPORTING_DEGRADED_TIMEOUT_MINUTES` | `5` | Sin conexión activa → no utilizable |
| notification | `spring.mail.host/port` | `SMTP_HOST`, `SMTP_PORT` | `localhost`, `1025` | Mailpit en dev |
| notification | `hotel.notification.lease-seconds` | `NOTIFICATION_LEASE_SECONDS` | `60` | Lease por job |
| notification | `hotel.notification.max-attempts` | `NOTIFICATION_MAX_ATTEMPTS` | `3` | + intento inicial = 4 reintentos SMTP |
| notification | `hotel.notification.retention-sent-days` | `NOTIFICATION_RETENTION_SENT_DAYS` | `30` | Destinatario/variables → NULL |
| notification | `hotel.notification.audit-retention-days` | `NOTIFICATION_AUDIT_RETENTION_DAYS` | `90` | commandId/estado/timestamps |
| notification | `hotel.notification.admin` | `--admin` | — | Habilita la CLI local auditada del operador |

## Health y readiness por servicio

Todo servicio expone liveness (proceso vivo) y readiness (DB arriba), según [`03-contrato-comun.md`](../03-contrato-comun.md):

| Servicio | Dependencias críticas para readiness | Degradación visible (no bloquea) |
|---|---|---|
| identity | PostgreSQL | Kafka del outbox |
| hotel | PostgreSQL | Kafka/Rabbit (proyección de ocupación y limpieza siguen por outbox) |
| reception | PostgreSQL | Kafka/Rabbit; identity por Feign responde 503 puntual |
| inventory | PostgreSQL | Kafka del outbox |
| sales | PostgreSQL | Kafka/Rabbit de compensaciones |
| reporting | PostgreSQL | Kafka > 5min sin conexión → `degraded`/`REPORTING_UNAVAILABLE` |
| notification | PostgreSQL, Rabbit | SMTP caído → jobs en reintento, negocio continúa |
| api-gateway | — (proceso) | Servicio destino caído → 503/circuit breaker |
| discovery-server | — (proceso) | Registro efímero se reconstruye |

El Gateway no publica `/actuator/**`; los health internos solo se consumen en red privada.

El estado del circuit breaker es diagnóstico interno y se consulta en el puerto de gestión del servicio que lo declara, **nunca por el Gateway**. Para la demo lo declara solo `hotel-service`, que expone `circuitbreakers` y `circuitbreakerevents` en `localhost:8082` (perfil local). El resto sigue exponiendo solo `health`.

## Flyway y seeds

- Convención `V<n>__<descripcion>.sql` por servicio: V1 crea el esquema; V2+ cambios; una versión aplicada **nunca** se edita (el cambio va en una nueva versión). El DDL de referencia vive en cada `base-de-datos.md`; los scripts se derivan de ahí, sin `.sql` duplicados fuera del servicio.
- Seeds solo con perfiles `dev`/`demo`: locations `classpath:db/migration` + `classpath:db/seed/dev`; idempotentes, sin secretos y sin datos personales reales. `DEMO_PASSWORD` es obligatoria para sembrar credenciales.
- Orden de arranque en Compose: Kafka/Rabbit/PostgreSQL sanos (`healthcheck`) → identity y hotel (sedes y habitaciones) → inventory → reception (gates por Kafka) → sales y reporting → notification (sin orden de dependencia).
- Los servicios esperan sus seeds/watermarks antes de recibir tráfico real: reception no admite check-in hasta tener gates; reporting responde `REPORTING_NOT_READY` hasta `LISTO`.
