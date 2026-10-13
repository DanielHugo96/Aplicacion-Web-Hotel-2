# Catálogo de planes

Inventario de los planes previstos, con sus dependencias, dueños de mensajes, endpoints y casos de aceptación. Lo valida `scripts/validar-planes.py` y lo rige la norma de [`README.md`](README.md). Todo cambio en un plan se refleja aquí en el mismo PR.

## 1. Planes

Los planes `front-*` planifican con [`../DESIGN.md`](../DESIGN.md) (sistema de diseño v1.0): tokens, componentes y checklist por pantalla. El validador exige que cada plan de front lo cite.

| Fase | Plan | Ámbito | Modo | Depende de | Contenido |
|---|---|---|---|---|---|
| F0 | `infra-monorepo` | infra | `directo` | — | Estructura de [01](../especificacion/01-arquitectura.md) §1, Maven agregador y wrappers, Angular 22.2.2, versiones fijadas, `.env.example`, CI mínima y README raíz con sección «Verificación» (comandos canónicos). |
| F1 | `infra-compose` | infra | `directo` | `infra-monorepo` | PostgreSQL 18.6 con 7 bases y roles (`postgres/init`), Kafka 4.3.1 en KRaft, RabbitMQ 4.3.6 con management, Mailpit, healthchecks y orden de arranque de [configuración](../especificacion/infraestructura/configuracion.md). |
| F1 | `infra-gateway-eureka` | infra | `directo` | `infra-compose` | Rutas del Gateway por prefijo, rate limit público de 60/min, timeouts, CORS, `/actuator` sin exponer, Eureka con credenciales propias. |
| F1 | `back-plataforma-comunes` | back | `directo` | `infra-compose` | Convención aplicada en cada servicio: resource server JWT RS256 (iss, aud, exp, scope), envelope y códigos genéricos, correlationId, patrón de idempotencia, `/actuator/health` y logs estructurados. |
| F1 | `back-plataforma-mensajeria` | back | `openspec` | `infra-compose`, `back-plataforma-comunes` | Outbox e inbox por servicio (DDL de [03](../especificacion/03-contrato-comun.md)), poller de 5 s con lote de 50, backoff, alertas, orden por agregado, exchanges `hotel.commands.v1` y `hotel.dlx.v1`, DLT y DLQ, JSON Schemas. |
| F2 | `back-identity-auth` | back | `directo` | `back-plataforma-mensajeria` | Login, registro público (fuerza CLIENTE y emite `notificacion.bienvenida` por outbox), `/api/auth/me`, cambio de clave (Idempotency-Key + HMAC), social con 410, JWKS, token máquina `POST /internal/auth/token`, rate limits y BCrypt. |
| F2 | `back-identity-personas` | back | `directo` | `back-identity-auth`, `back-hotel-catalogo` | Personas y tipos de persona con mutex del último ADMIN, sedes asignadas validadas contra hotel, `GET /internal/clientes/{id}`, auditoría `PERSONA_SEARCH` y `PERSONA_READ`, anonimización manual, `identity.persona.v1`. |
| F2 | `back-hotel-catalogo` | back | `directo` | `back-identity-auth`, `back-plataforma-mensajeria` | Sedes, pisos, categorías y habitaciones (altas, edición, imágenes), estados físicos fijos, catálogo público `/api/public/**`, rutas internas `/internal/habitaciones` y `/internal/hoteles`, `hotel.sede.v1`, `HabitacionCreada`, `HabitacionSnapshot`, consumidor `hotel-ocupacion-v1`. |
| F2 | `front-public-web-landing` | front | `directo` | `back-hotel-catalogo` | Landing: inicio, sedes, detalle de sede, tipos de habitación, contacto, privacidad, textos y assets demo, tarifa «Desde S/ 70.00», locale es-PE. |
| F2 | `front-admin-web-base` | front | `directo` | `back-identity-auth` | Login y registro, guards por rol, layout y menú de [08](../especificacion/08-admin-web.md) §5, interceptor (Idempotency-Key, ETag, 401, 202), mensajes desde `errors.md`, pipes de moneda y fecha, perfil y clave. |
| F2 | `front-admin-web-catalogo` | front | `directo` | `front-admin-web-base`, `back-identity-personas`, `back-hotel-catalogo` | Personas y roles; sedes, pisos, categorías y habitaciones con sus formularios, validaciones y estados de [08](../especificacion/08-admin-web.md). |
| F3 | `back-reception-operacion` | back | `openspec` | `back-identity-personas`, `back-hotel-catalogo`, `back-plataforma-mensajeria` | `room_gate` y su sincronización desde hotel (`reception-gates-v1`), check-in con adelanto y medio de pago, editar salida, lectura por estadía, campo `vencida`, bloqueos (`POST /internal/habitaciones/{id}/bloqueos`), operaciones de consumo y lápidas (`recepcion.operacion-finalizar`), `notificacion.checkin`. |
| F3 | `back-hotel-operaciones` | back | `openspec` | `back-reception-operacion` | Mantenimiento y baja con blockId (200, 202, 409), reactivación, consulta de operaciones, ciclo de limpieza (`limpiezas`, `limpieza-completada`), `limpieza.solicitada`, `HabitacionLista` y `HabitacionHabilitada`. |
| F3 | `back-inventory-stock` | back | `openspec` | `back-hotel-catalogo`, `back-plataforma-mensajeria` | Productos y existencias por sede, ajustes y movimientos, descuento y restitución con locks e idempotencia (`/internal/stock`), catálogo interno, `stock.restituir`, `inventory.stock.v1` e `inventory.movimiento.v1`. |
| F3 | `back-sales-ventas` | back | `openspec` | `back-reception-operacion`, `back-inventory-stock`, `back-plataforma-mensajeria` | Carrito, catálogo privado, ventas (alta, pago, anulación impagada), operaciones y recuperación cada 30 s, compensación (`sales-compensacion-v1`), productores de `recepcion.operacion-finalizar` y `stock.restituir`, `sales.venta.v1`, resumen y confirmación de cierre (`/internal/cierres`). |
| F3 | `front-admin-web-recepcion` | front | `directo` | `front-admin-web-base`, `back-reception-operacion`, `back-hotel-operaciones` | Listado y filtros de estadías, detalle, check-in (buscar cliente, crear estadía), editar salida, tablero de limpieza y acciones de mantenimiento con respuestas 200, 202 y 409. |
| F3 | `front-admin-web-ventas` | front | `directo` | `front-admin-web-base`, `back-sales-ventas`, `back-inventory-stock` | Productos y movimientos; ventas (nueva, listado, detalle, pago, anulación impagada); polling de operaciones 202. |
| F4 | `back-reception-cierre` | back | `openspec` | `back-reception-operacion`, `back-sales-ventas` | Cotización, cierre VALIDANDO → CONFIRMANDO → COMPLETADO con cálculo congelado, penalidad y `montoRecibido`, recuperación cada 30 s, anulación de check-in, archivado, `reception.recepcion.v1` (SalidaRegistrada y RecepcionActualizada de anulación), `limpieza.solicitada` y `notificacion.checkout`. |
| F4 | `back-notification-worker` | back | `openspec` | `back-plataforma-mensajeria` | Consumo de las tres colas `notificacion.*`, inbox, jobs con lease, SMTP hacia Mailpit, reintentos, CLI `--admin` auditada, purga por retención y readiness con Rabbit. |
| F4 | `back-reporting-proyecciones` | back | `openspec` | `back-identity-personas`, `back-hotel-catalogo`, `back-hotel-operaciones`, `back-reception-cierre`, `back-inventory-stock`, `back-sales-ventas`, `back-plataforma-mensajeria` | Consumidor `reporting-proyeccion-v1` (6 topics), bootstrap con manifiesto y watermarks, `/api/reportes/*`, `ingresosHoy` (casos A, B y C), cobros por medio de pago, degradación y `REPORTING_NOT_READY`. |
| F4 | `front-admin-web-cierre` | front | `directo` | `front-admin-web-recepcion`, `back-reception-cierre` | Detalle de estadía, cotización, penalidad, monto recibido y medio de pago, anulación de check-in, archivado, estados 202 y 409, aviso «cierre pendiente, no volver a cobrar». |
| F4 | `front-admin-web-reportes` | front | `directo` | `front-admin-web-base`, `back-reporting-proyecciones` | Dashboard y reportes (ventas, ocupación, cobros, bajo stock), exportación en el frontend, avisos de consistencia eventual y de 503. |
| F4 | `front-admin-web-area-cliente` | front | `directo` | `front-admin-web-base`, `back-reception-operacion`, `back-sales-ventas` | Mi estadía, catálogo y carrito, mis ventas con polling, mi perfil y clave, límites del rol C de [08](../especificacion/08-admin-web.md) §2. |
| F5 | `qa-matriz-aceptacion` | qa | `directo` | F1–F4 | Ejecutar la matriz completa con fixtures F1–F9 y evidencia por caso. Las correcciones de spec que surjan se registran como preguntas abiertas, no como cambios directos. |
| F5 | `qa-runbooks` | qa | `directo` | `back-plataforma-mensajeria`, `back-reception-cierre`, `back-hotel-operaciones`, `back-notification-worker`, `back-reporting-proyecciones` | Simulacros de RB1 a RB4, alertas de la tabla de [runbooks](../especificacion/infraestructura/runbooks.md), backup y restauración. |

## 2. Mensajería

Cada topic, comando y grupo de consumidor de [05](../especificacion/05-mensajeria.md) tiene un plan productor y un plan consumidor. Un consumidor implementado en un plan anterior a su productor se prueba en `qa-matriz-aceptacion`.

| Nombre | Tipo | Productor | Consumidor |
|---|---|---|---|
| `identity.persona.v1` | topic | `back-identity-personas` | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones` |
| `hotel.sede.v1` | topic | `back-hotel-catalogo` | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones` |
| `hotel.habitacion.v1` | topic | `back-hotel-catalogo` (Creada, Snapshot); `back-hotel-operaciones` (Lista, Habilitada) | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones`; grupo `reception-gates-v1` → `back-reception-operacion` |
| `reception.recepcion.v1` | topic | `back-reception-operacion` (CheckIn, Actualizada); `back-reception-cierre` (Actualizada de anulación, SalidaRegistrada) | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones`; grupo `hotel-ocupacion-v1` → `back-hotel-catalogo`; grupo `sales-compensacion-v1` → `back-sales-ventas` |
| `inventory.stock.v1` | topic | `back-inventory-stock` | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones` |
| `inventory.movimiento.v1` | topic | `back-inventory-stock` | grupo `sales-compensacion-v1` → `back-sales-ventas` |
| `sales.venta.v1` | topic | `back-sales-ventas` | grupo `reporting-proyeccion-v1` → `back-reporting-proyecciones` |
| `limpieza.solicitada` | comando | `back-reception-cierre` | `back-hotel-operaciones` |
| `stock.restituir` | comando | `back-sales-ventas` | `back-inventory-stock` |
| `recepcion.operacion-finalizar` | comando | `back-sales-ventas` | `back-reception-operacion` |
| `notificacion.bienvenida` | comando | `back-identity-auth` | `back-notification-worker` |
| `notificacion.checkin` | comando | `back-reception-operacion` | `back-notification-worker` |
| `notificacion.checkout` | comando | `back-reception-cierre` | `back-notification-worker` |

## 3. Endpoints

Cada ruta de los `api.md` de [`microservicios/`](../especificacion/microservicios/) tiene un plan dueño. Gana el prefijo más largo; `{id}` equivale a cualquier segmento. Solo cuentan las rutas declaradas en tablas o listas de cada `api.md`, no las llamadas salientes mencionadas en prosa.

| Plan | Prefijos de ruta |
|---|---|
| `back-identity-auth` | `/api/auth/login`, `/api/auth/registro`, `/api/auth/me`, `/api/auth/password`, `/api/auth/social`, `/.well-known/jwks.json`, `/internal/auth/token` |
| `back-identity-personas` | `/api/persona`, `/api/tipopersona`, `/internal/clientes/{id}` |
| `back-hotel-catalogo` | `/api/public/hoteles`, `/api/hotel`, `/api/piso`, `/api/categoria`, `/api/estadohabitacion`, `/api/habitacion/listar`, `/api/habitacion/buscar`, `/api/habitacion/registrar`, `/api/habitacion/actualizar`, `/internal/habitaciones/{id}`, `/internal/hoteles/{id}` |
| `back-hotel-operaciones` | `/api/habitacion/eliminar`, `/api/habitacion/{id}/mantenimiento`, `/api/habitacion/{id}/mantenimiento-completado`, `/api/habitacion/{id}/reactivar`, `/api/habitacion/operaciones`, `/api/habitacion/limpiezas`, `/api/habitacion/{id}/limpieza-completada` |
| `back-reception-operacion` | `/api/recepcion/listar`, `/api/recepcion/{id}`, `/api/recepcion/cliente`, `/api/recepcion/habitacion-activa`, `/api/recepcion/registrar`, `/api/recepcion/actualizar`, `/internal/recepciones/{id}`, `/internal/habitaciones/{id}/bloqueos` |
| `back-reception-cierre` | `/api/recepcion/{id}/cotizacion-salida`, `/api/recepcion/registrar-salida`, `/api/recepcion/{id}/anular-checkin`, `/api/recepcion/eliminar` |
| `back-inventory-stock` | `/api/producto`, `/internal/productos`, `/internal/stock` |
| `back-sales-ventas` | `/api/venta`, `/api/carrito`, `/internal/cierres` |
| `back-reporting-proyecciones` | `/api/reportes` |
| `back-plataforma-comunes` | `/actuator/health` |

## 4. Casos de aceptación

Cada caso de [06](../especificacion/06-migracion-y-pruebas.md) §5 tiene al menos un plan dueño. `qa-matriz-aceptacion` los ejecuta todos, pero no es dueño de ninguno.

| Plan | Casos |
|---|---|
| `infra-gateway-eureka` | SEC08 |
| `back-plataforma-comunes` | SEC06 |
| `back-plataforma-mensajeria` | MSG01–MSG02 |
| `back-identity-auth` | SEC02 |
| `back-identity-personas` | SEC01, SEC07 |
| `back-hotel-catalogo` | SEC03, LP01, CRUD01, OCC04 |
| `front-public-web-landing` | LP01, LP02 |
| `back-inventory-stock` | SEC05, CRUD01, STK01–STK02, STK04 |
| `back-reception-operacion` | OCC01–OCC03, CLN01, STK06 |
| `back-hotel-operaciones` | OCC02, OCC03, CLN01–CLN03 |
| `back-sales-ventas` | SEC04, STK03–STK07, PAY01–PAY02 |
| `back-reception-cierre` | OUT01–OUT06 |
| `back-notification-worker` | NOT01–NOT03 |
| `back-reporting-proyecciones` | SEC03, REP01–REP03 |

## 5. Fuera de alcance

- Migración del monolito y procedimiento de importación de [06](../especificacion/06-migracion-y-pruebas.md) §3. El proyecto se construye desde cero con seeds limpios.
- Convivencia con el monolito. Las etiquetas `LEGACY` de los `api.md` se implementan como cualquier otra ruta; no se planifica convivencia ni retiro de rutas.
- Social login, reservas futuras, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium y Config Server, según el alcance cerrado de la [especificación](../especificacion/README.md).
