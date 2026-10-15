# infra-monorepo — fundación del monorepo (F0)

| Campo | Valor |
|---|---|
| Plan | `infra-monorepo` |
| Ámbito | `infra` |
| Componente | `infra` |
| Fase | `F0` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `—` |
| Spec | [`01-arquitectura.md` §1 Monorepo](../especificacion/01-arquitectura.md#1-monorepo-sí) y [§4 Stack](../especificacion/01-arquitectura.md#4-stack); [`gateway-eureka-docker.md` §Compose y puertos propuestos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos) y [§Construcción y arranque futuro](../especificacion/infraestructura/gateway-eureka-docker.md#construcción-y-arranque-futuro); [`configuracion.md` §Comunes a todos los servicios](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) y [§Por servicio](../especificacion/infraestructura/configuracion.md#por-servicio); [`06-migracion-y-pruebas.md` §2](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) y [§6](../especificacion/06-migracion-y-pruebas.md#6-doble-validación-antes-de-implementar-y-antes-de-entregar); [`07-revision-y-arranque.md` §Primer incremento](../especificacion/07-revision-y-arranque.md#primer-incremento-implementable) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

El monorepo de [§1](../especificacion/01-arquitectura.md#1-monorepo-sí) existe y compila en vacío con las versiones fijadas (Java 25, Boot 4.1.1, Cloud 2025.1.3, Angular 22.2.2): el build Maven de los nueve módulos y el de las dos apps Angular terminan en verde, los PRs pasan la CI mínima y el `README.md` raíz publica los comandos canónicos de verificación.

## 2. Alcance

**Entra:**

- El árbol raíz de [§1](../especificacion/01-arquitectura.md#1-monorepo-sí): `apps/`, `services/`, `infra/` (subcarpetas `postgres/init/`, `rabbitmq/`, `kafka/`, `nginx/`), `tests/` (`postman/`, `integration/`) y `.gitignore`.
- `services/pom.xml`: agregador Maven con las versiones fijadas y los nueve módulos como esqueletos mínimos que compilan (siete servicios de dominio más `api-gateway` y `discovery-server`).
- Maven Wrapper en la raíz (`mvnw`, `mvnw.cmd` y `.mvn/wrapper/`).
- Las dos apps Angular 22.2.2 (`apps/public-web`, `apps/admin-web`) con `package-lock.json` comprometido.
- `.env.example` en la raíz con los nombres de variables de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) y defaults no sensibles.
- CI mínima en `.github/workflows/ci.yml` y `README.md` raíz con la sección «Verificación» (comandos canónicos de la norma [§6.7](README.md#6-contrato-con-la-ejecutora)).

**Fuera de alcance:**

- `infra/compose.yaml`, init de PostgreSQL, Kafka, RabbitMQ, Mailpit y healthchecks de arranque → plan `infra-compose` (F1).
- Rutas, rate limit, CORS y timeouts del Gateway, y Eureka → plan `infra-gateway-eureka` (F1).
- Seguridad común (JWT validado por servicio, envelope, idempotencia, health y logs) → plan `back-plataforma-comunes` (F1); este plan no introduce ninguna cabecera de identidad.
- Outbox/inbox y mensajería: el catálogo no asigna a este plan ningún topic, comando ni grupo; no se crea nada de ese alcance.
- Contratos OpenAPI y JSON Schemas: ya existen en `docs/contracts/` y este plan no los crea ni los edita.
- Pantallas, rutas de UI y estilos → planes `front-*`; migraciones Flyway y seeds → planes de servicio; Dockerfiles, colecciones Postman y runbooks → fases siguientes ([`06` §7](../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación)).
- Alcance cerrado de la [especificación](../especificacion/README.md#alcance-cerrado): sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.

## 3. Contexto

- La documentación (especificación, planes y contratos) vive en `docs/` y no se duplica: este plan materializa en la raíz solo el árbol de código de [§1](../especificacion/01-arquitectura.md#1-monorepo-sí) (`apps/`, `services/`, `infra/`, `tests/`); `scripts/` ya existe.
- Este plan es el paso 1 del [orden de implementación](../especificacion/06-migracion-y-pruebas.md#2-orden-simple-elegido) y deja la base que consumen `infra-compose` y toda la fase F1. Criterio de salida F0 ([norma §2](README.md#2-fases)): compila vacío con versiones fijadas y los PRs pasan CI.
- Las versiones salen de [§4](../especificacion/01-arquitectura.md#4-stack): Java 25, Boot 4.1.1, Cloud 2025.1.3, Angular 22.2.2, Node en el rango admitido por Angular 22.2.2 y TypeScript en el rango fijado; sin `latest`. La re-verificación de compatibilidad de Cloud 2025.1.x se hace antes de fijar el agregador (paso 1). Los comandos concretos de CI y build se fijan aquí porque [`06` §6](../especificacion/06-migracion-y-pruebas.md#6-doble-validación-antes-de-implementar-y-antes-de-entregar) los deja «al crear módulos».
- Decisiones de este plan (la spec no las fija): CI como GitHub Actions sobre pushes a `main` y PRs a `main` (es el hospedaje del repositorio; [`06` §6](../especificacion/06-migracion-y-pruebas.md#6-doble-validación-antes-de-implementar-y-antes-de-entregar) fija qué ejecuta); Node 24.15.0 como el rango admitido que fijan lockfiles y CI; groupId común `pe.hotel`; wrappers en la raíz del repo, invocados con `-f services/pom.xml`.
- Los esqueletos nacen sin lógica de negocio: no definen endpoints, errores, salud ni seguridad, y no propagan identidad por cabeceras — esa convención llega con [`03` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes) y los planes de F1.
- La sección «Verificación» del `README.md` raíz se publica ya con los comandos de build, pruebas, Compose y validador aunque `infra/compose.yaml` nazca en F1: la norma [§6.7](README.md#6-contrato-con-la-ejecutora) la fija como el listado canónico que citan los demás planes.

## 4. Pasos

- [ ] 1. Re-verificar la compatibilidad Boot 4.1.1 ↔ Cloud 2025.1.3 contra la tabla oficial que cita [`01` §4](../especificacion/01-arquitectura.md#4-stack) y anotar el resultado en el PR de ejecución. Verificación: el PR enlaza la tabla y registra «sin cambio» o un ajuste dentro de la línea 2025.1.x; un cambio que exija salir de la línea fijada deja el paso sin marcar y se registra en Preguntas abiertas.
- [ ] 2. Crear el árbol raíz de [§1](../especificacion/01-arquitectura.md#1-monorepo-sí): `apps/`, `services/`, `infra/` con `postgres/init/`, `rabbitmq/`, `kafka/`, `nginx/`, y `tests/` con `postman/`, `integration/`, con `.gitkeep` donde queden vacías. Verificación: `git ls-files` lista esas carpetas y `infra/compose.yaml` no existe (lo crea `infra-compose`).
- [ ] 3. Generar el Maven Wrapper en la raíz con `distributionUrl` fijado a un patch exacto de Maven 3.9.x (sin `latest`) y bit de ejecución en git. Verificación: `./mvnw -v` (y `mvnw.cmd -v`) imprime la versión fijada con Java 25, y `git ls-files -s mvnw` muestra modo ejecutable.
- [ ] 4. Crear `services/pom.xml` (agregador, packaging `pom`): groupId `pe.hotel`, properties con Java 25, `dependencyManagement` importando `spring-boot-dependencies` 4.1.1 y `spring-cloud-dependencies` 2025.1.3, y `pluginManagement` con compiler release 25 y `spring-boot-maven-plugin` 4.1.1; sin módulos aún. Verificación: `./mvnw -f services/pom.xml validate` termina en BUILD SUCCESS.
- [ ] 5. Crear los nueve esqueletos — `identity-service`, `hotel-service`, `reception-service`, `inventory-service`, `sales-service`, `reporting-service`, `notification-service`, `api-gateway` y `discovery-server` — con pom hijo que hereda versiones, clase principal anotada, `application.yml` con `spring.application.name` y `server.port` por defecto según la [tabla de puertos](../especificacion/infraestructura/gateway-eureka-docker.md#compose-y-puertos-propuestos), dependencias mínimas por tipo (web; gateway reactivo; discovery Eureka) y `src/main/resources/db/migration/` vacío en los servicios de dominio; añadir sus entradas a `<modules>`. Verificación: `./mvnw -f services/pom.xml clean verify` en BUILD SUCCESS para los nueve módulos y `spring.application.name` coincide con los nombres de servicio que usan los defaults de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios).
- [ ] 6. Crear `apps/public-web` con el CLI de Angular 22.2.2 (`ng new`, sin SSR ni git anidado, con las pruebas por defecto y sin pantallas de negocio); fijar los paquetes `@angular/*` y TypeScript en versión exacta (TypeScript dentro de `>=6.0 <6.1`), declarar en `engines` el rango de Node de [§4](../especificacion/01-arquitectura.md#4-stack), dejar el puerto de desarrollo 4200 y comprometer `package-lock.json`. Verificación: `npm ci && npm run build && npm test -- --watch=false` en verde y `npm ls @angular/core typescript` sin `latest` ni rangos flotantes.
- [ ] 7. Repetir el paso 6 en `apps/admin-web`, con puerto de desarrollo 4300. Verificación: los mismos comandos en verde en esa app y el puerto 4300 visible en su configuración de `serve`.
- [ ] 8. Crear `.env.example` en la raíz con los nombres de todas las variables de las tablas de [`configuracion.md`](../especificacion/infraestructura/configuracion.md#comunes-a-todos-los-servicios) (comunes y por servicio) y defaults no sensibles; las claves y secretos quedan sin valor. Verificación: la lista de nombres del archivo coincide con las dos tablas (comparación adjunta al PR) y ninguna credencial real aparece en él.
- [ ] 9. Crear `.gitignore` raíz para Java/Maven, Node/Angular, entorno y editores (`target/`, `node_modules/`, `dist/`, `.angular/`, `.env`, IDE). Verificación: tras compilar y probar, `git status --porcelain` no lista artefactos y `git check-ignore` confirma las rutas típicas de salida.
- [ ] 10. Crear `README.md` raíz con una descripción breve y la sección «Verificación» con los comandos canónicos: build backend (`./mvnw -f services/pom.xml verify` y variante Windows), pruebas de cada app (`npm ci && npm run build && npm test -- --watch=false` en `apps/public-web` y `apps/admin-web`), Compose (`docker compose -f infra/compose.yaml config`, `up -d` y `down`) y validador (`python scripts/validar-planes.py`). Verificación: la sección existe con los cuatro grupos; los tres comandos ejecutables en F0 terminan en verde desde un clon limpio y Compose queda publicado como canónico para F1.
- [ ] 11. Crear la CI mínima en `.github/workflows/ci.yml` con tres jobs — backend (Temurin 25, cache Maven, `./mvnw -f services/pom.xml verify`), frontend (Node 24.15.0, matriz con las dos apps: `npm ci`, `npm run build`, `npm test -- --watch=false`) y planes (`python scripts/validar-planes.py`) — disparados en push a `main` y en PRs a `main`. Verificación: en la rama de ejecución los tres jobs aparecen en verde y usan exactamente los comandos de la sección «Verificación».
- [ ] 12. Verificación final desde un clon limpio: `npm ci` en ambas apps, `./mvnw -f services/pom.xml clean verify` y `python scripts/validar-planes.py`. Verificación: todo en verde y 0 errores del validador; el log queda adjunto al PR de ejecución.

## 5. Contratos afectados

- OpenAPI: ninguno; no se tocan los YAML de `docs/contracts/openapi/`.
- Eventos y comandos: ninguno; el catálogo no asigna mensajería a este plan.
- Persistencia: ninguna tabla ni migración; los servicios de dominio nacen con `db/migration` vacío.
- Errores: ninguno; no se implementan envelope ni códigos de [`errors.md`](../contracts/errors.md) (llegan con F1).
- Estructura y comandos: crea el árbol de [§1](../especificacion/01-arquitectura.md#1-monorepo-sí) y publica en el `README.md` raíz los comandos canónicos que citan los demás planes ([norma §6.7](README.md#6-contrato-con-la-ejecutora)).

## 6. Pruebas

No hay casos de la [matriz de aceptación](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) asignados a este plan (`Casos: —`). La evidencia son los comandos canónicos que este plan publica:

- Build backend: `./mvnw -f services/pom.xml verify`; resultado esperado BUILD SUCCESS de los nueve módulos; evidencia en el job backend de CI y adjunto al PR.
- Build y pruebas de las apps: `npm ci && npm run build && npm test -- --watch=false` en `apps/public-web` y `apps/admin-web`; resultado esperado verde; evidencia en el job frontend de CI.
- Validador: `python scripts/validar-planes.py`; resultado esperado 0 errores; evidencia en el job planes de CI.
- Compose: `docker compose -f infra/compose.yaml config`; documentado como canónico y ejecutable a partir de `infra-compose` (F1).

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Los nueve módulos Maven y las dos apps Angular compilan en vacío con las versiones fijadas y lockfiles comprometidos.
- [ ] CI mínima en verde en los PRs (`main` actualizado) y `python scripts/validar-planes.py` sin errores.
- [ ] Evidencia de los comandos de la sección 6 adjunta al PR de ejecución.
- [ ] Sin preguntas abiertas.
