# infra-compose — PostgreSQL, Kafka, RabbitMQ y Mailpit en Compose (F1)

| Campo | Valor |
|---|---|
| Plan | `infra-compose` |
| Ámbito | `infra` |
| Componente | `infra` |
| Fase | `F1` |
| Modo | `directo` |
| Estado | `propuesto` |
| Depende de | `infra-monorepo` |
| Spec | [`01-arquitectura.md` §3](../especificacion/01-arquitectura.md#3-frontends-y-entrada) y [§4 Stack](../especificacion/01-arquitectura.md#4-stack); [`gateway-eureka-docker.md` §Compose y puertos propuestos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos) y [§Bases/usuarios](../especificacion/infraestructura/gateway-eureka-docker.md#basesusuarios); [`configuracion.md` §Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios), [§Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio) y [§Flyway y seeds](../especificacion/infraestructura/configuracion.md#flyway-y-seeds); [`03-contrato-comun.md` §Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal); [`05-mensajeria.md` §Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta) y [§Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit); [`06-migracion-y-pruebas.md` §2](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Desde un clon limpio, `docker compose -f infra/compose.yaml --env-file .env up -d --wait` levanta la infraestructura local de F1 y termina con los cuatro contenedores sanos: PostgreSQL 18.6 con las siete bases y sus roles, Kafka 4.3.1 en KRaft, RabbitMQ 4.3.6 con management y Mailpit; puertos publicados solo en 127.0.0.1, volúmenes nombrados y healthchecks verdes.

## 2. Alcance

**Entra:**

- `infra/compose.yaml` con los cuatro contenedores de infraestructura: imágenes fijadas, red privada backend única, volúmenes nombrados para PostgreSQL, Kafka y Rabbit, publicaciones en 127.0.0.1 según la [tabla de puertos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos), healthchecks y reinicio automático.
- `infra/postgres/init/`: creación de las siete bases y sus roles por base (un runtime `<servicio>_app` y un migrador `<servicio>_migrator`) usando variables de entorno; sin tablas de negocio.
- `infra/kafka/`: arranque KRaft de un nodo con los defaults de demo ([`05` §Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta)) y los listeners de la tabla de puertos.
- `infra/rabbitmq/`: healthcheck, vhost del proyecto y usuarios de broker (uno por servicio que usa Rabbit y uno de administración) con permisos acotados a su papel productor/consumidor; sin crear exchanges ni colas.
- Extensión de `.env.example` en la raíz con los nombres de las variables nuevas y defaults no sensibles; `.env` local sin versionar.
- Actualización de la sección «Verificación» del `README.md` raíz: los comandos de Compose pasan a llevar `--env-file .env`, porque con `-f infra/compose.yaml` Compose busca el `.env` junto al compose y no en la raíz.

**Fuera de alcance:**

- Gateway y Eureka (rutas, rate limit, CORS, timeouts y credenciales propias) → `infra-gateway-eureka`; este plan no añade esos contenedores.
- Contenedores, Dockerfiles y healthchecks de los siete servicios y de las dos apps Angular: no llegan con este plan (ver Contexto).
- Migraciones Flyway, esquemas de negocio, procedimientos y seeds → planes de servicio; aquí no se crea ninguna tabla.
- Topics, comandos, grupos de consumidor, exchanges, colas, DLT y DLQ: el catálogo no asigna mensajería a este plan; la topología pertenece a `back-plataforma-mensajeria` ([catálogo §2](catalogo.md#2-mensajería)).
- `infra/nginx/`: queda como está; el hosting de las apps en el perfil completo es de planes posteriores.
- CI (creada por `infra-monorepo`), colecciones Postman, runbooks y simulacros → `qa-*`.
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.

## 3. Contexto

- Este plan es la parte de Compose de la infraestructura del paso 1 del [orden de implementación](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) y cubre el criterio de salida de F1 «`compose up` con healthchecks verdes» ([norma §2](README.md#2-fases)). La parte de Gateway del criterio es de `infra-gateway-eureka`.
- La spec fija y este plan no redefine: versiones ([`01` §4](../especificacion/01-arquitectura.md#4-stack)); bases, usuarios y revocación de accesos cruzados ([§Bases/usuarios](../especificacion/infraestructura/gateway-eureka-docker.md#basesusuarios)); URLs, puertos y credenciales por defecto ([`configuracion.md` §Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios)); el orden de arranque ([§Flyway y seeds](../especificacion/infraestructura/configuracion.md#flyway-y-seeds)); los defaults de demo de Kafka —una partición y replicación 1 por topic, retención de 7 días— ([`05` §Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta)); vhost de proyecto, credenciales de broker distintas por servicio y permisos por productor/consumidor ([`05` §Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit)); y la separación runtime/migrador ([`03` §Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal)).
- Decisiones de este plan (la spec no las fija): nombre de proyecto Compose `hotel`; sufijo `_migrator` del usuario migrador; nombres de las variables de entorno de esta capa (POSTGRES_PASSWORD, POSTGRES_<SERVICIO>_APP_PASSWORD, POSTGRES_<SERVICIO>_MIGRATOR_PASSWORD, RABBITMQ_ADMIN_USER, RABBITMQ_ADMIN_PASSWORD y RABBITMQ_<SERVICIO>_PASSWORD); intervalos y reintentos de healthcheck; y que el alta de usuarios de RabbitMQ se genere al arrancar desde `.env` y se importe con la carga nativa de definiciones (cualquier alternativa idempotente vale si cumple las verificaciones). Mailpit no tiene versión fijada en la spec: su tag (sin `latest`) se elige al ejecutar y se anota en el PR.
- Esta capa no conoce identidad ni sedes: no define ni propaga cabeceras de identidad; el JWT lo validan el Gateway y cada servicio ([`03` §Headers](../especificacion/03-contrato-comun.md#headers)).
- Los contenedores de servicios y apps no se añaden aquí, y ningún plan `back`/`front` toca `infra/**` ([norma §6.4](README.md#6-contrato-con-la-ejecutora)): materializar el perfil completo de contenedores exige planificación adicional. Este plan deja listo el mecanismo del orden de arranque —healthchecks y `depends_on: condition: service_healthy`— que aplicarán los planes que añadan contenedores, en la cadena que fija [`configuracion.md` §Flyway y seeds](../especificacion/infraestructura/configuracion.md#flyway-y-seeds).
- Mailpit no bloquea negocio: el correo es efecto secundario y un SMTP caído solo degrada notification ([`05` §Datos sensibles, correo y orden](../especificacion/05-mensajeria.md#datos-sensibles-correo-y-orden)); por eso no ordena el arranque de nadie, pero recibe healthcheck propio.
- Sin secretos en Git: `.env` no se versiona y `.env.example` solo publica nombres y defaults no sensibles ([norma §6.10](README.md#6-contrato-con-la-ejecutora)); los valores se generan al ejecutar.

## 4. Pasos

- [ ] 1. Fijar y re-verificar las imágenes: `postgres:18.6`, `apache/kafka:4.3.1` y `rabbitmq:4.3.6-management` (versiones de [`01` §4](../especificacion/01-arquitectura.md#4-stack)), y elegir tag fijo (sin `latest`) para Mailpit. Verificación: `docker pull` o `docker manifest inspect` de las cuatro imágenes responde OK y el tag de Mailpit queda anotado en el PR.
- [ ] 2. Definir en `.env.example` los nombres de las variables nuevas y sus defaults no sensibles: POSTGRES_PASSWORD (superusuario), POSTGRES_<SERVICIO>_APP_PASSWORD y POSTGRES_<SERVICIO>_MIGRATOR_PASSWORD para las siete bases, y RABBITMQ_ADMIN_USER, RABBITMQ_ADMIN_PASSWORD y RABBITMQ_<SERVICIO>_PASSWORD para los seis servicios con Rabbit; crear el `.env` local con valores reales. Verificación: `git check-ignore -v .env` confirma que Git ignora el archivo, `.env.example` no contiene contraseñas y la lista de nombres coincide con los roles y usuarios de los pasos 5 y 8 (comparación adjunta al PR).
- [ ] 3. Crear `infra/compose.yaml`: nombre de proyecto `hotel`, red privada única `backend`, volúmenes nombrados `postgres_data`, `kafka_data` y `rabbitmq_data`, y publicaciones `127.0.0.1:55432→5432`, `127.0.0.1:5673→5672`, `127.0.0.1:15673→15672`, `127.0.0.1:29092→29092`, `127.0.0.1:1025→1025` y `127.0.0.1:8025→8025`; los cuatro servicios quedan sin `profiles` (modo infra por defecto) y el perfil `full` queda reservado para contenedores posteriores. Verificación: `docker compose -f infra/compose.yaml --env-file .env config` termina en 0, su salida muestra las seis publicaciones en loopback y los tres volúmenes, y `docker compose -f infra/compose.yaml --env-file .env config --services` lista los cuatro servicios sin activar perfiles.
- [ ] 4. Servicio `postgres`: `postgres:18.6`, volumen `postgres_data`, contraseña del superusuario desde `.env`, `restart: unless-stopped` y healthcheck con `pg_isready`. Verificación: `docker compose -f infra/compose.yaml --env-file .env up -d postgres` deja el contenedor healthy (`docker compose -f infra/compose.yaml --env-file .env ps --format json`) y `docker compose -f infra/compose.yaml --env-file .env exec postgres psql -U postgres -c "select version()"` reporta 18.6.
- [ ] 5. Añadir `infra/postgres/init/` con la creación de las siete bases (identity_db, hotel_db, reception_db, inventory_db, sales_db, reporting_db, notification_db) y sus roles runtime y migrador con contraseñas desde variables de entorno, revocando CONNECT a PUBLIC, cerrando el esquema `public` a PUBLIC, dejando al runtime sin CREATE y con privilegios por defecto de DML sobre lo que cree el migrador; sin tablas de negocio. Verificación con volumen recién creado: `psql -U postgres` lista las siete bases y los catorce roles; como runtime, `CREATE TABLE` falla por permisos y la conexión a otra base es rechazada; como migrador se crea una tabla de prueba, el runtime la lee y escribe, y luego se elimina (salidas adjuntas al PR).
- [ ] 6. Servicio `kafka`: `apache/kafka:4.3.1` en KRaft de un nodo (broker y controller), listener interno `kafka:9092`, listener externo anunciado `localhost:29092`, controller interno, una partición y factor de replicación 1 por defecto, retención de 7 días, volumen `kafka_data` y healthcheck que consulta el broker con las herramientas de la imagen. Verificación: el contenedor queda healthy; la descripción del broker devuelve los listeners esperados y los defaults de demo (1 partición, RF 1, retención 604800000 ms); y un contenedor efímero de la misma imagen contra `host.docker.internal:29092` (o el cliente Kafka local, si existe) obtiene la lista de APIs, lo que prueba el listener externo.
- [ ] 7. Servicio `rabbitmq`: `rabbitmq:4.3.6-management`, volumen `rabbitmq_data`, healthcheck con `rabbitmq-diagnostics -q ping` y publicaciones solo en loopback. Verificación: el contenedor queda healthy y la API de management (`curl` con las credenciales de administración a `http://localhost:15673/api/overview`) responde 200; la consola no responde fuera de 127.0.0.1.
- [ ] 8. Configurar el vhost `/hotel-demo`, un usuario de administración y un usuario por servicio con Rabbit (identity, hotel, reception, inventory, sales, notification) con permisos acotados a su papel productor/consumidor, como exige [`05` §Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit), y credenciales solo desde `.env`; el alta debe ser idempotente al arrancar el contenedor y no crea exchanges ni colas. Verificación: la API de management lista el vhost y los siete usuarios; los permisos de cada servicio permiten declarar y usar solo sus recursos; un usuario de servicio recibe permiso denegado al operar sobre recursos de otro servicio; reiniciar el contenedor no duplica usuarios ni falla.
- [ ] 9. Servicio `mailpit` con el tag fijo: SMTP `1025` y UI `8025` solo en loopback, y healthcheck con el subcomando `readyz` de la imagen. Verificación: el contenedor queda healthy, `curl http://localhost:8025/livez` responde 200 y una conexión TCP a `127.0.0.1:1025` es aceptada.
- [ ] 10. Cablear el orden de arranque: healthchecks en los cuatro contenedores y la convención `depends_on: condition: service_healthy` documentada para los contenedores que se añadan después, siguiendo la cadena de [`configuracion.md` §Flyway y seeds](../especificacion/infraestructura/configuracion.md#flyway-y-seeds). Verificación: `docker compose -f infra/compose.yaml --env-file .env up -d --wait` termina en 0 con los cuatro healthy y el compose deja anotada la cadena para los planes siguientes.
- [ ] 11. Confirmar persistencia: `restart: unless-stopped` en los cuatro servicios y `down` sin `-v` conserva los datos. Verificación: tras crear una tabla de prueba con el migrador, `docker compose -f infra/compose.yaml --env-file .env down` y de nuevo `up -d --wait`, la tabla sigue existiendo; `docker volume ls` lista los tres volúmenes con nombre (salida adjunta al PR).
- [ ] 12. Actualizar la sección «Verificación» del `README.md` raíz: requisito de copiar `.env.example` a `.env` y completar credenciales, y los tres comandos de Compose con `-f infra/compose.yaml --env-file .env` (`config`, `up -d --wait` y `down`). Verificación: los tres comandos se ejecutan desde la raíz en verde y la sección no documenta ningún otro comando de Compose.
- [ ] 13. Corrida de aceptación desde limpio: `docker compose -f infra/compose.yaml --env-file .env down -v` y `up -d --wait`, repitiendo en una sola sesión las comprobaciones de los pasos 5 a 9. Verificación: todas las salidas esperadas quedan adjuntas al PR (bases y roles, listener externo de Kafka, management de Rabbit y livez de Mailpit).
- [ ] 14. Correr `python scripts/validar-planes.py` desde la raíz. Verificación: 0 errores; cualquier aviso queda resuelto o justificado en el PR.

## 5. Contratos afectados

- OpenAPI: ninguno; no se tocan los YAML de `docs/contracts/openapi/`.
- Eventos y comandos: ninguno; el catálogo no asigna mensajería a este plan y aquí no se declara ningún topic, comando ni grupo de consumidor. En RabbitMQ solo quedan vhost, usuarios y permisos sobre los nombres que ya fija [`05`](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit); exchanges, colas, DLT y DLQ son de `back-plataforma-mensajeria`.
- Persistencia: ninguna tabla ni migración Flyway; `infra/postgres/init/` crea bases y roles, no esquema de negocio ([`01` §1](../especificacion/01-arquitectura.md#1-monorepo-sí)).
- Errores: ninguno; esta capa no expone HTTP de negocio ni códigos de [`errors.md`](../contracts/errors.md).
- Entorno y documentación: nombres de variables en `.env.example` y comandos de Compose de la sección «Verificación» del `README.md` raíz ([norma §6.4](README.md#6-contrato-con-la-ejecutora)).

## 6. Pruebas

No hay casos de la [matriz de aceptación](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) asignados a este plan (`Casos: —`). La evidencia sale de los comandos canónicos del `README.md` raíz y de las comprobaciones de los pasos, adjunta al PR de ejecución:

- `docker compose -f infra/compose.yaml --env-file .env config`: resultado esperado 0, con las publicaciones en loopback y los volúmenes; evidencia: salida adjunta.
- `docker compose -f infra/compose.yaml --env-file .env up -d --wait`: resultado esperado 0 y los cuatro contenedores healthy en `ps`; evidencia: salida adjunta.
- PostgreSQL: siete bases y catorce roles, runtime sin DDL y sin acceso cruzado, migrador con DDL y privilegios por defecto; evidencia: salidas de psql del paso 5.
- Kafka: listeners de la tabla de puertos, defaults de demo y acceso externo por 29092; evidencia: paso 6.
- RabbitMQ: management 200, vhost y siete usuarios con permisos acotados, prueba negativa entre servicios e idempotencia al reiniciar; evidencia: paso 8.
- Mailpit: livez 200 y puerto SMTP aceptando conexiones; evidencia: paso 9.
- Persistencia: `down` conserva los volúmenes y `down -v` los restablece; evidencia: paso 11.
- Validador: `python scripts/validar-planes.py`; resultado esperado 0 errores; evidencia: salida adjunta.
- El resto del criterio de F1 (401 sin token y rutas internas no expuestas) es de `infra-gateway-eureka`, no de este plan.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] `docker compose -f infra/compose.yaml --env-file .env up -d --wait` deja los cuatro contenedores healthy desde un clon limpio y volúmenes vacíos.
- [ ] Las siete bases y sus roles quedan creados con aislamiento verificado (runtime sin DDL ni acceso cruzado; migrador con DDL).
- [ ] Kafka y RabbitMQ arrancan con los defaults de demo y los usuarios de broker con credenciales propias; Mailpit responde; publicaciones solo en 127.0.0.1 y sin secretos en Git.
- [ ] Evidencia de la sección 6 adjunta al PR de ejecución y `python scripts/validar-planes.py` sin errores.
- [ ] El plan pasa a `cerrado` en el último PR del plan ([norma §6.9](README.md#6-contrato-con-la-ejecutora)).
- [ ] Sin preguntas abiertas.
