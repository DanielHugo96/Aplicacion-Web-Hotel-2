# back-reporting-proyecciones — read model de reportes (F4)

| Campo | Valor |
|---|---|
| Plan | `back-reporting-proyecciones` |
| Ámbito | `back` |
| Componente | `reporting-service` |
| Fase | `F4` |
| Modo | `openspec` |
| Estado | `cerrado` |
| Depende de | `back-identity-personas`, `back-hotel-catalogo`, `back-hotel-operaciones`, `back-reception-cierre`, `back-inventory-stock`, `back-sales-ventas`, `back-plataforma-mensajeria` |
| Spec | [`reporting/api.md` §REST legacy](../especificacion/microservicios/reporting/api.md#rest-legacy), [§Ejemplo](../especificacion/microservicios/reporting/api.md#ejemplo), [§Cálculo de ingresosHoy (casos numéricos)](../especificacion/microservicios/reporting/api.md#cálculo-de-ingresoshoy-casos-numéricos), [§Feign, Kafka y Rabbit](../especificacion/microservicios/reporting/api.md#feign-kafka-y-rabbit) y [§Alcance](../especificacion/microservicios/reporting/api.md#alcance); [`reporting/base-de-datos.md` §Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección), [§Funciones SQL](../especificacion/microservicios/reporting/base-de-datos.md#funciones-sql), [§DDL de referencia](../especificacion/microservicios/reporting/base-de-datos.md#ddl-de-referencia), [§Esquemas JSONB](../especificacion/microservicios/reporting/base-de-datos.md#esquemas-jsonb), [§Seeds y bootstrap](../especificacion/microservicios/reporting/base-de-datos.md#seeds-y-bootstrap) y [§Validación](../especificacion/microservicios/reporting/base-de-datos.md#validación); [`03-contrato-comun.md` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [§Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio), [§Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal), [§Salud, readiness y resiliencia](../especificacion/03-contrato-comun.md#salud-readiness-y-resiliencia) y [§Privacidad y retención](../especificacion/03-contrato-comun.md#privacidad-y-retención); [`05-mensajeria.md` §Envelope de evento Kafka](../especificacion/05-mensajeria.md#envelope-de-evento-kafka), [§Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta) y [§Grupos de consumidor y DLT concretos](../especificacion/05-mensajeria.md#grupos-de-consumidor-y-dlt-concretos); [`04-flujos.md` §4](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía) y [§7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden); [`configuracion.md` §Por servicio](../especificacion/infraestructura/configuracion.md#por-servicio), [§Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio) y [§Flyway y seeds](../especificacion/infraestructura/configuracion.md#flyway-y-seeds); [`errors.md` §Reporting y Gateway](../contracts/errors.md#reporting-y-gateway) |
| Casos | `SEC03`, `REP01–REP03` |
| Change | `openspec/changes/archive/2026-10-20-back-reporting-proyecciones/` |

## 1. Objetivo

Al cerrar el plan, `reporting-service` mantiene al día el read model de reportes consumiendo los 6 topics del grupo `reporting-proyeccion-v1`, con bootstrap por manifiesto y watermarks, y expone los cinco reportes de `/api/reportes` con aislamiento por sede, meta de consistencia eventual y `ingresosHoy` sin doble conteo (casos A, B y C). Los casos `SEC03` (parte de reporting) y `REP01–REP03` quedan probados con evidencia.

## 2. Alcance

**Entra:**

- `services/reporting-service/` con la estructura del monorepo y las versiones fijadas ([`01-arquitectura.md` §1](../especificacion/01-arquitectura.md#1-monorepo-sí) y [§4 Stack](../especificacion/01-arquitectura.md#4-stack)), aplicando las convenciones ya mergeadas de `back-plataforma-comunes`; sin Feign, sin RabbitMQ, sin rutas internas y sin tablas de outbox/idempotencia.
- Esquema de `reporting_db`: las tablas de proyección del [DDL de referencia](../especificacion/microservicios/reporting/base-de-datos.md#ddl-de-referencia) (persona, sede, habitación, gate, recepción, stock, venta, detalle, limpieza completada, checkpoint y bootstrap) con sus CHECK e índices, más la `inbox` común de [§Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal).
- Consumidor `reporting-proyeccion-v1` de `identity.persona.v1`, `hotel.sede.v1`, `hotel.habitacion.v1`, `reception.recepcion.v1`, `inventory.stock.v1` y `sales.venta.v1`, con inbox, skip-if-older, reintentos del contrato y DLT por topic.
- Proyecciones: catálogo (personas, sedes, habitaciones), gates con reconciliación de limpieza y bloqueos en ambos órdenes entre topics, estadías con su cierre y su anulación sin recibo, stock y ventas con detalle.
- Bootstrap con manifiesto (`dominios` y watermarks) y checkpoints de todas las particiones suscritas, incluidas las vacías; `bootstrapComplete` y `REPORTING_NOT_READY` hasta el LISTO.
- Reportes `GET /api/reportes/productos-bajo-stock`, `/ventas`, `/ocupacion`, `/cobros` y `/dashboard` con validaciones de rango, orden estable, paginación, `data[]` sin filas y meta `{updatedAt, consistency, degraded, bootstrapComplete}`.
- Caja del día `ingresosHoy` con los casos A, B y C, y cobros con `medioPagoAdelanto`/`medioPagoCierre` (null = NO_REGISTRADO) sin volver a sumar ventas.
- Degradación explícita: 200 con `degraded=true`, o 503 `REPORTING_UNAVAILABLE` fuera del umbral de conexión.
- Evidencia de la parte de reporting de `SEC03` y de `REP01–REP03`.

**Fuera de alcance:**

- Cierre de estadía, anulación, archivo y salidas de `reception.recepcion.v1` → `back-reception-cierre`; gates, limpieza y mantenimiento → `back-hotel-operaciones` (la emisión de `hotel.habitacion.v1` es de `back-hotel-catalogo`); ventas y cierres de consumos → `back-sales-ventas`; stock → `back-inventory-stock`; personas → `back-identity-personas`. Reporting solo proyecta esos hechos.
- `inventory.movimiento.v1` (compensación de ventas) no se consume aquí, y reporting no produce eventos ni comandos ni publica en RabbitMQ ([`reporting/api.md` §Feign, Kafka y Rabbit](../especificacion/microservicios/reporting/api.md#feign-kafka-y-rabbit)).
- Outbox, poller, reintentos y convención de inbox → `back-plataforma-mensajeria`; Compose, brokers y rate limit → planes `infra-*`.
- Dashboard y reportes visuales, exportación a PDF/Excel y avisos de la UI → `front-admin-web-reportes`; la exportación la hace el frontend con librerías existentes, sin endpoints de archivo.
- API pública para reconstruir o truncar proyecciones y migración del monolito; la reconstrucción es operación offline con backup y DB destino nueva ([`reporting/api.md` §Feign, Kafka y Rabbit](../especificacion/microservicios/reporting/api.md#feign-kafka-y-rabbit) y [`reporting/base-de-datos.md` §Seeds y bootstrap](../especificacion/microservicios/reporting/base-de-datos.md#seeds-y-bootstrap)).
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.

## 3. Contexto

- Este plan cubre la parte de reporting del criterio de salida de F4 ([norma §2](README.md#2-fases)) y del [orden de arranque](../especificacion/infraestructura/configuracion.md#flyway-y-seeds): reporting arranca con el resto de servicios, responde `REPORTING_NOT_READY` hasta su LISTO y es la fuente del dashboard y de los reportes. Depende de los 7 planes de la cabecera; para ejecutar, todos deben estar `cerrado` ([norma §6.2](README.md#6-contrato-con-la-ejecutora)).
- La spec fija y este plan no redefine: rutas, accesos, DTO, validaciones de rango y semántica de respuestas ([`reporting/api.md`](../especificacion/microservicios/reporting/api.md) y [`reporting.yaml`](../contracts/openapi/reporting.yaml)); tablas, DDL, funciones SQL, manifiesto y reglas de bootstrap ([`reporting/base-de-datos.md`](../especificacion/microservicios/reporting/base-de-datos.md)); envelope, key por agregado, grupos y DLT ([`05-mensajeria.md`](../especificacion/05-mensajeria.md)); identidad por JWT validado, nunca cabeceras `X-User-Id`, `X-Role` ni `X-Hotel-Id` ([`03` §Headers](../especificacion/03-contrato-comun.md#headers)); el cierre con penalidad y `montoRecibido` ([`04` §4](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía)); la entrega, duplicados y orden entre topics ([`04` §7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden)); los códigos y mensajes de [`errors.md`](../contracts/errors.md); y la minimización de datos: los reportes usan nombre y apellido, nunca documento ni correo ([`03` §Privacidad y retención](../especificacion/03-contrato-comun.md#privacidad-y-retención)).
- El modo es `openspec` porque el plan cumple condiciones de la [norma §3](README.md#3-modo-directo-u-openspec): proyecta datos con bootstrap, watermarks y consistencia eventual, y depende de fiabilidad de mensajería (inbox, reintentos, DLT y orden por agregado) con dinero de por medio (caja del día sin doble conteo). La propuesta y el diseño viven en [`openspec/changes/archive/2026-10-20-back-reporting-proyecciones/`](../../openspec/changes/archive/2026-10-20-back-reporting-proyecciones/) y no se repiten aquí.
- La spec también fija que reporting es un read model puro: no consulta bases ajenas en un reporte, no hace Feign, no tiene outbox ni API de idempotencia y su replay es operacional offline ([`reporting/base-de-datos.md` §Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección) y [`reporting/api.md` §Feign, Kafka y Rabbit](../especificacion/microservicios/reporting/api.md#feign-kafka-y-rabbit)).
- Decisiones de este plan (la spec no las fija): el `manifest` de dev/demo se deriva del seed terminado (los conteos del [§Esquemas JSONB](../especificacion/microservicios/reporting/base-de-datos.md#esquemas-jsonb) y los watermarks topic/partición del stream de bootstrap) y sus valores exactos se fijan al ejecutar y se anotan en el PR; los nombres de migración y fixtures se fijan igual; la proyección guarda la fecha de cierre del recibo y el medio de pago del cierre ya resueltos por el evento, sin recalcularlos; la reconstrucción de `REP02` usa la herramienta offline del servicio (importar snapshot y reproducir el stream en una `reporting_db` nueva) sin abrir API.
- La anulación de check-in (`RecepcionActualizada` con CERRADA, `archivada=true` y `cierre=null`) se proyecta como CERRADA con los importes del recibo en 0.00 y `closure_id`, `fecha_cierre` y `medio_pago_cierre` en null: cumple el CHECK de "CERRADA con importes" sin fabricar un recibo, y la fila queda fuera de cobros y del cobro final de alojamiento de la caja del día por no tener fecha de cierre. La spec no se toca; la decisión queda registrada aquí y en el change ([norma §7](README.md#7-reglas)).
- Sin secretos en Git: credenciales, claves y contraseñas solo por variables de entorno ([norma §6.10](README.md#6-contrato-con-la-ejecutora)).

## 4. Pasos

- [x] 1. Dar de alta `services/reporting-service/` con la estructura del monorepo y las versiones fijadas (Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3, PostgreSQL 18.6), perfil dev, Flyway, registro en Eureka y las convenciones ya mergeadas de `back-plataforma-comunes` (envelope, correlationId, logs, health), sin Feign, sin RabbitMQ y sin tablas de outbox/idempotencia. Verificación: el build del monorepo compila el módulo; con PostgreSQL arriba el servicio arranca en dev, se registra en Eureka y `GET /actuator/health/liveness` y `GET /actuator/health/readiness` responden 200 (readiness solo con la base arriba); las rutas de health internas no se publican por el Gateway.
- [x] 2. Migración V1 de `reporting_db` con las tablas del [DDL de referencia](../especificacion/microservicios/reporting/base-de-datos.md#ddl-de-referencia): personas, sedes, habitaciones, gates, recepciones, stock, ventas y detalle, limpieza completada, checkpoints, bootstrap e `inbox`, con sus CHECK, PK, la FK local de detalle y los índices de consulta. Verificación: la migración aplica sobre una base vacía; un `estado_estadia` CERRADA sin importes de cierre falla por CHECK; un estado de gate fuera del enum falla; existen los índices de `recepcion_projection`, `stock_projection` y `venta_projection` del [§Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección).
- [x] 3. Seguridad: resource server JWT RS256 (algoritmo, issuer `hotel-identity`, audiencia `hotel-api` y exp) en las rutas de reportes, con ADMIN global, EMPLEADO solo sus sedes asignadas y CLIENTE prohibido; `idHotel` obligatorio en los 4 reportes y opcional solo para ADMIN en dashboard; sede ajena o inexistente 404; deny-by-default, sin rutas internas, sin scopes de máquina y sin confiar en cabeceras de identidad ([`03` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes) y [`reporting.yaml`](../contracts/openapi/reporting.yaml)). Verificación: sin token 401 `UNAUTHENTICATED`; token C 403 `FORBIDDEN`; EMPLEADO sin `idHotel` en listados 400 `VALIDATION_ERROR`; caso `SEC03` en su parte de reporting — token E de sede 1 con `GET /api/reportes/dashboard?idHotel=2` responde 404 `RESOURCE_NOT_FOUND` sin datos de la sede 2. Evidencia: `GET /api/reportes/dashboard` sin token → HTTP 401 `UNAUTHENTICATED`; token C (cliente) → HTTP 403 `FORBIDDEN`; token E (sede 1) sin `idHotel` → HTTP 400 `VALIDATION_ERROR` (`{"idHotel":"parámetro obligatorio"}`); token E de sede 1 con `idHotel=2` → HTTP 404 `RESOURCE_NOT_FOUND` sin datos de la sede 2; token A con `idHotel=999` (sede inexistente) → HTTP 404. Sondas con tokens reales de identity (admin@hotel.test, recepcion.lima@hotel.test, cliente.uno@hotel.test).
- [x] 4. Consumidor `reporting-proyeccion-v1` de los 6 topics asignados con inbox en la misma transacción del efecto, commit de offset después del commit local, hasta 5 reintentos (1s, 5s, 30s, 120s, 300s) y DLT `<topic>.reporting-proyeccion-v1.dlt` que conserva envelope, key y error no sensible; skip-if-older por versión de agregado y marcado de degradación ante DLT ([`05` §Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta) y [§Grupos de consumidor y DLT concretos](../especificacion/05-mensajeria.md#grupos-de-consumidor-y-dlt-concretos)). Verificación: un mensaje duplicado no duplica filas ni altera reportes; una versión vieja no rebaja el snapshot; un payload inválido agotado termina en la DLT correcta y deja la proyección `degraded`; `inventory.movimiento.v1` no tiene listener. Evidencia: `GET /api/reportes/dashboard` con token → 200. Se republicó el MISMO `VentaSnapshot` (mismo `eventId`) y `inbox` conserva una sola fila para ese `eventId`, sin filas duplicadas ni cambio de totales; se publicó un `VentaSnapshot` con `version` 2 (total 1.00) y la proyección siguió en 7.00 con `source_version` 3; un payload que no valida contra el esquema terminó en `identity.persona.v1.reporting-proyeccion-v1.dlt` (envelope, key y `kafka_dlt-exception-message` conservados) y dejó el checkpoint `degraded=true`; `kafka-consumer-groups --describe --group reporting-proyeccion-v1` muestra exactamente los 6 topics, sin `inventory.movimiento.v1`.
- [x] 5. Proyecciones de catálogo: `persona_projection` y `hotel_projection` con upsert por versión (baja conserva el snapshot con `estado=false`); `habitacion_projection` desde HabitacionCreada y HabitacionSnapshot con número, categoría, precio, estado y estado físico, y el seguimiento de versiones del DDL, sin alterar la ocupación ([§Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección)). Verificación: tras el seed, las proyecciones reflejan 6 personas, 3 sedes y 6 habitaciones; un evento repetido no duplica; una versión menor no pisa; una baja deja `estado=false` con el resto de campos. Evidencia: tras el seed y el arranque, `persona_projection` tiene 6 personas, `hotel_projection` 3 sedes y `habitacion_projection` 7 habitaciones (6 del seed + la 103 creada por la API de hotel); un `PersonaSnapshot` repetido no duplica filas; un `eventId` nuevo con `version` 2 y `estado=false` deja la fila con `estado=false` y `source_version=2` conservando nombre y apellido.
- [x] 6. Proyección de gates y limpieza entre topics: `gate_projection` se crea aunque falte el catálogo; hotel inicializa solo la parte física sin reemplazar un gate ya proyectado desde reception; los snapshots de reception se aplican por `roomVersion` y `ocupacionActual`; `limpieza_completada_projection` reconcilia salidas tardías; HabitacionLista libera solo su ciclo (nunca un gate OCUPADA posterior) y HabitacionHabilitada solo su `blockId`; un LISTA genérico no libera ([§Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección)). Verificación: con Lista antes de la salida y salida antes de la Lista el gate converge al mismo estado; un ciclo viejo no libera; la SalidaRegistrada posterior a su Lista no deja un `LIMPIEZA_PENDIENTE` eterno. Evidencia: con eventos sintéticos válidos en `hotel.habitacion.v1` y `reception.recepcion.v1`, la Lista publicada ANTES que la salida deja el gate en `LIBRE` (ciclo ya completado, sin `LIMPIEZA_PENDIENTE` eterno); la salida ANTES que la Lista deja `LIMPIEZA_PENDIENTE` y la Lista de ese ciclo lo deja `LIBRE`; la Lista de un ciclo viejo NO libera el ciclo vigente (`LIMPIEZA_PENDIENTE` con su ciclo) y solo lo libera la Lista del ciclo coincidente. `limpieza_completada_projection` guarda los cuatro ciclos con su `cleaning_room_version`.
- [x] 7. Proyección de estadías: `recepcion_projection` con upsert por versión de la estadía desde CheckInRegistrado, RecepcionActualizada y SalidaRegistrada; CERRADA con recibo fija total de alojamiento, consumos, total general, cobro, `closure_id` y medio de pago del cierre; la anulación de check-in (CERRADA, archivada y sin cierre) se proyecta con esos importes en 0.00 y sin `closure_id`, `fecha_cierre` ni `medio_pago_cierre`; snapshots, importes y archivo se conservan; el reporte de cobros usará ese snapshot sin re-sumar ventas. Verificación: una estadía cerrada cumple el CHECK con los valores del recibo; una anulación queda CERRADA con importes 0.00 y sin fecha de cierre, fuera del reporte de cobros y del cobro final de alojamiento de la caja del día; archivarla no borra importes; un evento con versión menor no pisa el cierre. Evidencia: la estadía cerrada cumple el CHECK con los valores del recibo (`total_alojamiento` 70.00, `total_consumos` 7.00, `total_general` 77.00, `cobro_ahora` 50.00, `fecha_cierre`, `closure_id`, `medio_pago_cierre`); la anulación de check-in (`RecepcionActualizada` con CERRADA, `archivada=true`, `cierre=null`) queda CERRADA con importes 0.00, sin `fecha_cierre`, sin `closure_id` y sin `medio_pago_cierre`, no aparece en el reporte de cobros y solo aporta su adelanto a la caja del día; un evento con `version` menor no pisa el cierre.
- [x] 8. Proyecciones de stock y ventas: `stock_projection` por `(id_hotel, id_producto)` con precio, cantidad, umbral y estado efectivo, skip-if-older; `venta_projection` con estado, estado de operación, total, medio de pago, creación y pago; `venta_detalle_projection` se reemplaza completo en la misma transacción; las anulaciones son snapshots, nunca DELETE físico ([§Tablas de proyección](../especificacion/microservicios/reporting/base-de-datos.md#tablas-de-proyección)). Verificación: tras el seed hay 3 productos × 3 sedes con sus umbrales; un duplicado y una versión vieja no alteran cantidad ni estado; el pago fija su fecha y medio una sola vez; la anulación conserva historial. Evidencia: `stock_projection` tiene 9 filas (3 productos × 3 sedes) con su umbral; un duplicado y una versión vieja no alteran cantidad ni estado; el pago fija `paid_at` y `medio_pago` una sola vez; la anulación de la venta 2 deja la fila como snapshot (`estado_operacion=CANCELADA`, `source_version` 4) con su línea de detalle, sin borrado físico, y no suma a la caja ni al reporte de ventas.
- [x] 9. Bootstrap con manifiesto y watermarks: `projection_bootstrap` arranca CARGANDO con `manifest` de conteos por dominio y watermarks topic/partición; carga los snapshots de los cinco dominios, crea checkpoint de todas las particiones suscritas —incluidas las vacías con offset -1 y sin evento— y marca LISTO en una sola transacción solo al cumplirlos; `bootstrapComplete` sale del estado ([§Seeds y bootstrap](../especificacion/microservicios/reporting/base-de-datos.md#seeds-y-bootstrap)). Verificación: caso `REP03` — sobre la demo sembrada (3 sedes, 6 habitaciones, 9 stock, 6 personas y 0 ventas/estadías) el bootstrap llega a LISTO sin esperar mensajes inexistentes; antes responde 503 `REPORTING_NOT_READY` y después 200 con `bootstrapComplete=true`; 0 ingresos es correcto solo tras LISTO. Evidencia: `projection_bootstrap` en `LISTO` con el manifiesto `{dominios:{personas:6,hoteles:3,habitaciones:6,productosSede:9,ventas:0,recepciones:0}, watermarks:[...lastOffset:-1]}` y `completed_at`; los 6 checkpoints existen con `last_offset=-1` y `last_event_at=null` para las particiones vacías; antes del LISTO la API responde 503 `REPORTING_NOT_READY` y después 200 con `bootstrapComplete=true` e `ingresosHoy` 0.00.
- [x] 10. Checkpoints y meta de consistencia: `projection_checkpoint` se actualiza por topic/partición al consumir y el meta `{updatedAt, consistency, degraded, bootstrapComplete}` (más paginación en listas) acompaña todas las respuestas; `updatedAt` es el mínimo checkpoint con timestamp de ingestión de los topics requeridos y puede ser null mientras todas las particiones estén vacías ([§REST legacy](../especificacion/microservicios/reporting/api.md#rest-legacy) y [`reporting.yaml`](../contracts/openapi/reporting.yaml)). Verificación: los 5 endpoints devuelven el meta completo; con topics vacíos `updatedAt=null` y `bootstrapComplete=true`; la consistencia se declara EVENTUAL y ninguna respuesta promete frescura absoluta. Evidencia: los 5 endpoints devuelven `meta` con `consistency` `EVENTUAL`, `degraded`, `bootstrapComplete` y `updatedAt`; con los topics sin tráfico `updatedAt=null` y `bootstrapComplete=true`; `projection_checkpoint` se actualiza por topic/partición al consumir (último offset y `last_event_at` de ingestión).
- [x] 11. Degradación y modo no utilizable: con DLT o error conocido y proyección utilizable se responde 200 con `degraded=true` y advertencia visible; con más de 5 minutos sin conexión activa (propiedad `hotel.reporting.degraded-timeout-minutes`) se responde 503 `REPORTING_UNAVAILABLE` con `Retry-After`; la propiedad `hotel.reporting.max-range-days` vale 366 ([§REST legacy](../especificacion/microservicios/reporting/api.md#rest-legacy) y [§Health y readiness por servicio](../especificacion/infraestructura/configuracion.md#health-y-readiness-por-servicio)). Verificación: con Kafka detenido y superado el umbral los reportes responden 503 `REPORTING_UNAVAILABLE`; al reconectar vuelven a 200; un mensaje en DLT dentro del umbral produce 200 con `degraded=true`; el 503 lleva `Retry-After`. Evidencia: con Kafka parado (`docker stop hotel-kafka-1` a las 13:28:31 UTC) los reportes siguieron en 200 con `degraded=true` dentro del umbral y a las 13:33:54 UTC (5 min 23 s sin conexión) pasaron a HTTP 503 `REPORTING_UNAVAILABLE` con `Retry-After: 60`; al reconectar el broker volvieron a 200. Con un mensaje en la DLT dentro del umbral la respuesta es 200 con `degraded=true`. `hotel.reporting.max-range-days` vale 366.
- [x] 12. Reportes de productos bajo stock y ventas: `GET /api/reportes/productos-bajo-stock` (productos activos con cantidad ≤ `limite`, default 5, orden por producto) y `GET /api/reportes/ventas` (solo ventas PAGADO por fecha de pago en `[inicio, fin)`, agrupadas por producto con cantidad e ingreso totales), con rango ISO-8601 con offset, `inicio<fin`, máximo 366 días, paginación y `data[]` sin filas ([§Funciones SQL](../especificacion/microservicios/reporting/base-de-datos.md#funciones-sql)). Verificación: una venta PENDIENTE no suma y su pago cuenta una vez; los kits del seed con cantidad 5 aparecen con el `limite` por defecto; sin filas responde 200 con `data=[]`; un rango de más de 366 días o con `inicio ≥ fin` responde 400 `VALIDATION_ERROR`. Evidencia: `GET /api/reportes/productos-bajo-stock?idHotel=1` devuelve el `Kit de aseo` (cantidad 5, `limite` por defecto 5) y el dashboard cuenta 3 productos bajo stock; `GET /api/reportes/ventas?idHotel=1&inicio=2026-10-20T00:00:00-05:00&fin=2026-10-21T00:00:00-05:00` devuelve solo la venta `PAGADO` (`Agua 500ml`, cantidad 2, 7.00) y la `PENDIENTE` no suma; sin filas responde 200 con `data=[]`; un rango de más de 366 días, con `inicio >= fin` o con `limite` negativo responde 400 `VALIDATION_ERROR`.
- [x] 13. Reportes de ocupación y cobros: `GET /api/reportes/ocupacion` (check-ins por fecha de entrada en fechas Lima; `inicio`/`fin` solo medianoche America/Lima; veces alquilada sin porcentaje) y `GET /api/reportes/cobros` (recepciones CERRADAS por fecha de cierre en `[inicio, fin)`, snapshot del recibo sin re-sumar ventas, medios de pago del adelanto y del cierre con null = NO_REGISTRADO, orden por fecha de cierre e id de recepción) ([§Funciones SQL](../especificacion/microservicios/reporting/base-de-datos.md#funciones-sql)). Verificación: una hora distinta de medianoche en ocupación responde 400 `VALIDATION_ERROR`; el cierre del fixture F8 aparece con sus totales y su medio de pago; un histórico sin medio muestra null; sin filas, `data=[]`. Evidencia: `GET /api/reportes/ocupacion` con una hora distinta de medianoche responde 400 `VALIDATION_ERROR` (`ocupación exige medianoche America/Lima`) y con medianoche devuelve `vecesAlquilada` 1 para la habitación 103; `GET /api/reportes/cobros` devuelve el cierre con `totalAlojamiento` 70.00, `totalConsumos` 7.00, `totalGeneral` 77.00, `medioPagoAdelanto` y `medioPagoCierre` `EFECTIVO` y `fechaCierre`; sin filas responde 200 con `data=[]`.
- [x] 14. Dashboard e `ingresosHoy`: `GET /api/reportes/dashboard` con habitaciones ocupadas y disponibles (disponibles = activas, de física LISTA, sin ocupación y sin limpieza pendiente), productos bajo stock, caja del día y moneda PEN; `ingresosHoy` = adelantos de check-ins de hoy + cobro final de alojamiento de cierres de hoy (total de alojamiento − adelanto) + ventas PAGADO con fecha de pago de hoy, cada pago una sola vez, sin sumar el total general del cierre otra vez ([§Cálculo de ingresosHoy](../especificacion/microservicios/reporting/api.md#cálculo-de-ingresoshoy-casos-numéricos)). Verificación: caso A → 77.00 (20 + 50 + 7); caso B → 70.00 con cobro final 0.00; caso C → 77.00 sin contar dos veces la venta pagada antes del cierre; con la demo tras bootstrap → 0 ingresos, 6 disponibles y 3 productos bajo stock, sin métricas hardcodeadas. Evidencia: la caja del día se midió paso a paso con la API real: 20.00 tras el check-in (solo el adelanto), 20.00 con la venta `PENDIENTE` (una venta no pagada no es ingreso), 27.00 tras el pago, 77.00 tras el cierre (20.00 + 50.00 + 7.00, caso C) y 97.00 al añadir la estadía anulada (solo su adelanto). Con la demo recién sembrada y el bootstrap en LISTO: 0 ingresos, 6 disponibles y 3 productos bajo stock, sin métricas hardcodeadas.
- [x] 15. Invariantes y reconstrucción: mismo evento dos veces no duplica ingresos; versión vieja no rebaja; un pago recibido antes del cierre no se cuenta dos veces; reordenar recepción y venta mantiene reportes coherentes al ponerse al día; ARCHIVADA conserva historia de cobro; cliente 403; empleado de sede ajena 404; límites de fecha 400; ventas PENDIENTE no son ingreso ([§Validación](../especificacion/microservicios/reporting/base-de-datos.md#validación)). Reconstrucción `REP02`: snapshot exportado con watermark/version importado a una `reporting_db` nueva más replay del stream produce los mismos totales y claves (checksums por reporte). Verificación: suite de invariantes verde; caso `REP02` con checksums iguales antes y después y sin depender de los 7 días de Kafka. Evidencia: suite de invariantes verde (`reporting-service`: 40 pruebas, 0 fallos). Mismo `eventId` dos veces no duplica (`inbox` con una sola fila); versión vieja no rebaja; un pago recibido antes del cierre no se cuenta dos veces (77.00 = 20 + 50 + 7); la fila ARCHIVADA conserva la historia de cobro; cliente 403; empleado de sede ajena 404; límites de fecha 400; ventas `PENDIENTE` no son ingreso. Reconstrucción `REP02`: snapshot exportado con sus watermarks importado en `reporting_db_rebuild` (base nueva) más replay completo del stream con el grupo `reporting-proyeccion-v1-rebuild` → checksums por reporte idénticos.
- [x] 16. Cierre: `npx openspec validate back-reporting-proyecciones --strict` sin errores, los comandos canónicos de la sección «Verificación» del `README.md` raíz (build, pruebas y Compose con PostgreSQL y Kafka) en verde, `python scripts/validar-planes.py` sin errores y la evidencia de `SEC03` (parte de reporting), `REP01`, `REP02` y `REP03` adjunta al PR; al mergear, el plan pasa a `cerrado` y el change se archiva con el comando de OpenSpec ([norma §6.9](README.md#6-contrato-con-la-ejecutora)). Verificación: las salidas de los validadores y las respuestas de los casos quedan adjuntas al PR de ejecución. Evidencia: `npx openspec validate back-reporting-proyecciones --strict` sin errores; `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS` en los nueve módulos; `python scripts/validar-planes.py` → 0 errores; `python scripts/validar-contratos.py --base origin/main` → 0 errores; Compose con PostgreSQL 18.6, Kafka 4.3.1 y RabbitMQ 4.3.6 arriba y en verde; evidencia de `SEC03`, `REP01`, `REP02` y `REP03` en la sección 6.

## 5. Contratos afectados

- OpenAPI: este plan implementa, de [`contracts/openapi/reporting.yaml`](../contracts/openapi/reporting.yaml), `GET /api/reportes/productos-bajo-stock`, `GET /api/reportes/ventas`, `GET /api/reportes/ocupacion`, `GET /api/reportes/cobros` y `GET /api/reportes/dashboard`. Si al ejecutar aparece una discrepancia con la spec, se corrige en ese mismo YAML.
- Eventos y comandos: consume `identity.persona.v1` (schema [`PersonaSnapshot`](../contracts/events/identity.persona.v1.PersonaSnapshot.schema.json)), `hotel.sede.v1` ([`SedeSnapshot`](../contracts/events/hotel.sede.v1.SedeSnapshot.schema.json)), `hotel.habitacion.v1` ([`HabitacionCreada`](../contracts/events/hotel.habitacion.v1.HabitacionCreada.schema.json), [`HabitacionSnapshot`](../contracts/events/hotel.habitacion.v1.HabitacionSnapshot.schema.json), [`HabitacionLista`](../contracts/events/hotel.habitacion.v1.HabitacionLista.schema.json) y [`HabitacionHabilitada`](../contracts/events/hotel.habitacion.v1.HabitacionHabilitada.schema.json)), `reception.recepcion.v1` ([`CheckInRegistrado`](../contracts/events/reception.recepcion.v1.CheckInRegistrado.schema.json), [`RecepcionActualizada`](../contracts/events/reception.recepcion.v1.RecepcionActualizada.schema.json) y [`SalidaRegistrada`](../contracts/events/reception.recepcion.v1.SalidaRegistrada.schema.json)), `inventory.stock.v1` ([`ProductoStockSnapshot`](../contracts/events/inventory.stock.v1.ProductoStockSnapshot.schema.json)) y `sales.venta.v1` ([`VentaSnapshot`](../contracts/events/sales.venta.v1.VentaSnapshot.schema.json)), con el grupo `reporting-proyeccion-v1` y las DLT `<topic>.reporting-proyeccion-v1.dlt`. No produce eventos ni comandos, no usa RabbitMQ y no consume `inventory.movimiento.v1`.
- Persistencia: migración V1 de `reporting_db` con las tablas de proyección, checkpoints, bootstrap e `inbox`; sin outbox ni API de idempotencia. Sin seeds de ventas, cobros ni estadísticas: la demo se llena con los snapshots de bootstrap y el stream.
- Errores: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `REPORTING_NOT_READY` y `REPORTING_UNAVAILABLE`.

## 6. Pruebas

- Caso `SEC03` (parte de este plan): token E de sede 1 (`recepcion.lima@hotel.test`) contra
  `GET /api/reportes/dashboard?idHotel=2` → 404 `RESOURCE_NOT_FOUND` sin datos de la sede 2.
  Evidencia: respuesta HTTP real contra `reporting-service` en `:8086` con el token emitido por
  `identity-service` en `:8081`:

  ```
  HTTP/1.1 404
  {"success":false,"message":"No encontramos lo que buscamos.","data":null,
   "error":{"code":"RESOURCE_NOT_FOUND","fields":{}},
   "meta":{"correlationId":"7ebd98dd-134e-4f27-b3cd-1af8efe98646","updatedAt":null}}
  ```

  Con el mismo token: sin token → 401 `UNAUTHENTICATED`, token C → 403 `FORBIDDEN` y token E sin
  `idHotel` → 400 `VALIDATION_ERROR` (`{"idHotel":"parámetro obligatorio"}`); token A con `idHotel=999`
  (sede inexistente) → 404 `RESOURCE_NOT_FOUND`.

  La mitad de hotel (`GET /api/habitacion/buscar/{id}` con la habitación 201) la prueba
  `back-hotel-catalogo`.

- Caso `REP01`: fixtures F3 (venta de 7.00 pagada hoy) + F8 (adelanto 20 + final 50 hoy) → dashboard con
  `ingresosHoy = 77.00` sin duplicar y reporte de ventas solo con pagadas por su fecha de pago. La escena se
  montó con los servicios reales: `POST /api/habitacion/registrar` (habitación 103, sede 1, 70.00),
  `POST /api/recepcion/registrar` (F1, cliente 100, adelanto 20.00 `EFECTIVO`),
  `POST /api/venta` (F2, 2 × Agua 500ml, 7.00) y `PUT /api/venta/{idVenta}` (F3, `PAGADO` `EFECTIVO`),
  `POST /api/recepcion/registrar-salida` (F8, `montoRecibido` 50.00). La caja del día medida paso a paso
  contra la API de reporting:

  | Momento | `GET /api/reportes/dashboard?idHotel=1` → `data` |
  |---|---|
  | tras el check-in (F1) | `ingresosHoy: 20.0` (solo el adelanto), `habitacionesOcupadas: 1` |
  | con la venta F2 `PENDIENTE` | `ingresosHoy: 20.0` (una venta no pagada no es ingreso) |
  | tras pagar F3 | `ingresosHoy: 27.0` (20.00 + 7.00, la venta cuenta una sola vez) |
  | tras el cierre F8 | `ingresosHoy: 77.0` (20.00 + 50.00 + 7.00, caso C) |

  ```
  $ curl -s -H "Authorization: Bearer $ADMIN" "http://localhost:8086/api/reportes/dashboard?idHotel=1"
  {"success":true,"message":"OK","data":{"habitacionesOcupadas":0,"habitacionesDisponibles":2,
   "productosBajoStock":1,"ingresosHoy":77.0,"moneda":"PEN"},
   "meta":{"correlationId":"1bbb907a-ca69-4e4a-a476-d9814dfa5d32","updatedAt":"2026-10-20T13:45:08.756952Z",
   "consistency":"EVENTUAL","degraded":false,"bootstrapComplete":true}}

  $ curl -s -H "Authorization: Bearer $ADMIN" "http://localhost:8086/api/reportes/ventas?idHotel=1&inicio=2026-10-20T00:00:00-05:00&fin=2026-10-21T00:00:00-05:00"
  {"success":true,"message":"OK","data":[{"idProducto":1,"nombreProducto":"Agua 500ml",
   "cantidadTotal":2,"totalIngresado":7.0}], ... "totalElements":1}

  $ curl -s -H "Authorization: Bearer $ADMIN" "http://localhost:8086/api/reportes/cobros?idHotel=1&inicio=2026-10-20T00:00:00-05:00&fin=2026-10-21T00:00:00-05:00"
  {"success":true,"message":"OK","data":[{"idRecepcion":1,"numeroHabitacion":"103","nombreCliente":"Cliente Uno",
   "totalAlojamiento":70.0,"totalConsumos":7.0,"totalGeneral":77.0,"medioPagoAdelanto":"EFECTIVO",
   "medioPagoCierre":"EFECTIVO","fechaCierre":"2026-10-20T13:47:55.142908Z"}], ...}
  ```

  El cierre lo produce `reception-service` con el desglose del [caso C](../especificacion/microservicios/reporting/api.md#cálculo-de-ingresoshoy-casos-numéricos)
  (`cotizacion-salida`: alojamiento 70.00, adelanto 20.00, saldo 50.00, consumos pagados 7.00, consumos
  pendientes 0.00, `cobroAhora` 50.00) y `reporting` lo proyecta sin volver a sumar ventas.

- Caso `REP02`: snapshot + stream de la corrida completa reconstruidos en `reporting_db` nueva → mismos
  totales y claves (checksums por reporte). Evidencia: `pg_dump --data-only` de las once tablas de
  proyección de `reporting_db` (incluidos `projection_checkpoint` con sus watermarks) importado en
  `reporting_db_rebuild` (base creada de cero, migración V1 aplicada por Flyway) y replay completo del
  stream con el grupo `reporting-proyeccion-v1-rebuild`; los checksums por reporte y por proyección salen
  idénticos en las dos bases:

  ```
  reporting_db        : bajo_stock=08a7c330c65fe85e1f1bda581182845e ventas=0065c933bf0a38c12be8c5a55bd9cde1
                        ocupacion=bb76704ebce5d8dd55ed32db8f9d550f cobros=7219e549fd942bc60d5597e188ea2fe3
                        dashboard=9033d3b16884c8a2cf7aa8f3169f9b82 gates=f9540fd91e894f4acaca01394fb9739f
                        recepciones=1b72da7783e73fc18eda7ea87bc32875 ventas_proy=2732d7a860e8fb08e3d632a0e0a23bf8
                        stock=c61091e25ac8e2c81bac7789a7c34779 personas=0734a7b4a49401325bbc7df5862a3cd8
                        checkpoints=7bcf5f42391112c127b451eca7ad7934 manifest=39a0a5dbae06378d47e3f3294b5a43d3
  reporting_db_rebuild: (los mismos doce checksums)
  IDENTICOS: los checksums por reporte coinciden antes y despues de la reconstruccion
  ```

  La reconstrucción no depende de los 7 días de Kafka: el snapshot se importa y luego se reproduce el
  stream posterior. Es operación offline: no se abrió ninguna API de reconstrucción.

- Caso `REP03`: demo recién sembrada sin ventas ni estadías y topics vacíos → `projection_bootstrap` en
  LISTO al cumplir manifiesto/watermarks, sin esperar mensajes; API 200 con `bootstrapComplete=true`.
  Evidencia: fila de `projection_bootstrap` y meta.

  ```
  Base recién creada (`reporting_db_observado`), manifiesto cumplido por el seed terminado y
  grupo de consumidor sin tráfico pendiente: el bootstrap llega a LISTO sin esperar mensajes.

  id | estado | manifest | completed_at
   1 | LISTO  | {"dominios": {"ventas": 0, "hoteles": 3, "personas": 6, "recepciones": 0,
         "habitaciones": 6, "productosSede": 9},
         "watermarks": [{"topic": "identity.persona.v1", "partition": 0, "lastOffset": -1},
         {"topic": "hotel.sede.v1", "partition": 0, "lastOffset": -1},
         {"topic": "hotel.habitacion.v1", "partition": 0, "lastOffset": -1},
         {"topic": "reception.recepcion.v1", "partition": 0, "lastOffset": -1},
         {"topic": "inventory.stock.v1", "partition": 0, "lastOffset": -1},
         {"topic": "sales.venta.v1", "partition": 0, "lastOffset": -1}]} | 2026-10-20 14:20:52.257591+00

   topic          | partition | last_offset | last_event_at | degraded
  ----------------+-----------+-------------+----------------+----------
   hotel.habitacion.v1    |         0 |          -1 |               | f
   hotel.sede.v1          |         0 |          -1 |               | f
   identity.persona.v1    |         0 |          -1 |               | f
   inventory.stock.v1     |         0 |          -1 |               | f
   reception.recepcion.v1 |         0 |          -1 |               | f
   sales.venta.v1         |         0 |          -1 |               | f

  $ curl -s -H "Authorization: Bearer $ADMIN" "http://localhost:8089/api/reportes/dashboard"
  HTTP 200
  {"success":true,"message":"OK","data":{"habitacionesOcupadas":0,"habitacionesDisponibles":6,
   "productosBajoStock":3,"ingresosHoy":0.0,"moneda":"PEN"},
   "meta":{"correlationId":"5182cb1f-bb5d-4465-9c45-8bd50aebf383","updatedAt":null,
   "consistency":"EVENTUAL","degraded":false,"bootstrapComplete":true}}
  ```

  `updatedAt` es `null` porque ninguna partición tiene marca de ingestión (`last_event_at` nulo) y
  `bootstrapComplete` es `true` porque `projection_bootstrap` está en LISTO: el `bootstrapComplete` sale
  del estado del bootstrap, no de la presencia de ventas.

  Antes del LISTO la API responde 503 `REPORTING_NOT_READY` con `Retry-After: 60`:

  ```
  HTTP/1.1 503
  Retry-After: 60
  {"success":false,"message":"Los reportes aún se están preparando.","data":null,
   "error":{"code":"REPORTING_NOT_READY","fields":{}}, ...}
  ```

- Comandos: los de la sección «Verificación» del `README.md` raíz (build, pruebas y Compose con PostgreSQL
  y Kafka) y `python scripts/validar-planes.py`; evidencia: salidas adjuntas al PR de ejecución.

  ```
  $ bash scripts/mvn.sh -f services/pom.xml verify
  [INFO] Hotel services (agregador) ......................... SUCCESS [  0.001 s]
  [INFO] identity-service ................................... SUCCESS [ 45.000 s]
  [INFO] hotel-service ...................................... SUCCESS [ 20.438 s]
  [INFO] reception-service .................................. SUCCESS [01:13 min]
  [INFO] inventory-service .................................. SUCCESS [ 51.378 s]
  [INFO] sales-service ...................................... SUCCESS [ 16.660 s]
  [INFO] reporting-service .................................. SUCCESS [ 16.454 s]
  [INFO] notification-service ............................... SUCCESS [ 11.084 s]
  [INFO] api-gateway ........................................ SUCCESS [ 39.906 s]
  [INFO] discovery-server ................................... SUCCESS [ 14.624 s]
  [INFO] BUILD SUCCESS

  $ cat services/reporting-service/target/surefire-reports/*.txt
  MessageValidatorTest   Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
  JwtValidatorTests      Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
  SecurityConventionTests Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  ReportingGuardTests    Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
  ReportRangeTest        Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
  ReportServiceTests     Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
  ReportingServiceApplicationTests Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  => 40 pruebas, 0 fallos
  ```

## 7. Preguntas abiertas

Ninguna (resuelto por IA: la anulación de check-in y la caja del día, registrada en
[`IA-back-reporting-proyecciones-01`](../implementacion/decisiones/IA-back-reporting-proyecciones-01.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Proyecciones al día por `reporting-proyeccion-v1` con inbox, orden por agregado y reconciliación de gates/limpieza probada en ambos órdenes, sin duplicar ni rebajar snapshots.
- [x] Bootstrap por manifiesto y watermarks con checkpoints de particiones vacías (`REP03`), 503 `REPORTING_NOT_READY` antes del LISTO y guardia de degradación con 503 `REPORTING_UNAVAILABLE` fuera del umbral.
- [x] `ingresosHoy` sin doble conteo con los casos A, B y C, cobros con medios de pago y límites de rango validados (`REP01`); reconstrucción con checksums iguales (`REP02`).
- [x] Evidencia de la sección 6 adjunta al PR de ejecución y `python scripts/validar-planes.py` sin errores; `npx openspec validate back-reporting-proyecciones --strict` sin errores.
- [x] El plan pasa a `cerrado` en el último PR del plan y el change se archiva con el comando de OpenSpec ([norma §6.9](README.md#6-contrato-con-la-ejecutora) y [§8](README.md#8-ciclo-de-vida)).
- [x] Sin preguntas abiertas.
