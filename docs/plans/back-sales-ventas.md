# back-sales-ventas — carrito, ventas, compensación y cierres de Sales (F3)

| Campo | Valor |
|---|---|
| Plan | `back-sales-ventas` |
| Ámbito | `back` |
| Componente | `sales-service` |
| Fase | `F3` |
| Modo | `openspec` |
| Estado | `cerrado` |
| Depende de | `back-reception-operacion`, `back-inventory-stock`, `back-plataforma-mensajeria` |
| Spec | [`sales/api.md` §REST: catálogo privado y carrito](../especificacion/microservicios/sales/api.md#rest-catálogo-privado-y-carrito), [§REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas), [§Feign entrante: cierre de consumos](../especificacion/microservicios/sales/api.md#feign-entrante-cierre-de-consumos), [§Feign saliente](../especificacion/microservicios/sales/api.md#feign-saliente), [§Kafka](../especificacion/microservicios/sales/api.md#kafka) y [§Rabbit](../especificacion/microservicios/sales/api.md#rabbit); [`sales/base-de-datos.md` §Tablas](../especificacion/microservicios/sales/base-de-datos.md#tablas), [§Funciones/procedimientos](../especificacion/microservicios/sales/base-de-datos.md#funcionesprocedimientos), [§DDL de referencia](../especificacion/microservicios/sales/base-de-datos.md#ddl-de-referencia), [§Esquemas JSONB](../especificacion/microservicios/sales/base-de-datos.md#esquemas-jsonb) y [§Seeds](../especificacion/microservicios/sales/base-de-datos.md#seeds); [`03-contrato-comun.md` §Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes), [§Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio) y [§Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal); [`04-flujos.md` §2. Venta y stock](../especificacion/04-flujos.md#2-venta-y-stock), [§3. Carrito](../especificacion/04-flujos.md#3-carrito), [§4. Cierre seguro de estadía](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía) y [§7. Entrega, duplicados y orden](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden); [`05-mensajeria.md` §Envelope de evento Kafka](../especificacion/05-mensajeria.md#envelope-de-evento-kafka), [§Topics y asignación exacta](../especificacion/05-mensajeria.md#topics-y-asignación-exacta), [§Envelope de comando RabbitMQ](../especificacion/05-mensajeria.md#envelope-de-comando-rabbitmq) y [§Reintentos y DLQ Rabbit](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit); [rutas de venta, carrito, operaciones y cierres de `sales.yaml`](../contracts/openapi/sales.yaml); [`VentaSnapshot`](../contracts/events/sales.venta.v1.VentaSnapshot.schema.json), [`StockRestituir`](../contracts/events/stock.restituir.StockRestituir.v1.schema.json), [`RecepcionOperacionFinalizar`](../contracts/events/recepcion.operacion-finalizar.RecepcionOperacionFinalizar.v1.schema.json), [`MovimientoStockResuelto`](../contracts/events/inventory.movimiento.v1.MovimientoStockResuelto.schema.json) y [`SalidaRegistrada`](../contracts/events/reception.recepcion.v1.SalidaRegistrada.schema.json); [`errors.md` §Inventory y Sales](../contracts/errors.md#inventory-y-sales) y [§Genéricos](../contracts/errors.md#genéricos); [`06-migracion-y-pruebas.md` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional); [`01-arquitectura.md` §4 Stack](../especificacion/01-arquitectura.md#4-stack) |
| Casos | `SEC04`, `STK03–STK07`, `PAY01–PAY02` |
| Change | `openspec/changes/back-sales-ventas/` |

> Nota: este plan sigue la norma de `docs/plans/README.md` (§§3–5, §7–§8), la estructura de `docs/plans/_plantilla.md` y su fila de `docs/plans/catalogo.md` (§§1–4). La propuesta OpenSpec de §4 se crea en la misma rama y mismo PR que este plan; `tasks.md` del change refleja estos pasos.

## 1. Objetivo

Al cerrar el plan, `sales-service` expone carrito por estadía, catálogo privado de la sede, altas de venta con pago manual y anulación de impagadas, operaciones con polling y recuperación cada 30 s, compensación con el grupo `sales-compensacion-v1`, emisión de `sales.venta.v1` y de los comandos `stock.restituir` y `recepcion.operacion-finalizar`, y el resumen y la confirmación de cierre para reception, todo con JWT validado e idempotencia. Se comprueba con `SEC04`, `STK03–STK07` y `PAY01–PAY02` en verde y `openspec validate back-sales-ventas --strict` sin errores.

## 2. Alcance

**Entra:**

- El servicio `services/sales-service/` con el stack fijado y las convenciones ya mergeadas de `back-plataforma-comunes` y `back-plataforma-mensajeria` (JWT, envelope, idempotencia, outbox e inbox, salud).
- Carrito: `POST /api/carrito/agregar`, `GET /api/carrito/listar/{idRecepcion}` y `DELETE /api/carrito/eliminar`, con upsert por producto, cantidad 1..9999 y versión, según [`sales/api.md` §REST: catálogo privado y carrito](../especificacion/microservicios/sales/api.md#rest-catálogo-privado-y-carrito).
- Catálogo privado: `GET /api/venta/catalogo` de la sede de la estadía con precio autoritativo de inventory y 503 sin fallback ante caída.
- Ventas: `GET /api/venta`, `GET /api/venta/buscar/{id}`, `GET /api/venta/recepcion/{idRecepcion}`, `POST /api/venta`, `PUT /api/venta/{id}`, `DELETE /api/venta/{id}` y `GET /api/venta/operaciones/{operationId}`, con VentaAlta cerrada de hasta 50 líneas, pago con medio obligatorio y anulación con motivo 1..300.
- Operaciones `operacion_venta` de tipo VENTA, PAGO y ANULACION con una sola no terminal por venta, estados PREPARANDO, ADMITIDA, DEBITO_SOLICITADO y TERMINAL, y respuestas 201 o 202 con Location y Retry-After 2.
- Recuperación cada 30 s de PENDIENTE con edad mayor a 120 s y de CANCELACION_PENDIENTE, con reintento de PAGO y ANULACION y sin compensar CONFIRMADA por antigüedad.
- Compensación con el grupo `sales-compensacion-v1` sobre `inventory.movimiento.v1` (solo RESTITUIDO finaliza CANCELACION_PENDIENTE) y `reception.recepcion.v1` (SalidaRegistrada limpia el carrito sin marcar pagos), con inbox y DLT por topic y grupo.
- Producción por outbox de `sales.venta.v1` con clave de venta y versión, y de los comandos `stock.restituir` y `recepcion.operacion-finalizar` con clave estable y combinaciones tipo y resultado compatibles.
- Cierres internos: `GET /internal/cierres/{idRecepcion}/resumen` y `POST /internal/cierres/confirmar` con recibo único por cierre y bloqueo por recepción.
- Persistencia de `sales_db`: tablas carrito, venta, detalle_venta, operacion_venta y cierre_consumos más las técnicas, con los procedimientos de [§Funciones/procedimientos](../especificacion/microservicios/sales/base-de-datos.md#funcionesprocedimientos) y DDL de [§DDL de referencia](../especificacion/microservicios/sales/base-de-datos.md#ddl-de-referencia).
- Evidencia de `SEC04`, `STK03–STK07` y `PAY01–PAY02`.
- Propuesta OpenSpec en `openspec/changes/back-sales-ventas/` con `proposal.md`, specs delta, `design.md` y `tasks.md` en español, validada en estricto.

**Fuera de alcance:**

- Débito, movimientos, ajustes y catálogo interno de inventory, con sus locks y lápidas → `back-inventory-stock`; aquí solo se invoca su Feign y se consume su resolución.
- Admisión de operaciones, lápidas de finalizar, gates, check-in, cierres de estadía, bloqueos y cotización → `back-reception-operacion` y `back-reception-cierre`; aquí solo se registra la operación y se emite finalizar.
- Proyecciones, bootstrap y reportes de ventas, ocupación y cobros → `back-reporting-proyecciones`; aquí solo se emite `sales.venta.v1` para su consumo.
- Correos y trabajos de notificación → planes de notification; pantallas de ventas, productos, cierre y área de cliente → planes `front-*`.
- Mantenimiento, limpieza, reservas futuras, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium y Config Server, según el alcance cerrado de la especificación.

## 3. Contexto

- Este plan cubre la parte de ventas del criterio de salida de F3 (venta PAGADA con débito y compensación probados) de [norma §2](README.md#2-fases). Depende de `back-reception-operacion`, `back-inventory-stock` y `back-plataforma-mensajeria`; para ejecutar, los tres deben estar `cerrado` ([norma §6.2](README.md#6-contrato-con-la-ejecutora)). El modo es `openspec` porque mueve dinero y stock con concurrencia, estados persistidos y fiabilidad de mensajería ([norma §3](README.md#3-modo-directo-u-openspec)).
- La spec fija y este plan no redefine: rutas, accesos, DTO cerrados, respuestas 201 y 202 con Location y Retry-After 2, y polling ([`sales/api.md`](../especificacion/microservicios/sales/api.md)); tablas, DDL, procedimientos con CAS y locks, JSONB y seeds vacíos ([`base-de-datos.md`](../especificacion/microservicios/sales/base-de-datos.md)); transiciones PENDIENTE a CONFIRMADA, RECHAZADA y CANCELACION_PENDIENTE, pago separado PENDIENTE y PAGADO, admisión, débito todo o nada, resultado incierto a compensación, recuperación cada 30 s y cierre con recibo ([`04-flujos.md` §2](../especificacion/04-flujos.md#2-venta-y-stock), [§3](../especificacion/04-flujos.md#3-carrito) y [§4](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía)); envelope, topics, comandos, grupos y DLT y DLQ ([`05-mensajeria.md`](../especificacion/05-mensajeria.md)); identidad por JWT validado con algoritmo, issuer, audiencia, exp y scope, nunca cabeceras de identidad ([`03` §Headers](../especificacion/03-contrato-comun.md#headers)); idempotencia por actor, método, ruta y clave con hash canónico y orden que evalúa idempotencia antes que versión ([`03` §Respuestas](../especificacion/03-contrato-comun.md#respuestas)); scopes de sales hacia reception e inventory y timeouts y cortacircuitos ([`03` §Servicio a servicio](../especificacion/03-contrato-comun.md#servicio-a-servicio)); y códigos, mensajes y acciones ([`errors.md`](../contracts/errors.md)).
- Mensajería: el [catálogo §2](catalogo.md#2-mensajería) asigna a este plan producir `sales.venta.v1`, `stock.restituir` y `recepcion.operacion-finalizar`, y consumir con el grupo `sales-compensacion-v1` los topics `inventory.movimiento.v1` y `reception.recepcion.v1`. No implementa otro consumidor ni otro productor.
- Decisiones de este plan (la spec no las fija): los nombres de migración Flyway `V<n>__descripcion.sql` y la ubicación de seeds de dev, que se fijan al ejecutar y se anotan en el PR; la generación de `operationId`, `commandId` y `eventId` como UUID estables por intento; la clave estable de outbox por agregado y versión para reenvíos idempotentes; y el planificador periódico con intervalo fijo de 30 s sobre `operacion_venta` además de `venta`, sin cambiar los umbrales de 120 s ni las transiciones fijadas.
- Sin secretos en Git: credenciales, secretos de servicio y claves solo por variables de entorno ([norma §6.10](README.md#6-contrato-con-la-ejecutora)). Este plan solo toca Sales y no cambia `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 4. Pasos

- [x] 1. Dar de alta `services/sales-service/` con Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3 y PostgreSQL 18.6, con perfil dev, Flyway, registro en Eureka y las convenciones ya mergeadas de `back-plataforma-comunes` y `back-plataforma-mensajeria`. Verificación: el build del monorepo compila el módulo; con PostgreSQL arriba, el servicio arranca en dev y `GET /actuator/health/liveness` y `GET /actuator/health/readiness` responden 200.
- [x] 2. Configurar la seguridad: resource server JWT con algoritmo, issuer, audiencia, exp y scope validados, deny-by-default, A global, E solo sedes asignadas y C solo recepciones y ventas propias, más token máquina con scopes hacia reception e inventory para el Feign saliente. Verificación: sin token 401 `UNAUTHENTICATED`; C en venta ajena 404 `RESOURCE_NOT_FOUND`; token humano en `GET /internal/cierres/{idRecepcion}/resumen` 403 `FORBIDDEN`; grep de cabeceras de identidad prohibidas devuelve 0 usos.
- [x] 3. Crear la migración V1 de `sales_db` con carrito, venta, detalle_venta, operacion_venta y cierre_consumos según el [DDL de referencia](../especificacion/microservicios/sales/base-de-datos.md#ddl-de-referencia), con checks, unicidades, índices y clave diferida entre venta y operación. Verificación: la migración aplica sobre base vacía; cantidad fuera de 1..9999, subtotal distinto de cantidad por precio, y dos operaciones no terminales de la misma venta fallan por restricción.
- [x] 4. Aplicar idempotencia y versión del contrato común: `Idempotency-Key` en POST, PUT y DELETE de negocio con clave por actor, método, ruta y clave y hash canónico, y `If-Match` en `PUT /api/venta/{id}` y `DELETE /api/venta/{id}` con ETag. Verificación: misma clave y mismo cuerpo devuelven lo mismo sin segundo efecto; misma clave con cuerpo distinto 409 `IDEMPOTENCY_CONFLICT`; sin `If-Match` 428 `PRECONDITION_REQUIRED`; versión distinta 412 `PRECONDITION_FAILED`.
- [x] 5. Implementar carrito y catálogo privado: `POST /api/carrito/agregar` con upsert que suma cantidad, `DELETE /api/carrito/eliminar` que borra solo la línea, `GET /api/carrito/listar/{idRecepcion}` que en CERRADA devuelve lista vacía tras validar propiedad, y `GET /api/venta/catalogo` con precio autoritativo de inventory. Verificación: agregar dos veces suma y sube versión; repetir eliminar con la misma clave devuelve el mismo 204; estadía ajena 404 `RESOURCE_NOT_FOUND`; con inventory caído el catálogo responde 503 `DEPENDENCY_UNAVAILABLE` sin precio inventado.
- [x] 6. Implementar `POST /api/venta` con VentaAlta cerrada de hasta 50 líneas sin precios ni totales, C solo con estado PENDIENTE u omitido y A/E con PAGADO y medio obligatorio. Verificación: alta válida responde 201 o 202 con Location y Retry-After 2; envío de precio o total 400 `VALIDATION_ERROR`; C con estado PAGADO 403 `FORBIDDEN`; A/E con PAGADO sin medio 400 `VALIDATION_ERROR`.
- [x] 7. Implementar admisión y débito: crear venta PENDIENTE con operación VENTA, registrar la operación en reception solo si la estadía está ACTIVA, persistir el paso a DEBITO_SOLICITADO solo con CAS ganador antes del Feign a inventory, y ante timeout de admisión dejar RECHAZADA con finalizar sin invocar inventory. Verificación: venta con admisión aceptada descuenta todo o nada con precios de inventory; timeout de admisión deja RECHAZADA sin movimiento; un éxito tardío de admisión tras el terminal no inicia débito.
- [x] 8. Implementar confirmación y rechazo: CAS a CONFIRMADA con detalles y total del resultado de inventory más snapshot y finalizar, o a RECHAZADA sin descuento, o a CANCELACION_PENDIENTE con `stock.restituir` ante resultado incierto y 202. Verificación: confirmada responde 201 CONFIRMADA; insuficiencia o inactividad 409 `STOCK_INSUFFICIENT` o 409 `PRODUCT_INACTIVE` sin descuento parcial; resultado incierto responde 202 y no confirma aunque llegue luego un éxito HTTP.
- [x] 9. Implementar `PUT /api/venta/{id}` de pago con operación PAGO admitida en reception, solo desde CONFIRMADA o PENDIENTE, con medio obligatorio y sin alterar precio ni líneas, idempotente por clave. Verificación: pago válido 200 con fecha de pago y medio registrados; operación de pago en curso 202 con Location y Retry-After 2; reintento con la misma clave devuelve la misma venta sin segundo efecto.
- [x] 10. Implementar `DELETE /api/venta/{id}` de anulación de impagada con motivo obligatorio, CAS a CANCELACION_PENDIENTE y `stock.restituir` por outbox sin llamada HTTP de restitución, con 202 hasta CANCELADA y 200 si ya estaba CANCELADA. Verificación: anulación de PENDIENTE o CONFIRMADA impagada responde 202; venta PAGADA 409 `SALE_ALREADY_PAID` con líneas intactas; venta LEGACY sin movimiento 409 `LEGACY_MOVEMENT_UNVERIFIED` sin emitir restitución.
- [x] 11. Implementar lecturas y polling: `GET /api/venta` con sede obligatoria y filtros de estado, `GET /api/venta/buscar/{id}`, `GET /api/venta/recepcion/{idRecepcion}` con historial que conserva canceladas, y `GET /api/venta/operaciones/{operationId}` sin payload interno. Verificación: listados devuelven data con meta de paginación; C solo ve su operación y venta o 404 `RESOURCE_NOT_FOUND`; el polling de un 202 termina en el recurso terminal de `GET /api/venta/buscar/{id}`.
- [x] 12. Producir por outbox `sales.venta.v1` con clave de venta y versión ante cada confirmación, pago desde cierre y cancelación, y los comandos `stock.restituir` y `recepcion.operacion-finalizar` con clave estable tras el commit terminal, incluidas RECHAZADA y PAGO_REGISTRADO con su operación. Verificación: cada mutación deja una fila por destino que valida contra sus JSON Schemas; el payload de finalizar lleva tipo y resultado compatibles y tupla verificada; sin confirmación del broker la fila queda pendiente.
- [x] 13. Implementar el consumidor `sales-compensacion-v1` de `inventory.movimiento.v1` y `reception.recepcion.v1` con inbox en la misma transacción del efecto y regla de versión mayor. Verificación: RESTITUIDO en CANCELACION_PENDIENTE deja CANCELADA con snapshot y finalizar; DESCONTADO tardío no confirma y registra inconsistencia con alerta; SalidaRegistrada limpia el carrito una sola vez ante duplicados; contradicción de sede o tupla va a DLT sin mover stock ajeno ni desbloquear otra venta.
- [x] 14. Implementar el recuperador cada 30 s sobre `venta` y `operacion_venta` con CAS: PENDIENTE con edad mayor a 120 s en PREPARANDO a RECHAZADA con finalizar sin stock, en DEBITO_SOLICITADO a CANCELACION_PENDIENTE con restitución, y reintento de PAGO por la misma admisión y de ANULACION por su CAS. Verificación: con débito en vuelo simulado el job deja un terminal consistente sin confirmar tras compensar; un timeout de pago no compensa inventario; la operación no se libera antes de RESTITUIDO.
- [x] 15. Implementar `GET /internal/cierres/{idRecepcion}/resumen` con sede obligatoria del caller, totales de CONFIRMADAS de la estadía y sede, exclusión de rechazadas, canceladas y técnicas sin admisión, y 409 ante operación admitida no terminal. Verificación: con ventas pendientes devuelve total pendiente, total pagado y lista de pendientes; sin ventas devuelve totales 0; con operación abierta responde 409 sin mutar.
- [x] 16. Implementar `POST /internal/cierres/confirmar` con clave igual al cierre del cuerpo, búsqueda del recibo antes de recalcular, validación exacta del total esperado, bloqueo por recepción y recibo único incluso con importe 0, marcando pendientes como PAGADO con el medio recibido. Verificación: mismo cierre reintentado devuelve el mismo recibo sin repagar; otro cierre para la misma recepción 409; total distinto 409 sin mutar; dos cierres concurrentes con el mismo cierre no duplican el pago.
- [x] 17. Crear la propuesta OpenSpec con el flujo de OpenSpec y mantener `openspec/changes/back-sales-ventas/` con `proposal.md`, specs delta, `design.md` y `tasks.md` en español y tareas que reflejan estos pasos, y correr las validaciones y la evidencia. Verificación: existe la carpeta del change con los cuatro artefactos; `openspec validate back-sales-ventas --strict` termina sin errores; `python scripts/validar-planes.py` desde la raíz da 0 errores; las respuestas y consultas de `SEC04`, `STK03–STK07` y `PAY01–PAY02` quedan adjuntas al PR.

## 5. Contratos afectados

- OpenAPI (`contracts/openapi/sales.yaml`): este plan implementa `GET /api/venta/catalogo`, `POST /api/carrito/agregar`, `GET /api/carrito/listar/{idRecepcion}`, `DELETE /api/carrito/eliminar`, `GET /api/venta`, `POST /api/venta`, `GET /api/venta/buscar/{id}`, `GET /api/venta/recepcion/{idRecepcion}`, `PUT /api/venta/{id}`, `DELETE /api/venta/{id}`, `GET /api/venta/operaciones/{operationId}`, `GET /internal/cierres/{idRecepcion}/resumen` y `POST /internal/cierres/confirmar`. Si al ejecutar aparece una discrepancia con la spec, se corrige en ese mismo YAML.
- Eventos y comandos: produce `sales.venta.v1` (VentaSnapshot con clave de venta y versión) y los comandos `stock.restituir` (StockRestituir sin cantidades ni precios del emisor) y `recepcion.operacion-finalizar` (RecepcionOperacionFinalizar con tipo VENTA, PAGO o ANULACION y resultado compatible), con los schemas [`VentaSnapshot`](../contracts/events/sales.venta.v1.VentaSnapshot.schema.json), [`StockRestituir`](../contracts/events/stock.restituir.StockRestituir.v1.schema.json) y [`RecepcionOperacionFinalizar`](../contracts/events/recepcion.operacion-finalizar.RecepcionOperacionFinalizar.v1.schema.json); consume `inventory.movimiento.v1` (MovimientoStockResuelto) y `reception.recepcion.v1` (SalidaRegistrada para limpiar el carrito) con el grupo `sales-compensacion-v1` y sus DLT por topic y grupo, con los schemas [`MovimientoStockResuelto`](../contracts/events/inventory.movimiento.v1.MovimientoStockResuelto.schema.json) y [`SalidaRegistrada`](../contracts/events/reception.recepcion.v1.SalidaRegistrada.schema.json). No implementa otro topic, comando ni grupo.
- Persistencia: migraciones Flyway `V<n>__descripcion.sql` y seeds de dev de `sales_db` con carrito, venta, detalle_venta, operacion_venta y cierre_consumos según el [DDL de referencia](../especificacion/microservicios/sales/base-de-datos.md#ddl-de-referencia). Las tablas técnicas (api_idempotency, outbox, inbox y audit_log) llegan con los planes `back-plataforma-*` ([`03` §Persistencia transversal](../especificacion/03-contrato-comun.md#persistencia-transversal)).
- Errores: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `IDEMPOTENCY_CONFLICT`, `PRECONDITION_REQUIRED`, `PRECONDITION_FAILED`, `DEPENDENCY_UNAVAILABLE`, `STOCK_INSUFFICIENT`, `PRODUCT_INACTIVE`, `DEBIT_CANCELLED`, `LEGACY_MOVEMENT_UNVERIFIED`, `SALE_ALREADY_PAID` y `STAY_NOT_ACTIVE` de [`errors.md`](../contracts/errors.md), con sus mensajes y acciones.

## 6. Pruebas

Ejecución real del 2026-10-20 contra Compose (`postgres`, `kafka`, `rabbitmq`) e identity, hotel,
reception, inventory y sales arrancados en dev. Las salidas literales se pegan abajo; las consultas
se ejecutaron con `docker exec hotel-postgres-1 psql -U postgres -d <db>`.

- Caso `SEC04` — token C del cliente 100:
  `GET /api/carrito/listar/{idRecepcion}` → `HTTP 404` `{"error":{"code":"RESOURCE_NOT_FOUND"}}`.
  `POST /api/venta` con `{"idRecepcion":5,...}` → `HTTP 404` `RESOURCE_NOT_FOUND`.
  Counts: `select count(*) from carrito;` → `0`; `select count(*) from venta;` → `0`.
- Caso `STK03` — `POST /api/venta` clave `33333333-3333-4333-8333-333333333333`, producto 1 × 2,
  repetido con el mismo cuerpo → `HTTP 201` las dos veces, `idVenta=1`, `total=7.00`,
  `estadoOperacion=CONFIRMADA`. Counts: `venta=1`, `detalle_venta=1`,
  `movimiento_venta where id_venta=1` → una fila `DESCONTADO`; `producto_sede` cantidad `18`.
- Caso `STK04` — venta 3 con operación VENTA/DEBITO_SOLICITADO **inducida por SQL** para simular el débito en vuelo:
  `DELETE /api/venta/{id}?motivo=...` → `HTTP 202`, `Location: /api/venta/buscar/3`,
  `Retry-After: 2`. Tras la restitución, `POST /internal/stock/descontar` tardío →
  `HTTP 409` `DEBIT_CANCELLED`; `movimiento_venta` venta 3 → `RESTITUIDO`; stock `20`; venta
  `CANCELADA`.
- Caso `STK05` — venta 5 **inducida por SQL** con débito real `DESCONTADO` y operación de 4 minutos:
  el job de 30 s la pasó a CANCELACION_PENDIENTE y, tras `RESTITUIDO`, a `CANCELADA`; un solo
  `movimiento_venta` (versión 2, `RESTITUIDO`), stock de vuelta a `20`; nunca `CONFIRMADA`.
- Caso `STK06` — admisión con timeout (proxy que corta `POST el POST de admisión de reception`
  a los 5 s de Feign): `POST /api/venta` → `HTTP 202` con `Location` y `Retry-After: 2`, venta 7
  `RECHAZADA`, operación `TERMINAL`, `recepcion.operacion-finalizar` con `RECHAZADA` y recepción
  `FINALIZADA/RECHAZADA`; `movimiento_venta` venta 7 → `0` filas. Admisión tardía directa a
  reception → `HTTP 409` `STAY_NOT_ACTIVE`. También se reprodujo con la venta 4 envejecida
  (`PREPARANDO` de 4 min, inducida por SQL): el job la dejó `RECHAZADA` y la admisión tardía
  devolvió `409`.
- Caso `STK07` — worker de sales detenido con la venta 5 **inducida por SQL** en DEBITO_SOLICITADO y el débito ya
  aplicado (stock `18`, movimiento `DESCONTADO`); al reiniciar, el recuperador compensó:
  `stock.restituir` → `RESTITUIDO`, venta `CANCELADA`, stock `20`, operación `TERMINAL` y
  finalizar `VENTA/CANCELADA`. La venta no se liberó antes de `RESTITUIDO` (permaneció
  CANCELACION_PENDIENTE).
- Caso `PAY01` — C con `estado=PAGADO` → `HTTP 403` `FORBIDDEN`; línea con `precioUnitario` →
  `HTTP 400` `VALIDATION_ERROR` (JSON cerrado). No se creó ninguna venta por esos intentos
  (`venta` seguía con las filas previas) y el precio autoritativo de inventory siguió en `3.50`.
- Caso `PAY02` — venta 2 `PAGADO`, `DELETE /api/venta/{id}?motivo=...` con `If-Match: "3"` →
  `HTTP 409` `SALE_ALREADY_PAID`; la fila conserva `estado=PAGADO`, `medio_pago=TARJETA`,
  `paid_at` y `version=3`, y `detalle_venta` sigue con cantidad 1 y precio 8.00.
- Evidencia adicional por paso:
  - Paso 1: `GET /actuator/health/liveness` y `/readiness` → `200`; Eureka muestra
    `SALES-SERVICE` `UP`; Flyway aplicó V1–V3.
  - Paso 3: base vacía `sales_verify` con V1–V3; `cantidad=0` y `10000` violan
    `carrito_cantidad_check`; subtotal 99.00 con 2×3.50 viola `detalle_venta_check`; segunda
    operación no terminal viola `uq_operacion_venta_en_curso`.
  - Paso 4: misma clave y cuerpo → misma respuesta; misma clave con `cantidad` distinta →
    `409 IDEMPOTENCY_CONFLICT`; `PUT` sin `If-Match` → `428 PRECONDITION_REQUIRED`; versión 1
    contra versión 2 → `412 PRECONDITION_FAILED`.
  - Paso 5: agregar 2 y luego 3 → `cantidad=5`, `version=2`; repetir `DELETE` con la misma clave
    → `204` dos veces y `carrito=0`; `GET /api/carrito/listar/{idRecepcion}` (CERRADA) → `[]`; con inventory
    detenido, catálogo y agregar → `503 DEPENDENCY_UNAVAILABLE`.
  - Paso 6: alta válida → `201` con `Location` y `Retry-After: 2`; `precioUnitario` en el body →
    `400 VALIDATION_ERROR`; C con `PAGADO` → `403`; A con `PAGADO` sin medio → `400`.
  - Paso 9: pago válido → `200` con `paidAt` y `medioPago`; con una operación `PAGO/ADMITIDA`
    en curso **insertada por SQL** (para simular una admisión de pago en vuelo) → `202` con
    `Location` y `Retry-After: 2`; repetir la misma clave → misma venta.
  - Paso 10: anulación de venta impagada → `202` y luego `CANCELADA`; `PAGADA` → `409`;
    `LEGACY` sin movimiento **inducida por SQL** → `409 LEGACY_MOVEMENT_UNVERIFIED` y `outbox`
    sin fila de restitución.
  - Paso 11: `GET /api/venta?idHotel=1&page=0&size=2` → `data` + `meta` con `page/size/
    totalElements`; `recepcion/8` conserva canceladas; `buscar/8` responde `200`; operación
    consultada sin `request`; C101 sobre venta u operación de C100 → `404`.
  - Paso 12: con RabbitMQ detenido, la venta 11 dejó `recepcion.operacion-finalizar` y
    `stock.restituir` en `outbox` con `published=f` (intentos 5–6) mientras `sales.venta.v1`
    quedó `published=t`; al volver Rabbit, ambas filas pasaron a `published=t` y la venta 11
    terminó `CANCELADA`.
  - Paso 13: `RESTITUIDO` sobre CANCELACION_PENDIENTE dejó `CANCELADA` + snapshot + finalizar;
    un `DESCONTADO` tardío solo registró `alerta_descontado_tardio` sin cambiar el estado; un
    `SalidaRegistrada` limpió el carrito (`carrito_limpiado_por_salida`) y su reentrega con el
    mismo `eventId` no volvió a borrar (inbox `1` fila, línea residual intacta).
  - Paso 14: `PREPARANDO` viejo → `RECHAZADA` sin stock; DEBITO_SOLICITADO viejo →
    CANCELACION_PENDIENTE → `CANCELADA` sin confirmar; un `PAGO` en curso se reintentó y pagó
    sin nuevo movimiento de inventario.
  - Pasos 15–16: resumen recepción 5 → totales `0` y `ventasPendientes []`; recepción 8 →
    `totalPendiente 7.00`, `totalPagado 11.50`, `ventasPendientes [10]`; con operación abierta →
    `409 OPERATIONS_IN_PROGRESS`; confirmación → receipt `totalPagadoPrevio 11.50`,
    `totalPagadoAhora 7.00`, `totalConsumos 18.50`, `ventasPagadas [10]`; el mismo cierre
    devuelve el mismo receipt; otro cierre → `409 CLOSURE_IN_PROGRESS`; total distinto →
    `409 AMOUNT_MISMATCH`; dos confirmaciones concurrentes del mismo cierre (recepción 5,
    importe 0) devolvieron el mismo receipt y dejaron una sola fila en `cierre_consumos`.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify` → `BUILD SUCCESS` (sales-service:
  `Tests run: 21, Failures: 0, Errors: 0`); `python scripts/validar-planes.py` → `30 plan(es)
  revisado(s); 0 error(es), 0 aviso(s)`; `python scripts/validar-contratos.py --base origin/main` →
  `Los contratos solo se han ampliado. Correcto.`; `npx openspec validate back-sales-ventas
  --strict` → `Change 'back-sales-ventas' is valid`. Salidas de Surefire en
  `services/sales-service/target/surefire-reports/`. El change se archivó al cerrar en
  `openspec/changes/archive/2026-10-20-back-sales-ventas/` y su spec quedó promovida a
  `openspec/specs/sales-ventas/spec.md`.

## 7. Preguntas abiertas

Ninguna (resuelto por IA: migración de dominio en V3 por la convención de plataforma ya mergeada,
transiciones CAS en el repositorio porque las firmas documentadas no reciben actor ni hash, y sin
snapshot de venta cuando no hay líneas confirmadas porque el JSON Schema exige `minItems: 1`; ver
`docs/implementacion/decisiones/IA-back-sales-ventas-01.md`, `-02` y `-03`).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Carrito y catálogo privado operativos por estadía con aislamiento por sede y propiedad, sin reserva de stock y con 503 sin precio inventado.
- [x] Ventas con alta, pago y anulación impagada verificadas con CAS, idempotencia, versión y comandos durables, sin borrar historial y sin anular PAGADO ni LEGACY sin movimiento.
- [x] Operaciones con polling, recuperación cada 30 s y compensación del grupo `sales-compensacion-v1` verificadas sin doble débito ni confirmación tras compensar.
- [x] `sales.venta.v1`, `stock.restituir` y `recepcion.operacion-finalizar` emitidos por outbox contra sus JSON Schemas, y cierres con resumen y recibo único para reception.
- [x] Casos `SEC04`, `STK03–STK07` y `PAY01–PAY02` con evidencia adjunta al PR de ejecución.
- [x] `openspec validate back-sales-ventas --strict` sin errores y `python scripts/validar-planes.py` sin errores; cada aviso, resuelto o justificado en el PR.
- [x] Sin preguntas abiertas.
- [x] El plan pasa a `cerrado` en el último PR del plan ([norma §6.9](README.md#6-contrato-con-la-ejecutora)).
