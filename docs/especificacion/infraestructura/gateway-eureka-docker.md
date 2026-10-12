# Gateway, Eureka y Docker

## API Gateway

Spring Cloud Gateway, puerto 8080. Enrutamiento explícito, sin discovery-locator público automático:

| Prefijo externo | Servicio |
|---|---|
| /api/auth/**, /api/persona/**, /api/tipopersona/**, /.well-known/jwks.json | identity-service |
| /api/public/**, /api/hotel/**, /api/piso/**, /api/categoria/**, /api/habitacion/**, /api/estadohabitacion/** | hotel-service |
| /api/recepcion/** | reception-service |
| /api/producto/** | inventory-service |
| /api/carrito/**, /api/venta/** | sales-service |
| /api/reportes/** | reporting-service |

No rutas Gateway hacia notification, /internal/**, /eureka/**, /actuator/** ni DB/brokers. No API /api/detalleventa independiente: detalle vive dentro de Venta.

JWT, correlationId, límite de tamaño 1MiB, rate limit público y CORS allowlist para los dos orígenes; CORS no reemplaza autorización. Exponer solo endpoints documentados. Opciones preflight permitidas para orígenes aprobados. Sin caché autenticada. Gateway no implementa stock ni joins.

Rate limit y timeouts de demo (una instancia; valores en [configuracion.md](configuracion.md)):

- Público: **60 req/min por IP**. Los límites de identity (login 5/min por IP+cuenta, registro 3/min por IP) se aplican además allí y no se sustituyen por el límite del Gateway.
- Timeouts por ruta: conectar 2s / leer 5s por defecto; **10s de lectura en reportes**. Sin retry ciego; un reintento solo con la misma clave/idempotencia.
- Si se despliega con réplicas, revisar el almacenamiento del contador antes de afirmar límites globales; no agregar Redis al MVP.

Ciclo de vida de rutas `LEGACY`/`RETIRADA`: las rutas `LEGACY` se mantienen durante todo el MVP (convivencia con el frontend adaptado) y se retiran al cerrar la migración, después de validar la [matriz de aceptación](../06-migracion-y-pruebas.md). Una ruta retirada responde **410**; no hay fecha fija: el retiro es la decisión de cierre.

Configuración CORS explícita en Gateway:

- allowedOrigins: http://localhost:4200 y http://localhost:4300 en dev; dominios HTTPS concretos por entorno en despliegue.
- allowedMethods: GET, POST, PUT, DELETE, OPTIONS.
- allowedHeaders: Authorization, Content-Type, Accept, Idempotency-Key, If-Match, X-Correlation-Id.
- exposedHeaders: ETag, Location, Retry-After, X-Correlation-Id; necesarios para editar/reintentar/pollear desde Angular.
- allowCredentials=false: no se utilizan cookies de autenticación; maxAge=600.
- Preflight de origen permitido no exige JWT; la petición real sí aplica autorización. Sin wildcard de origen ni rutas internas como efecto del preflight.

Rate limit de demo local a una instancia; login en identity distingue cuenta/IP sin revelar existencia de cuenta. Si se despliega con réplicas, revisar el almacenamiento del contador antes de afirmar límites globales; no agregar Redis al MVP.

BD/procedimientos/seeds: ninguno. Configuración de rutas versionada; secretos externos. Runbooks de operación: [runbooks.md](runbooks.md).

## Discovery server

Eureka puerto 8761, solo red interna y localhost para depuración. Registro con credenciales distintas de usuarios del hotel. No se registra a sí mismo en single-node demo. Heartbeats no prueban que una dependencia de negocio esté sana; Feign tiene timeouts.

BD/procedimientos/seeds: ninguno. Registro efímero se reconstruye por clientes. No rutas consumidas por Angular.

## Compose y puertos propuestos

| Contenedor | Puerto contenedor | Host dev |
|---|---:|---|
| postgres | 5432 | 127.0.0.1:55432 |
| rabbitmq | 5672 / 15672 | 127.0.0.1:5673 / 15673 |
| kafka KRaft | 9092 interno / 29092 externo | 127.0.0.1:29092 |
| mailpit | SMTP 1025 / UI 8025 | 127.0.0.1:1025 / 8025 |
| discovery-server | 8761 | 127.0.0.1:8761 |
| api-gateway | 8080 | 127.0.0.1:8080 |
| identity/hotel/reception | 8081 / 8082 / 8083 | solo perfil debug, localhost |
| inventory/sales/reporting/notification | 8084 / 8085 / 8086 / 8087 | solo perfil debug, localhost |
| admin-web / public-web | 80 / 80 | 127.0.0.1:4200 / 4300 |

Puertos son diseño, no verificación de disponibilidad. Evitar conflicto con ejercicios previos; override de Compose si están ocupados.

`infra`: PostgreSQL, brokers y Mailpit; apps desde IDE. `full`: todo en contenedores. Una red privada backend; reverse proxy/Gateway publicados; volúmenes con nombres para PostgreSQL, Kafka y Rabbit. Healthchecks y reintento de clientes; depends_on por sí solo no garantiza readiness.

No montar el socket Docker dentro de apps. No exponer consolas a internet. Persistencia no se borra con comando rutinario de apagado. Backup antes de migrar.

## Bases/usuarios

`identity_db/identity_app`, `hotel_db/hotel_app`, `reception_db/reception_app`, `inventory_db/inventory_app`, `sales_db/sales_app`, `reporting_db/reporting_app`, `notification_db/notification_app`. Revocar CONNECT global y grants públicos que permitan cruces. Migrador por DB, runtime sin DDL; credenciales de broker distintas por servicio.

Init de PostgreSQL crea DB/roles; Flyway de cada app crea su propio esquema. Migraciones versionadas V1, V2…; seeds dev aparte, no activados en producción.

## Configuración necesaria

Las propiedades `application.yml` y variables de entorno con nombre exacto, separadas en comunes y propias, están en [configuracion.md](configuracion.md): datasource, Eureka, brokers (Kafka/RabbitMQ), JWT (`JWT_ISSUER`, `JWK_SET_URI`), cliente máquina, SMTP, outbox/inbox y rate/timeouts. Sin valores reales en `.env.example`.

HTTP entre apps solo en red local demo; TLS para despliegue real, incluidos credenciales internas y brokers. JWT público aud hotel-api, tokens internos aud servicio concreto.

Logs estructurados incluyen service, correlationId, eventId/commandId cuando aplique. Readiness interna revisa DB y dependencias críticas; liveness solo proceso. Actuator health sin detalles a usuario externo.

## Construcción y arranque futuro

Las siguientes son tareas de implementación, no comandos ya disponibles:

1. Fijar los builds en Boot 4.1.1 / Cloud 2025.1.3, Angular 22.2.2 y Node compatible (`^22.22.3 || ^24.15.0 || ^26.0.0`), con lockfiles.
2. Crear Dockerfile multi-stage por servicio y frontend; lockfiles e imágenes con tag fijo/digest.
3. Compose valida configuración sin secretos en Git.
4. Migraciones + seed de catálogos y datos ficticios.
5. Eureka/identity y resto; bootstrap de proyecciones; esperar readiness.
6. Ejecutar smoke/contratos de integración.

No se instala ni levanta infraestructura como parte de esta entrega de Markdown.
