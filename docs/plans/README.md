# Planes de ejecución

## Qué es esta carpeta

Aquí viven los planes de ejecución: documentos cortos que explican, antes de escribir código, qué hay que hacer, en qué orden y cómo se sabe que terminó.

Un plan no es código ni documentación del sistema. Es la hoja de ruta de una tarea concreta: la escribe quien la va a hacer, la usa mientras trabaja y se archiva cuando la tarea se cierra.

> La especificación dice qué es el sistema. El plan dice cómo lo construimos paso a paso.
> Spec en [`../especificacion`](../especificacion), contratos en [`../contracts`](../contracts), planes en esta carpeta.

Este documento es la **norma del agente planificador**: cualquier plan generado (por una IA o por una persona) debe cumplir lo que sigue. Un plan que no cumple la estructura de §6, el proceso de §5 y las reglas de §7 no se acepta en PR.

## 1. Ámbito: back, front, infra o qa

Cada plan cubre un solo ámbito, y su nombre de archivo lo declara:

| Ámbito | Nombre del archivo | Qué cubre | Dónde vive el código |
|---|---|---|---|
| Backend | `back-<servicio>-<tema>.md` | Un microservicio Spring Boot (`identity`, `hotel`, `reception`, `inventory`, `sales`, `reporting`, `notification`) | `services/<servicio>-service/` |
| Frontend | `front-<app>-<tema>.md` | Una app Angular: `public-web` (landing y catálogo público) o `admin-web` (panel A/E y área cliente C) | `apps/<app>/` |
| Infraestructura | `infra-<tema>.md` | Compose, PostgreSQL, brokers, Gateway, Eureka, migraciones | `infra/`, `services/api-gateway/`, `services/discovery-server/` |
| Calidad | `qa-<tema>.md` | Matriz de aceptación, fixtures, runbooks ejecutados, simulacros | `tests/` |

Ejemplos: `back-identity-auth.md`, `front-admin-web-recepcion.md`, `infra-compose.md`, `qa-matriz-aceptacion.md`. Para piezas compartidas por varios servicios se usa `plataforma` como servicio: `back-plataforma-mensajeria.md`.

Una funcionalidad que necesita back y front son **dos planes enlazados** (pueden viajar en un mismo PR de funcionalidad). Un plan nunca mezcla dos servicios de back ni dos apps de front.

## 2. Fases de implementación

Las fases equivalen a los 6 pasos del [primer incremento implementable](../especificacion/07-revision-y-arranque.md) y toman sus casos de la [matriz de aceptación](../especificacion/06-migracion-y-pruebas.md). No se abre una fase sin cumplir el criterio de salida de la anterior.

| Fase | Nombre | Objetivo | Criterio de salida | Modo por defecto |
|---|---|---|---|---|
| F0 | Fundación | Monorepo, versiones fijadas, plantillas y CI | Compila vacío con versiones pinneadas; los PRs pasan CI | `directo` |
| F1 | Plataforma | Compose (PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6, Mailpit), Gateway/Eureka, seguridad común y mensajería fiable | `compose up` con healthchecks verdes; Gateway enruta y devuelve 401 sin token; rutas internas no expuestas; configuración de [`configuracion.md`](../especificacion/infraestructura/configuracion.md) aplicable | `directo`, con `openspec` en mensajería |
| F2 | Dominio base | identity y hotel con seeds; landing pública; login/layout y CRUD del panel | Login, aislamiento por sede, último ADMIN, catálogo público y CRUD operativos; eventos de identity/hotel publicados por outbox | `directo` |
| F3 | Operación | reception (gate/check-in), inventory y sales (consumo con débito) y estado físico/limpieza de hotel | Check-in y venta PAGADA con débito y compensación probados; mantenimiento 200/202 y limpieza visibles en el panel | `openspec` en flujos críticos |
| F4 | Cierre y asíncronos | Cierre de estadía con cobro, área de cliente, notification y proyecciones de reporting | Cierre completo con penalidad y cobro; correos demo; reportes al día con desglose por medio de pago | `openspec` en flujos críticos |
| F5 | Aceptación | Matriz completa, runbooks y endurecimiento | Matriz de ~41 casos verde con evidencia; simulacros de DLQ, cierre atascado y restauración | `directo` |

Los planes de F2 en adelante pueden solaparse dentro de la misma fase (p. ej. hotel y landing), pero no saltar de fase sin su dependencia.

## 3. Modo del plan: directo u OpenSpec

- **`directo`**: la especificación ya fija el contrato completo (endpoints, pantallas, validaciones, configuración) y el plan es una checklist de implementación. Es el modo por defecto.
- **`openspec`**: la tarea depende de estados persistidos, concurrencia, dinero/stock o varios servicios, y equivocarse es caro. Se escribe primero una propuesta OpenSpec con deltas de especificación y `tasks.md`, se revisa y recién se implementa.

Se recomienda `openspec` cuando la tarea cumple dos o más de:

1. Toca dos o más servicios (Feign, eventos o comandos).
2. Mantiene una máquina de estados persistida (CAS, rechazos durables, lápidas).
3. Mueve dinero o stock (idempotencia, compensación, comparación exacta).
4. Depende de fiabilidad de mensajería (outbox/inbox, reintentos, DLT/DLQ, orden por agregado).
5. Depende de concurrencia (locks, versiones, entrega duplicada).
6. Proyecta o migra datos (watermarks, bootstrap, consistencia eventual).

El catálogo de la sección 4 marca el modo recomendado de cada plan. El modo por defecto de una fase no obliga: un plan puede promoverse a `openspec` si al escribirlo aparecen estos riesgos.

Los planes `openspec` no duplican la spec en dos sitios: el plan de esta carpeta fija objetivo, alcance, contratos y casos, y delega el detalle en `openspec/changes/<plan>/` (proposal, specs delta, design, tasks). El flujo es proponer → aplicar → verificar → archivar; al archivar, los deltas se consolidan. Antes del primer plan `openspec` (F1, mensajería) se inicializa OpenSpec en la raíz del repo (`openspec init`) y se comprueba que no pise las reglas de [`AGENTS.md`](../../AGENTS.md).

## 4. Catálogo de planes previstos

Estos archivos aún no existen: son la ruta de trabajo y se crean al abrir cada fase. "Depende de" se refiere a otros planes del catálogo.

| Fase | Plan | Ámbito | Modo | Contenido y dependencias |
|---|---|---|---|---|
| F0 | `infra-monorepo.md` | infra | `directo` | Estructura de la [arquitectura](../especificacion/01-arquitectura.md) §1: Maven agregador, wrappers, Angular 22.2.2, versiones fijadas, `.env.example`, CI. |
| F1 | `infra-compose.md` | infra | `directo` | Compose con PostgreSQL (7 bases vía `postgres/init`), Kafka KRaft, RabbitMQ + management, Mailpit, healthchecks y orden de arranque. |
| F1 | `infra-gateway-eureka.md` | infra | `directo` | Rutas del Gateway, rate limits (60/min; login 5/min; registro 3/min), timeouts, CORS, rutas LEGACY y Eureka. Depende de `infra-compose`. |
| F1 | `back-plataforma-comunes.md` | back | `directo` | Resource server JWT RS256, token máquina por scopes en `/internal/**`, envelope de errores y correlation id, patrón de idempotencia. |
| F1 | `back-plataforma-mensajeria.md` | back | `openspec` | Outbox/inbox: poller 5 s/lote 50, backoff, retención 30 días, orden por agregado, envelopes y JSON Schemas, DLT/DLQ. |
| F2 | `back-identity-auth.md` | back | `directo` | Login JWT, registro público, `/api/auth/me`, cambio de contraseña (Idempotency-Key + HMAC), BCrypt y rate limits. |
| F2 | `back-identity-personas.md` | back | `directo` | Personas, roles, sedes asignadas, último ADMIN, auditoría `PERSONA_SEARCH`/`PERSONA_READ`, retención 5 años, `PersonaSnapshot`. |
| F2 | `back-hotel-catalogo.md` | back | `directo` | Sedes, pisos, categorías, habitaciones con imágenes, slug, catálogo público `/api/public/**`, snapshots de sede/habitación. |
| F2 | `front-public-web.md` | front | `directo` | Landing: inicio, catálogo informativo, contacto, privacidad, textos/assets demo y locale es-PE. Depende de `back-hotel-catalogo`. |
| F2 | `front-admin-web-base.md` | front | `directo` | Login/registro, guards por rol, layout y menú, interceptor (Idempotency-Key, ETag, 401), catálogo de errores en UI, pipes moneda/fecha. |
| F2 | `front-admin-web-catalogo.md` | front | `directo` | Pantallas de personas/roles y sedes/pisos/categorías/habitaciones con formularios, validaciones y estados de [`08-admin-web.md`](../especificacion/08-admin-web.md). |
| F3 | `back-reception-operacion.md` | back | `openspec` | `room_gate`, check-in con adelanto y medio de pago, snapshots, editar salida, campo `vencida` y listados. |
| F3 | `back-inventory-stock.md` | back | `openspec` | Productos por sede, ajustes, movimientos, admisión/débito/restitución con locks e idempotencia, `PRODUCT_INACTIVE` y umbral bajo. |
| F3 | `back-sales-ventas.md` | back | `openspec` | Carrito, venta PENDIENTE→PAGADA, pago con medio, débito a inventory y compensación, receipts y anulación impagada. Depende de `back-inventory-stock` y mensajería. |
| F3 | `back-hotel-operaciones.md` | back | `openspec` | Mantenimiento/bloqueo (200/202/409, blockId, reintentos) y ciclo de limpieza con comando Rabbit. Depende del gate de reception. |
| F3 | `front-admin-web-recepcion.md` | front | `directo` | Estadías, check-in, detalle y limpieza; estados 202/409 con polling. Depende de los back de F3. |
| F3 | `front-admin-web-ventas.md` | front | `directo` | Productos, movimientos, ventas, pago y carrito. Depende de los back de F3. |
| F4 | `back-reception-cierre.md` | back | `openspec` | Cotización, cierre VALIDANDO→CONFIRMANDO→COMPLETADO, cálculo congelado, penalidad, `montoRecibido`, anulación de check-in y disparo de limpieza. |
| F4 | `back-notification-worker.md` | back | `openspec` | Inbox, trabajos, lease SMTP con Mailpit, reintentos acotados, purga y CLI de operador auditada. |
| F4 | `back-reporting-proyecciones.md` | back | `openspec` | Bootstrap con manifiesto/watermarks, proyecciones de topics, dashboard, `ingresosHoy` (3 componentes), cobros por medio y bajo stock. |
| F4 | `front-admin-web-reportes.md` | front | `directo` | Dashboard, reportes y exportación; aviso de consistencia eventual y degradado por 503. Depende de `back-reporting-proyecciones`. |
| F4 | `front-admin-web-area-cliente.md` | front | `directo` | Mi estadía, catálogo, carrito, mis ventas y mi perfil (rol C). Depende de los back de F3/F4. |
| F5 | `qa-matriz-aceptacion.md` | qa | `directo` | Ejecutar la matriz completa con fixtures, evidencias y las correcciones de spec que salgan. Depende de F1–F4. |
| F5 | `qa-runbooks.md` | qa | `directo` | Simulacros de DLQ, cierre CONFIRMANDO, operación PREPARANDO y outbox atrasado; backup y restauración. |

## 5. Proceso del agente planificador

Este README es el contrato que sigue quien redacte un plan (humano o IA). El flujo es:

1. **Elegir** el plan del catálogo (§4) y revisar su pendiente: ámbito, fase, modo y dependencias.
2. **Leer lo obligatorio** para su ámbito (tabla siguiente). Un plan redactado sin esa lectura se rechaza si contradice la spec.
3. **Redactar** con la estructura de §6 y en el modo que fija §3.
4. **Autorevisar** con el checklist de esta sección; cada punto se verifica con un enlace o un grep sobre el repo.
5. **Publicar** en `docs/plans/` con estado `propuesto` y abrir PR según [`../guia-git.md`](../guia-git.md).

### Lectura obligatoria por ámbito

| Ámbito | Siempre | Además |
|---|---|---|
| Todos | este README, [`../especificacion/README.md`](../especificacion/README.md), [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md), [`../contracts/errors.md`](../contracts/errors.md) | — |
| `back` | `microservicios/<servicio>/api.md` y `base-de-datos.md`, `contracts/openapi/<servicio>.yaml`, [`05-mensajeria.md`](../especificacion/05-mensajeria.md) si emite o consume mensajes, [`06-migracion-y-pruebas.md`](../especificacion/06-migracion-y-pruebas.md) (casos y fixtures de su dominio) | [`04-flujos.md`](../especificacion/04-flujos.md) si toca check-in, venta/stock, cierre, limpieza o mantenimiento; `contracts/events/` si publica o consume eventos |
| `front` | [`02-landing-y-panel.md`](../especificacion/02-landing-y-panel.md), [`08-admin-web.md`](../especificacion/08-admin-web.md), [`../contracts/README.md`](../contracts/README.md) (sección de su módulo), `contracts/openapi/admin-web.yaml` o `public-web.yaml` | [`06-migracion-y-pruebas.md`](../especificacion/06-migracion-y-pruebas.md) §4 (cambios frontend obligatorios) |
| `infra` | [`01-arquitectura.md`](../especificacion/01-arquitectura.md), [`gateway-eureka-docker.md`](../especificacion/infraestructura/gateway-eureka-docker.md), [`configuracion.md`](../especificacion/infraestructura/configuracion.md) | [`06-migracion-y-pruebas.md`](../especificacion/06-migracion-y-pruebas.md) §2 (orden de implementación) |
| `qa` | [`06-migracion-y-pruebas.md`](../especificacion/06-migracion-y-pruebas.md), [`runbooks.md`](../especificacion/infraestructura/runbooks.md) | contratos de los flujos que cubre |
| Modo `openspec` | además, la sección de `04-flujos.md` o `05-mensajeria.md` que describe su máquina de estados o su entrega fiable | [`../contracts/events/README.md`](../contracts/events/README.md) y los JSON Schema del flujo |

Los archivos `microservicios/*/api.md` y `base-de-datos.md` son el detalle por servicio (endpoints, tablas, DDL de referencia, esquemas JSONB y seeds); se leen por plan, no de una sola vez.

### Checklist de validación (antes del PR)

- [ ] Cabecera completa: plan, ámbito, componente, fase, modo, estado, dependencias, spec, casos.
- [ ] Todo enlace relativo del plan resuelve (sin rutas rotas).
- [ ] Cada endpoint citado existe en el OpenAPI de su servicio o en [`../contracts/README.md`](../contracts/README.md).
- [ ] Cada código de error citado existe en [`../contracts/errors.md`](../contracts/errors.md).
- [ ] Cada ID de caso y fixture citado existe en la matriz de [`06-migracion-y-pruebas.md`](../especificacion/06-migracion-y-pruebas.md).
- [ ] Cada topic, cola, evento o comando citado existe en [`05-mensajeria.md`](../especificacion/05-mensajeria.md) y en [`../contracts/events/`](../contracts/events/).
- [ ] El modo `openspec` se aplica si se cumplen ≥ 2 criterios de §3, o se explica por qué no.
- [ ] Alcance de un solo servicio (`back`) o una sola app (`front`); dependencias listadas.
- [ ] Sin alcance ni tecnología fuera de [`01-arquitectura.md`](../especificacion/01-arquitectura.md) y del alcance cerrado de la spec.
- [ ] Nada aparece como "probado" o "ejecutado": las evidencias se adjuntan al cerrar el plan.

## 6. Estructura obligatoria de un plan

### Cabecera (tabla al inicio)

| Campo | Regla |
|---|---|
| Plan | Igual al nombre del archivo sin `.md`: `back-identity-auth` |
| Ámbito | `back`, `front`, `infra` o `qa` |
| Componente | Servicio o app exactos: `identity-service`, `admin-web`, `infra`… |
| Fase | `F0`…`F5` |
| Modo | `directo` u `openspec` |
| Estado | `propuesto`, `en curso` o `cerrado` |
| Depende de | Planes previos por archivo; `—` si no hay |
| Spec | Enlaces a las secciones concretas, no solo al documento |
| Casos | IDs de la matriz de aceptación que cubre (p. ej. `SEC01`, `OUT05`) |
| Change | Solo en modo `openspec`: carpeta del change (`openspec/changes/<plan>/`) |

### Secciones, en este orden

1. **Objetivo** — resultado verificable en 1–2 líneas.
2. **Alcance y fuera de alcance** — endpoints, pantallas o eventos que entran; y lo que queda fuera explícitamente.
3. **Contexto y decisiones** — lo que la spec ya fija y lo que este plan decide (poco: la spec manda).
4. **Pasos** — checklist ordenado y verificable. En modo `openspec`, aquí van los hitos y el detalle fino vive en el `tasks.md` del change.
5. **Contratos afectados** — OpenAPI (`contracts/openapi/`), schemas de eventos (`contracts/events/`), DDL/JSONB, códigos de [`errors.md`](../contracts/errors.md). Se actualizan en el mismo PR.
6. **Pruebas y evidencia** — pruebas por ID de caso, fixtures usados y qué se adjunta al PR (salidas, capturas, logs con `correlationId`).
7. **Criterio de terminado** — lista corta y objetiva; el plan se cierra con el PR mergeado y la matriz actualizada.

## 7. Reglas al escribir planes

- La spec manda: no inventar endpoints, eventos ni reglas. Si falta algo, se corrige la spec en el mismo PR (contrato, BD, flujo y prueba relacionada).
- La identidad nunca se propaga por headers (`X-User-Id`, `X-Role`, `X-Hotel-Id`): cada servicio la obtiene del JWT validado (algoritmo/issuer/audience/exp/scope), según [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md). No usar ejemplos antiguos que propagaban esos headers.
- Los formatos de UI (moneda, fechas), los mensajes de error y las validaciones salen de [`02-landing-y-panel.md`](../especificacion/02-landing-y-panel.md), [`08-admin-web.md`](../especificacion/08-admin-web.md) y [`errors.md`](../contracts/errors.md); el plan no los redefine.
- Cada plan cita los IDs de la matriz de aceptación que cubre; nada se marca como ejecutado por estar escrito (la evidencia se adjunta al cerrar).
- Los planes de front consumen el Gateway (`localhost:8080`) y se cierran con integración real; pueden avanzar contra `contracts/openapi/*.yaml` con mocks, nunca contra puertos de servicios.
- Sin secretos reales en planes ni ejemplos: solo nombres de variables (`DEMO_PASSWORD`, `JWT_JWK_SET_URI`…).
- Alcance cerrado: no se proponen reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server. Si un plan los necesitara, se escala como decisión de spec antes de continuar.
- Versiones: solo las fijadas en [`01-arquitectura.md`](../especificacion/01-arquitectura.md) (Java 25, Boot 4.1.1, Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6), sin `latest`; re-verificar la compatibilidad de Spring Cloud en cada patch de 2025.1.x.
- No copiar bloques largos de la spec en el plan: enlaza la sección exacta. El plan aporta orden, alcance, dependencias y criterios; la spec aporta el contenido.
- Rama y PR siguen [`../guia-git.md`](../guia-git.md): una rama por plan en minúsculas con guiones (p. ej. `identity-auth`), nunca commit directo a `main` y PR revisado por alguien más.

## 8. Ciclo de vida

- Activo: está en esta carpeta y en trabajo.
- Cerrado: se mueve a `archivados/` o se elimina tras el merge del PR que lo implementó. Esta carpeta solo contiene trabajo pendiente o en curso.
- Si al cerrar aparece trabajo nuevo (fuera de alcance o una corrección), se escribe un plan nuevo; el cerrado no se reabre.
