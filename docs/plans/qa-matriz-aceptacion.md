# qa-matriz-aceptacion — Ejecución de la matriz completa de aceptación (F5)

| Campo | Valor |
|---|---|
| Plan | `qa-matriz-aceptacion` |
| Ámbito | `qa` |
| Componente | `tests`, `scripts` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `infra-compose`, `infra-gateway-eureka`, `back-plataforma-comunes`, `back-plataforma-mensajeria`, `back-identity-auth`, `back-identity-personas`, `back-hotel-catalogo`, `front-public-web-landing`, `front-admin-web-base`, `front-admin-web-catalogo`, `back-reception-operacion`, `back-hotel-operaciones`, `back-inventory-stock`, `back-sales-ventas`, `front-admin-web-recepcion`, `front-admin-web-ventas`, `back-reception-cierre`, `back-notification-worker`, `back-reporting-proyecciones`, `front-admin-web-cierre`, `front-admin-web-reportes`, `front-admin-web-area-cliente` |
| Spec | [`06-migracion-y-pruebas.md` §5 (fixtures y matriz)](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) y [§6 (doble validación)](../especificacion/06-migracion-y-pruebas.md#6-doble-validación-antes-de-implementar-y-antes-de-entregar); [`03-contrato-comun.md` §Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [§Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes) y [§Salud, readiness y resiliencia](../especificacion/03-contrato-comun.md#salud-readiness-y-resiliencia); [`errors.md` §Genéricos](../contracts/errors.md#genéricos), [§Reception](../contracts/errors.md#reception), [§Inventory y Sales](../contracts/errors.md#inventory-y-sales) y [§Reporting y Gateway](../contracts/errors.md#reporting-y-gateway); [`04-flujos.md` §1](../especificacion/04-flujos.md#1-check-in-inmediato), [§2](../especificacion/04-flujos.md#2-venta-y-stock), [§4](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía), [§5](../especificacion/04-flujos.md#5-limpieza-sin-ventana-de-doble-ingreso), [§6](../especificacion/04-flujos.md#6-mantenimiento-y-baja-de-habitación) y [§7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden); [`05-mensajeria.md` §Topics](../especificacion/05-mensajeria.md#topics-y-asignación-exacta), [§Comandos](../especificacion/05-mensajeria.md#envelope-de-comando-rabbitmq), [§Reintentos](../especificacion/05-mensajeria.md#reintentos-y-dlq-rabbit) y [§Pruebas mínimas](../especificacion/05-mensajeria.md#pruebas-mínimas); [`runbooks.md` RB1–RB4](../especificacion/infraestructura/runbooks.md#rb1--mensajes-en-dlq-kafka-y-rabbit) como referencia de escenarios; [contratos README §1–§3](../contracts/README.md#1-reglas-transversales) |
| Casos | `Todos` |
| Change | `—` |

## 1. Objetivo

Los 41 casos de la matriz de [06 §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional) quedan ejecutados sobre DB efímera con PostgreSQL y brokers reales, con fixtures F1–F9 y evidencia por caso guardada en tests/ y scripts/.

## 2. Alcance

**Entra:** siembra de los fixtures F1–F9 de [06 §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional); ejecución de los 41 casos (SEC01–SEC08, LP01–LP02, CRUD01, OCC01–OCC04, CLN01–CLN03, STK01–STK07, PAY01–PAY02, OUT01–OUT06, MSG01–MSG02, REP01–REP03, NOT01–NOT03) con evidencia por caso (comando o endpoint, resultado y salida guardada); scripts de ejecución y de informe en scripts/ y tests/.

**Fuera de alcance:** simulacros RB1–RB4, alertas, backup y restauración → `qa-runbooks`; cualquier corrección de la especificación (este plan no cambia la spec: lo que surja se registra en §7); implementación o modificación de servicios, apps o infra (este plan solo escribe en tests/ y scripts/, según la [norma §6.4](README.md#6-contrato-con-la-ejecutora)); alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).

## 3. Contexto

- La spec fija y este plan no redefine: fixtures F1–F9 y criterios cuantitativos por caso ([06 §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional)); ejecución sobre DB efímera con brokers reales guardando los IDs devueltos como variables ([06 §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional)); envelope success/message/data/meta, headers (Authorization Bearer, Idempotency-Key, If-Match, Retry-After) y catálogo único de códigos ([03 §Headers](../especificacion/03-contrato-comun.md#headers), [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [`errors.md`](../contracts/errors.md)); identidad solo por JWT RS256 validado (algoritmo, iss, aud, exp y scope) y sedes por claim, nunca por cabeceras falsificables ([03 §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes)); versiones fijadas (Java 25, Boot 4.1.1, Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6); reglas de check-in, venta/stock, cierre, limpieza y mantenimiento ([04 §1](../especificacion/04-flujos.md#1-check-in-inmediato), [§2](../especificacion/04-flujos.md#2-venta-y-stock), [§4](../especificacion/04-flujos.md#4-cierre-seguro-de-estadía), [§5](../especificacion/04-flujos.md#5-limpieza-sin-ventana-de-doble-ingreso), [§6](../especificacion/04-flujos.md#6-mantenimiento-y-baja-de-habitación)); entrega al menos una vez con efectos idempotentes y pruebas mínimas de duplicados/orden ([05 §Pruebas mínimas](../especificacion/05-mensajeria.md#pruebas-mínimas), [04 §7](../especificacion/04-flujos.md#7-entrega-duplicados-y-orden)).
- El catálogo asigna este plan como ejecutor de todos los casos sin ser dueño de ninguno ([catálogo §4](catalogo.md#4-casos-de-aceptación)); los consumidores implementados antes que su productor se prueban aquí ([catálogo §2](catalogo.md#2-mensajería)); los runbooks RB1–RB4 son referencia de los escenarios, sus simulacros pertenecen a `qa-runbooks`.
- Decisiones de este plan (la spec no las fija): orden de siembra F1–F9 y de ejecución por grupos; nombres de `scripts/ejecutar-matriz.sh`, `tests/fixtures/` y `tests/evidencia/` con un archivo por caso más `INFORME.md`; formato del informe (tabla ID → comando/endpoint → resultado → archivo de salida).
- Este plan no decide reglas: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a §7 y detiene la ejecución ([norma §6.6](README.md#6-contrato-con-la-ejecutora)).

## 4. Pasos

- [x] 1. Confirmar plataforma F1–F4 cerrada y sana antes de sembrar. Verificación: `docker compose` con los contenedores healthy, `GET /actuator/health/readiness` 200 por servicio y el Gateway respondiendo 401 sin token.
- [x] 2. Crear el esqueleto en tests/ y scripts/ (runner, siembra de F1–F9 e informe de evidencia). Verificación: `ls tests/fixtures tests/evidencia scripts` lista lo creado y `scripts/ejecutar-matriz.sh --help` termina en 0.
- [x] 3. Sembrar F1–F3: estadía ACTIVA (cliente 100, habitación 101, adelanto 20.00) con `POST /api/recepcion/registrar`, venta PENDIENTE con `POST /api/venta` y venta PAGADA con `PUT /api/venta/{id}`. Verificación: idRecepcion, idVenta y operationId guardados como variables y counts en reception_db y sales_db coinciden con lo sembrado.
- [x] 4. Sembrar F4–F5: cierre en CONFIRMANDO con `POST /api/recepcion/registrar-salida` con Sales detenido tras aceptar, y mantenimiento en PREPARANDO con `POST /api/habitacion/{id}/mantenimiento` con reception detenido a mitad de admisión. Verificación: closureId y blockId guardados y fases visibles en `GET /api/recepcion/{id}` y `GET /api/habitacion/operaciones/{blockId}`.
- [x] 5. Sembrar F6–F7: check-in en 201 más `POST /api/habitacion/{id}/mantenimiento` rechazado (bloqueo R durable) y venta en recuperación simulando timeout de inventory durante el débito sin liberar la operación. Verificación: bloqueo R RECHAZADO en DB y la operación de F7 sigue sin terminal.
- [x] 6. Sembrar F8–F9: cierre 200 con `POST /api/recepcion/registrar-salida` (gate LIMPIEZA_PENDIENTE, cleaningCycleId C1) y venta LEGACY sin movimiento conciliado como fila de importación en DB efímera. Verificación: ciclo C1 en `GET /api/habitacion/limpiezas` y fila LEGACY visible solo en DB.
- [x] 7. Ejecutar SEC01–SEC08 contra el Gateway con JWT solo en memoria: `GET /api/persona/listar` y `GET /api/auth/me` sin token o con firma/audiencia inválidas; `POST /api/auth/registro` con idTipoPersona en el body; `GET /api/habitacion/buscar/{id}` y `GET /api/reportes/dashboard` con token E de sede 1 sobre sede 2; `GET /api/carrito/listar/{idRecepcion}` y `POST /api/venta` con recepción ajena; `POST /internal/stock/descontar` directo al puerto interno; concurrencia de `PUT /api/persona/actualizar/{id}` y `DELETE /api/persona/eliminar/{id}` sobre los dos ADMIN; preflight CORS con origen permitido y con origen ajeno. Verificación: 401 `UNAUTHENTICATED`, 400 `VALIDATION_ERROR`, 404 `RESOURCE_NOT_FOUND`, 403/404 según el caso, 409 `LAST_ADMIN_PROTECTED` con al menos un ADMIN activo restante, 429 `RATE_LIMITED` donde aplique, y cada salida guardada en tests/evidencia/SEC0X.json.
- [x] 8. Ejecutar LP01–LP02 y CRUD01: los 4 GET públicos (`GET /api/public/hoteles`, `GET /api/public/hoteles/{slug}`, `GET /api/public/hoteles/{slug}/categorias`, `GET /api/public/hoteles/{slug}/categorias/{idCategoria}`) sin token; inspección del bundle de public-web; altas y ediciones de sede (`POST /api/hotel/registrar`, `PUT /api/hotel/actualizar/{id}`), piso, categoría, habitación (`POST /api/habitacion/registrar`, `PUT /api/habitacion/actualizar/{id}`) y producto (`POST /api/producto/registrar`, `PUT /api/producto/actualizar/{id}`) con GET y DELETE posteriores. Verificación: 200 en los 4 públicos con solo claves de HotelPublico/CategoriaPublica; sin dato privado en el bundle; filas persistidas con version incrementada, DELETE deja estado falso y PUT sin If-Match devuelve 428 `PRECONDITION_REQUIRED`; evidencia en tests/evidencia/LP0X.json y CRUD01.json.
- [x] 9. Ejecutar OCC01–OCC04 y CLN01–CLN03: doble `POST /api/recepcion/registrar` simultáneo a 101; check-in concurrente con `POST /api/habitacion/{id}/mantenimiento` en 102; `PUT /api/habitacion/{id}/limpieza-completada` con el ciclo correcto tras reanudar Rabbit; reenvío de `limpieza.solicitada` C1 y evento viejo tras nuevo check-in; cierre de 201 con reintento del blockId R y nueva intención con otro blockId; `DELETE /api/piso/eliminar/{id}` y `DELETE /api/categoria/eliminar/{id}` con habitación activa concurrente. Verificación: 201 frente a 409 `ROOM_OCCUPIED`, `ROOM_NOT_READY`, `GATE_NOT_AVAILABLE`, `ACTIVE_DEPENDENCIES` o `ROOM_OPERATION_IN_PROGRESS` según el caso; gate y tarea de limpieza en el estado exigido; evidencia en tests/evidencia/OCC0X.json y CLN0X.json.
- [x] 10. Ejecutar STK01–STK07: dos `POST /api/venta` simultáneas, venta multítem sin descuento parcial, repetición con la misma Idempotency-Key, anulación con débito tardío, job de recuperación cada 30 s, `recepcion.operacion-finalizar` RECHAZADA antes de la respuesta de admitir, y reinicio del worker tras DEBITO_SOLICITADO; consultas en `GET /api/venta/operaciones/{operationId}` y `GET /internal/stock/movimientos/{idVenta}`. Verificación: 201 frente a 409 `STOCK_INSUFFICIENT`, `IDEMPOTENCY_CONFLICT` o `DEBIT_CANCELLED` según el caso; stock y movimientos con un solo efecto por venta; comandos `stock.restituir` y `recepcion.operacion-finalizar` observados; evidencia en tests/evidencia/STK0X.json.
- [x] 11. Ejecutar PAY01–PAY02 y OUT01–OUT06: `POST /api/venta` con estado PAGADO como CLIENTE y con precio en detalles; `DELETE /api/venta/{id}` sobre venta PAGADA; `POST /api/recepcion/registrar-salida` con admisión en vuelo, con montoRecibido inexacto, sin ventas, con proceso matado por fase y reintentado con el mismo closureId; `PUT /api/recepcion/actualizar/{id}` acortando bajo el adelanto; cotización en `GET /api/recepcion/{id}/cotizacion-salida`. Verificación: 400 `VALIDATION_ERROR` o 403 `FORBIDDEN` en PAY01; 409 `SALE_ALREADY_PAID`, `OPERATIONS_IN_PROGRESS`, `CLOSURE_IN_PROGRESS`, `AMOUNT_MISMATCH` o `ADVANCE_EXCEEDS_NEW_TOTAL` según el caso; un solo receipt y una sola salida en reintentos; evidencia en tests/evidencia/PAY0X.json y OUT0X.json.
- [x] 12. Ejecutar MSG01–MSG02: commitear F2 con el broker detenido y levantar dejando al poller; duplicar mensajes, invertir limpieza/salida y reenviar versión vieja de `reception.recepcion.v1`, `hotel.habitacion.v1`, `sales.venta.v1` e `inventory.movimiento.v1` sobre los grupos `reporting-proyeccion-v1`, `reception-gates-v1`, `hotel-ocupacion-v1` y `sales-compensacion-v1`. Verificación: outbox con published_at y efecto procesado una vez (inbox); sin doble stock, pago ni limpieza; contradicción en DLT/DLQ; evidencia en tests/evidencia/MSG0X.json más consultas a outbox e inbox.
- [x] 13. Ejecutar REP01–REP03: `GET /api/reportes/dashboard` y `GET /api/reportes/ventas` sobre F3+F8; rebuild de reporting en DB nueva con snapshot más stream; arranque en demo sin ventas esperando bootstrap. Verificación: ingresosHoy 77.00 sin duplicar y solo PAGADO por paid_at; mismos totales y claves tras el rebuild; bootstrap LISTO con bootstrapComplete verdadero y 200; 503 `REPORTING_NOT_READY` sin bootstrap y degradación visible según el caso; evidencia en tests/evidencia/REP0X.json.
- [ ] 14. Ejecutar NOT01–NOT03: cerrar F1 con SMTP (Mailpit) caído y repetir `notificacion.checkout`; responder tarde con lease vencido; purga sobre ENVIADA antigua e intento sobre PENDIENTE/ERROR con la CLI --admin. Verificación: salida 200 sin bloqueo del negocio, un job por commandId con reintentos y ERROR tras agotarlos; lease viejo sin efecto; purga solo en ENVIADA con registro en audit_log; comandos `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` observados; evidencia en tests/evidencia/NOT0X.json, notification_job y Mailpit.
- [ ] 15. Consolidar la evidencia y congelar hallazgos: completar tests/evidencia/INFORME.md (tabla ID → comando/endpoint → resultado → archivo) y anotar en §7, sin marcar, cualquier corrección de spec como pregunta abierta sin aplicarla. Verificación: los 41 casos tienen fila con salida adjunta y `git status --porcelain` muestra cambios solo en tests/, scripts/ y el propio plan.
- [ ] 16. Correr el validador y la auto-revisión de la [norma §4.5](README.md#4-proceso-del-planificador). Verificación: `python scripts/validar-planes.py` desde la raíz termina con 0 errores y cada aviso queda resuelto o justificado en el PR; objetivo verificable, cada paso comprobable y ninguna regla fijada por la spec.

## 5. Contratos afectados

- OpenAPI: ninguno se añade ni cambia. Se ejercitan rutas existentes de los contratos por servicio y de landing (las citadas en §4 y §6: auth, persona, tipopersona, hotel, piso, categoria, habitacion, estadohabitacion, recepcion, producto, carrito, venta, reportes y las 4 públicas), siempre vía Gateway en `http://localhost:8080`, nunca contra puertos de servicios.
- Eventos y comandos: ninguno nuevo. Se observan los topics `identity.persona.v1`, `hotel.sede.v1`, `hotel.habitacion.v1`, `reception.recepcion.v1`, `inventory.stock.v1`, `inventory.movimiento.v1` y `sales.venta.v1`; los comandos `limpieza.solicitada`, `stock.restituir`, `recepcion.operacion-finalizar`, `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout`; y los grupos `reporting-proyeccion-v1`, `hotel-ocupacion-v1`, `reception-gates-v1` y `sales-compensacion-v1`.
- Persistencia: ninguna migración Flyway ni seed permanente. Solo lecturas de verificación sobre tablas de los servicios (persona, recepcion, producto por sede, movimientos, cierre y receipt, limpieza, bloqueos, operaciones, jobs de notificación, checkpoints de proyección, outbox, inbox y audit_log) en DB efímera.
- Errores: ningún código nuevo. Se observan los del catálogo citados en §4 y §6: `UNAUTHENTICATED`, `VALIDATION_ERROR`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `PRECONDITION_REQUIRED`, `PRECONDITION_FAILED`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT`, `LAST_ADMIN_PROTECTED`, `ROOM_NOT_READY`, `ROOM_OCCUPIED`, `ROOM_NOT_SYNCED`, `ROOM_OPERATION_IN_PROGRESS`, `GATE_NOT_AVAILABLE`, `ACTIVE_DEPENDENCIES`, `STAY_NOT_ACTIVE`, `OPERATIONS_IN_PROGRESS`, `CLOSURE_IN_PROGRESS`, `AMOUNT_MISMATCH`, `ADVANCE_EXCEEDS_NEW_TOTAL`, `STOCK_INSUFFICIENT`, `PRODUCT_INACTIVE`, `DEBIT_CANCELLED`, `LEGACY_MOVEMENT_UNVERIFIED`, `SALE_ALREADY_PAID`, `REPORTING_NOT_READY`, `REPORTING_UNAVAILABLE` y `DEPENDENCY_UNAVAILABLE`.

## 6. Pruebas

Cada caso anota comando o endpoint, resultado exigido y archivo de evidencia en tests/evidencia/. Los valores concretos (IDs, importes) son los de los fixtures F1–F9 guardados como variables, según [06 §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional).

- Caso SEC01: `GET /api/persona/listar` sin token; 401 `UNAUTHENTICATED` sin datos; evidencia: tests/evidencia/SEC01.json y log del Gateway.
- Caso SEC02: `POST /api/auth/registro` con idTipoPersona en el body; 400 `VALIDATION_ERROR` y persona no creada; evidencia: tests/evidencia/SEC02.json y consulta a persona.
- Caso SEC03: `GET /api/habitacion/buscar/{id}` de sede 2 y `GET /api/reportes/dashboard` de sede 2 con token E de sede 1; 404 `RESOURCE_NOT_FOUND` en ambos; evidencia: tests/evidencia/SEC03.json.
- Caso SEC04: `GET /api/carrito/listar/{idRecepcion}` y `POST /api/venta` con recepción ajena y token C del cliente 100; 404 sin filas creadas; evidencia: tests/evidencia/SEC04.json y counts en sales_db.
- Caso SEC05: `POST /internal/stock/descontar` directo al puerto interno con token humano A; 403 `FORBIDDEN` (o 404 sin ruta por Gateway) con stock intacto; evidencia: tests/evidencia/SEC05.json y cantidad en producto por sede.
- Caso SEC06: `GET /api/auth/me` y `GET /api/hotel/listar` con JWT de firma ajena o audiencia incorrecta; 401 en cada servicio; evidencia: tests/evidencia/SEC06.json.
- Caso SEC07: `PUT /api/persona/actualizar/{id}` y `DELETE /api/persona/eliminar/{id}` simultáneos sobre los dos ADMIN; exactamente un 200 y un 409 `LAST_ADMIN_PROTECTED` con al menos un ADMIN activo con clave; evidencia: tests/evidencia/SEC07.json y consulta a persona.
- Caso SEC08: preflight CORS con Idempotency-Key e If-Match desde origen permitido y petición desde origen ajeno; preflight permitido con headers legibles y origen ajeno sin Allow-Origin; evidencia: tests/evidencia/SEC08.json.
- Caso LP01: los 4 GET públicos sin token; 200 en los 4 con solo claves públicas y validación de esquema; evidencia: tests/evidencia/LP01.json.
- Caso LP02: búsqueda de fecha/ocupación en public-web e inspección del bundle; parámetro ignorado o 400, sin dato privado ni endpoint nuevo; evidencia: tests/evidencia/LP02.json.
- Caso CRUD01: altas, ediciones, GET y DELETE de sede, piso, categoría, habitación y producto; filas persistidas con version incrementada, DELETE con estado falso y PUT sin If-Match 428; evidencia: tests/evidencia/CRUD01.json y consultas a DB.
- Caso OCC01: dos `POST /api/recepcion/registrar` simultáneos a 101; un 201 y un 409 `ROOM_OCCUPIED` con una sola fila activa; evidencia: tests/evidencia/OCC01.json e índice único.
- Caso OCC02: check-in y `POST /api/habitacion/{id}/mantenimiento` concurrentes en 102; gate final OCUPADA o BLOQUEADA, nunca ambas, perdedor 409; evidencia: tests/evidencia/OCC02.json, gate y operación.
- Caso CLN01: reintento de check-in con F8 y Rabbit pausado, reanudación y `PUT /api/habitacion/{id}/limpieza-completada` con el ciclo correcto; 409 `ROOM_NOT_READY` hasta completar y 201 después; evidencia: tests/evidencia/CLN01.json, tarea COMPLETADA y gate LIBRE.
- Caso CLN02: reenvío de `limpieza.solicitada` C1 y evento viejo tras nuevo check-in; sin reapertura de C1 ni liberación del ciclo actual; evidencia: tests/evidencia/CLN02.json, consola Rabbit, limpieza y gate.
- Caso CLN03: `HabitacionLista` antes de la salida o después del check-in nuevo; C1 conciliado sin liberar la ocupación posterior; evidencia: tests/evidencia/CLN03.json, proyecciones y gate.
- Caso OCC03: cierre de la estadía de 201, reintento del blockId R y nueva intención con otro blockId; R sigue 409 `GATE_NOT_AVAILABLE` y el nuevo 200; evidencia: tests/evidencia/OCC03.json y bloqueos RECHAZADO frente a ACTIVO.
- Caso OCC04: `DELETE /api/piso/eliminar/{id}` o categoría con habitación activa concurrente con alta/reactivación; un 409 `ACTIVE_DEPENDENCIES` sin habitación activa bajo padre inactivo; evidencia: tests/evidencia/OCC04.json y counts.
- Caso STK01: dos `POST /api/venta` simultáneas con claves distintas sobre stock 20; una 201 y una 409 `STOCK_INSUFFICIENT` con stock final 5 y un solo movimiento; evidencia: tests/evidencia/STK01.json, producto por sede y movimientos.
- Caso STK02: venta multítem que excede un ítem con `POST /api/venta`; 409 `STOCK_INSUFFICIENT` con stocks intactos; evidencia: tests/evidencia/STK02.json y cantidades.
- Caso STK03: repetición de `POST /api/venta` con la misma Idempotency-Key de F2; mismo idVenta con un solo débito y un solo movimiento; evidencia: tests/evidencia/STK03.json y counts.
- Caso STK04: anulación sobre F7 con débito en vuelo y entrega tardía del débito; lápida RESTITUIDO y 409 `DEBIT_CANCELLED` con stock intacto; evidencia: tests/evidencia/STK04.json y movimientos.
- Caso STK05: job de recuperación cada 30 s sobre F7; estado terminal consistente sin confirmar tras compensar y un movimiento por venta; evidencia: tests/evidencia/STK05.json, venta y movimientos.
- Caso STK06: `recepcion.operacion-finalizar` RECHAZADA antes de la respuesta tardía de admitir; lápida FINALIZADA con el admitir tardío en 409 y sin débito; evidencia: tests/evidencia/STK06.json, operación y ausencia de movimiento.
- Caso STK07: worker caído tras débito solicitado en ambos puntos de simulación, reinicio y recuperación; misma compensación segura sin liberar antes de RESTITUIDO; evidencia: tests/evidencia/STK07.json, operación y movimientos.
- Caso PAY01: `POST /api/venta` con estado PAGADO como CLIENTE y variante con precio en detalles; 403 o 400 con venta PENDIENTE y precio intacto; evidencia: tests/evidencia/PAY01.json y fila.
- Caso PAY02: `DELETE /api/venta/{id}` sobre F3 pagada; 409 `SALE_ALREADY_PAID` con líneas, receipt y paid_at intactos; evidencia: tests/evidencia/PAY02.json y filas.
- Caso OUT01: `POST /api/recepcion/registrar-salida` con venta admitida en vuelo; 409 `OPERATIONS_IN_PROGRESS` sin ventas admitidas tras el cierre; evidencia: tests/evidencia/OUT01.json y estados.
- Caso OUT02: reintento con el mismo closureId sobre F4; un receipt y una salida con ventas en PAGADO una sola vez; evidencia: tests/evidencia/OUT02.json, cierre y receipt.
- Caso OUT03: `POST /api/recepcion/registrar-salida` con montoRecibido inexacto y variante de penalidad sobre el precio inicial; 409 `AMOUNT_MISMATCH` con cierre RECHAZADO conservado y penalidad rechazada sin cierre; evidencia: tests/evidencia/OUT03.json y cierre.
- Caso OUT04: `POST /api/recepcion/registrar-salida` sobre F1 sin ventas con el monto exacto; receipt con consumos en cero, sede 1 y cobro de hoy; evidencia: tests/evidencia/OUT04.json y receipt.
- Caso OUT05: proceso matado en VALIDANDO, en CONFIRMANDO y tras el receipt, con reinicio; recálculo solo en VALIDANDO, reenvío congelado en CONFIRMANDO, un cobro y una salida; evidencia: tests/evidencia/OUT05.json y estados por fase.
- Caso OUT06: `PUT /api/recepcion/actualizar/{id}` con fecha que deja el precio inicial bajo el adelanto; 409 `ADVANCE_EXCEEDS_NEW_TOTAL` sin cambios; evidencia: tests/evidencia/OUT06.json y fila.
- Caso MSG01: F2 commiteada con el broker detenido, levantado y poller; outbox publicada una vez con efecto procesado una vez; evidencia: tests/evidencia/MSG01.json, outbox y consumidor.
- Caso MSG02: mensajes duplicados, órdenes invertidos y versión vieja reenviados; invariantes de inbox/versión/ciclo sin doble efecto y contradicción en DLQ; evidencia: tests/evidencia/MSG02.json, inbox y DLT.
- Caso REP01: `GET /api/reportes/dashboard` y `GET /api/reportes/ventas` sobre F3+F8; ingresosHoy 77.00 sin duplicar y solo PAGADO por paid_at; evidencia: tests/evidencia/REP01.json.
- Caso REP02: rebuild de reporting en DB nueva con snapshot más stream; mismos totales y claves; evidencia: tests/evidencia/REP02.json y comparación.
- Caso REP03: arranque en demo sin ventas con topics vacíos; LISTO por manifiesto/watermarks con 200 y bootstrapComplete verdadero; evidencia: tests/evidencia/REP03.json y bootstrap.
- Caso NOT01: cierre de F1 con SMTP caído y repetición de `notificacion.checkout`; salida 200, un job por commandId con reintentos y ERROR tras agotarlos; evidencia: tests/evidencia/NOT01.json, jobs y Mailpit.
- Caso NOT02: respuesta tardía con lease vencido; sin modificación del job nuevo; evidencia: tests/evidencia/NOT02.json, fila del job y log.
- Caso NOT03: purga sobre ENVIADA antigua e intento sobre PENDIENTE/ERROR; purga solo en ENVIADA con registro en audit_log; evidencia: tests/evidencia/NOT03.json, filas y audit_log.
- Comandos canónicos: los de la sección «Verificación» del README raíz más `python scripts/validar-planes.py` (0 errores) con salida adjunta al PR.

### 6.1 Resultados ejecutados

Corrida sobre el Compose real (PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6, Mailpit 1.31.5) y los
nueve servicios levantados con `scripts/qa/common.py::start_service`. Cada fila cita el comando
exacto, lo observado y el archivo donde está la salida completa. `scripts/ejecutar-matriz-completa.sh`
encadena los grupos en este mismo orden; el detalle por caso está en `INFORME.md`.

| Caso | Comando/endpoint exacto | Resultado observado | Salida |
|---|---|---|---|
| SEC01 | `GET /api/persona/listar` sin token | 401 `UNAUTHENTICATED`, sin datos | `SEC01.json` |
| SEC02 | `POST /api/auth/registro` con `idTipoPersona` | 400 `VALIDATION_ERROR`; 0 personas creadas | `SEC02.json` |
| SEC03 | `GET /api/habitacion/buscar/{id}` (201, sede 2) y `GET /api/reportes/dashboard?idHotel=2` con token E de sede 1 | 404 `RESOURCE_NOT_FOUND` en ambos | `SEC03.json` |
| SEC04 | `GET /api/carrito/listar/{idRecepcion}` (estadía ajena) y `POST /api/venta` con esa recepción | 404 `RESOURCE_NOT_FOUND`; 0 ventas y 0 filas de carrito | `SEC04.json` |
| SEC05 | `POST /internal/stock/descontar` al puerto 8084 con token A | 403 `FORBIDDEN`; por Gateway 404; stock 16 → 16 | `SEC05.json` |
| SEC06 | `GET /api/auth/me` y `GET /api/hotel/listar` con firma ajena y con `aud` incorrecta | 401 `UNAUTHENTICATED` en los dos servicios y en las dos variantes | `SEC06.json` |
| SEC07 | `PUT /api/persona/actualizar/{id}` (admin 2) y `DELETE /api/persona/eliminar/{id}` (admin 1) simultáneos | DELETE 200, PUT 409 `LAST_ADMIN_PROTECTED`; 1 ADMIN activo con clave | `SEC07.json` |
| SEC08 | Preflight `OPTIONS` con `If-Match`/`Idempotency-Key` desde `http://localhost:4200` y desde `http://evil.test` | Preflight 200 con los dos headers permitidos y `ETag, Location, Retry-After` expuestos; origen ajeno 403 sin `Access-Control-Allow-Origin` | `SEC08.json` |
| LP01 | Los 4 GET públicos sin token | 200 en los 4; 0 claves sobrantes frente a `HotelPublico`/`CategoriaPublica` | `LP01.json` |
| LP02 | `GET /api/public/hoteles?fecha=…` y `…/lima-centro?ocupacion=1`; inspección del bundle | 200 ignorando el parámetro; 0 hallazgos de endpoint privado, precio interno o endpoint de reserva en el bundle | `LP02.json` |
| CRUD01 | Altas, ediciones, GET y DELETE de sede, piso, categoría, habitación y producto | `version` 1→2→3 en los cinco; DELETE deja `estado=f`; PUT sin `If-Match` 428 `PRECONDITION_REQUIRED` | `CRUD01.json` |
| OCC01 | Dos `POST /api/recepcion/registrar` simultáneos a 101 | 201 + 409 `ROOM_OCCUPIED`; 1 estadía activa; índice `uq_recepcion_habitacion_activa` | `OCC01.json` |
| OCC02 | Check-in y `POST /api/habitacion/{id}/mantenimiento` (102) concurrentes | 201 + 409; gate final OCUPADA y operación RECHAZADA | `OCC02.json` |
| OCC03 | Cierre de 201, reintento del blockId R y nueva intención | Reintento 409 `GATE_NOT_AVAILABLE`; bloqueo R RECHAZADO; nueva intención 200 con bloqueo ACTIVO | `OCC03.json` |
| OCC04 | `DELETE /api/piso/eliminar/{id}` y `/api/categoria/eliminar/{id}` con habitación activa | 409 `ACTIVE_DEPENDENCIES` en ambos; 0 habitaciones activas bajo padre inactivo | `OCC04.json` |
| CLN01 | Cierre 200, reintento de check-in con Rabbit pausado y `PUT /api/habitacion/{id}/limpieza-completada` | 409 `ROOM_NOT_READY` → tarea COMPLETADA → 201 tras reanudar; gate LIBRE y después OCUPADA | `CLN01.json` |
| CLN02 | Reenvío de `limpieza.solicitada` de C1 y `HabitacionLista` viejo | Tarea de C1 sigue COMPLETADA; 1 tarea por ciclo; gate OCUPADA sin reapertura | `CLN02.json` |
| CLN03 | `HabitacionLista` posterior al cierre con `gate_projection` poblado | Gate OCUPADA antes y después; `limpieza_completada_projection` concilia C1 sin liberar la ocupación | `CLN03.json` |
| STK01 | Dos `POST /api/venta` ×15 con claves distintas | 201 + 409 `STOCK_INSUFFICIENT`; stock 20 → 5; un movimiento DESCONTADO | `STK01.json` |
| STK02 | `POST /api/venta` multítem p1×2 + p3×10 | 409 `STOCK_INSUFFICIENT`; stocks 20 y 5 intactos | `STK02.json` |
| STK03 | `POST /api/venta` con la `Idempotency-Key` de F2 | Mismo idVenta 1; stock 20 sin segundo débito; un movimiento | `STK03.json` |
| STK04 | Anulación de F7 y entrega del débito tardío | Lápida RESTITUIDO; débito tardío 409 `DEBIT_CANCELLED`; stock 20 → 20 | `STK04.json` |
| STK05 | Job de recuperación cada 30 s sobre F7 | Estado de operación CANCELACION_PENDIENTE → CANCELADA; un movimiento RESTITUIDO; 420 s de espera | `STK05.json` |
| STK06 | `recepcion.operacion-finalizar` RECHAZADA antes de la respuesta de admitir | Lápida `FINALIZADA/RECHAZADA`; admitir tardío 409 `STAY_NOT_ACTIVE`; 0 movimientos | `STK06.json` |
| STK07 | Worker caído tras DEBITO_SOLICITADO y reinicio | Operación en DEBITO_SOLICITADO → venta CANCELADA; un movimiento RESTITUIDO; stock intacto | `STK07.json` |
| PAY01 | `POST /api/venta` con estado PAGADO como CLIENTE y con `precioUnitario` | 403 `FORBIDDEN` + 400 `VALIDATION_ERROR`; F2 sigue PAGADO con total 7.00 y precio 3.50 | `PAY01.json` |
| PAY02 | `DELETE /api/venta/{id}?motivo=…` sobre F3 | 409 `SALE_ALREADY_PAID`; línea, receipt y `paid_at` idénticos antes y después | `PAY02.json` |
| OUT01 | `POST /api/recepcion/registrar-salida` con una venta admitida en vuelo | 409 `OPERATIONS_IN_PROGRESS`; la venta queda con estado de operación CANCELACION_PENDIENTE | `OUT01.json` |
| OUT02 | Reintento del cierre de F4 con el mismo `closureId` | Cierre COMPLETADO con un receipt; la venta queda PAGADO una sola vez | `OUT02.json` |
| OUT03 | `POST /api/recepcion/registrar-salida` con `montoRecibido` inexacto y penalidad sobre el precio | 409 `AMOUNT_MISMATCH` con cierre RECHAZADO conservado; penalidad 400 y 0 cierres vigentes | `OUT03.json` |
| OUT04 | `POST /api/recepcion/registrar-salida` sin ventas con el monto exacto | 200; receipt con `totalPendiente 0.00` y `totalPagadoPrevio 0.00`; estadía CERRADA | `OUT04.json` |
| OUT05 | Proceso matado en VALIDANDO, en CONFIRMANDO y tras el receipt | VALIDANDO recalcula; CONFIRMANDO reenvía el congelado; un receipt y una salida por cierre; el reintento tras receipt da 409 `STAY_NOT_ACTIVE` sin recalcular | `OUT05.json` |
| OUT06 | `PUT /api/recepcion/actualizar/{id}` con `fechaSalida` que deja el precio bajo el adelanto | 409 `ADVANCE_EXCEEDS_NEW_TOTAL`; precioInicial 180.00 y adelanto 140.00 sin cambios | `OUT06.json` |
| MSG01 | F2 commiteada con Kafka detenido y levantado después | Outbox pendiente → publicada con `publishedAt`; el inbox del consumidor tiene una sola fila | `MSG01.json` |
| MSG02 | Duplicado, versión vieja y `HabitacionLista` viejo reenviados | Inbox 1 fila antes y después del duplicado; versión vieja sin efecto; contradicción en la DLT; gate y ventas sin doble efecto | `MSG02.json` |
| REP01 | `GET /api/reportes/dashboard?idHotel=1` y `GET /api/reportes/ventas?idHotel=1&inicio=…&fin=…` | `ingresosHoy` 77.00; solo la venta PAGADO por `paid_at` con 7.00; suma del reporte 7.00 | `REP01.json` |
| REP02 | Rebuild de reporting en `reporting_rebuild_db` con grupo nuevo y comparación contra la instancia principal | Conteos idénticos (venta 4, recepción 18, gate 9, persona 7); dashboard 846.50 y reporte de ventas iguales | `REP02.json` |
| REP03 | Arranque en demo sin ventas y variante sin bootstrap | LISTO con manifiesto/watermarks y `bootstrapComplete=true` con 200; sin bootstrap 503 `REPORTING_NOT_READY` con `Retry-After: 60` | `REP03.json` |
| NOT01 | Cierre con SMTP en un puerto cerrado y redelivery del mismo `notificacion.checkout` | 200 en el cierre; 1 job por commandId; 4 intentos a 1/5/30 min y estado ERROR con último error SMTP_ERROR | `NOT01.json` |
| NOT02 | `sp_notificacion_resultado` con un lease vencido | El `lease_token` viejo no modifica el job: sigue ENVIANDO sin `sent_at` | `NOT02.json` |
| NOT03 | `notification-service --admin purge --before <hoy>` | Anonimiza solo la ENVIADA antigua conservando estado y hash; PENDIENTE/ERROR intactos; acción NOTIFICATION_PURGE en `audit_log` | `NOT03.json` |


## 7. Preguntas abiertas

Ninguna que bloquee la ejecución. Las correcciones de spec que aparezcan al ejecutar se anotan aquí
como preguntas abiertas, sin aplicar cambios directos, y la ejecución se detiene hasta su revisión.

### 7.1 Hallazgos de `qa-runbooks` revalidados en esta corrida

Los tres hallazgos registrados en `qa-runbooks` se han vuelto a medir sobre `main` actual
(arreglos #92, #94, #115 y #117 incluidos). **Los tres siguen vigentes**; ninguno se ha reparado.
No se ha modificado ningún servicio: quedan como evidencia para el plan dueño de cada consumidor.

- **`IA-qa-runbooks-01` (payload inválido va directo a la DLT). Vigente.** Se publicó en
  `hotel.habitacion.v1` un `HabitacionCreada` con envelope válido y payload incompleto
  (`eventId` nuevo, `aggregateVersion` 9001). El mensaje apareció en
  `hotel.habitacion.v1.reception-gates-v1.dlt` en **5,2 s**, no tras la secuencia de 1s/5s/30s/120s/300s
  (456 s). `room_gate` de la 101 quedó en OCUPADA, sin efecto del evento. Confirma que reception
  clasifica `MessageInvalidException` como no reintentable
  (`services/reception-service/.../KafkaMessagingConfig.java:52`).
- **`IA-qa-runbooks-02` (eventos de recepción derivados a la DLT de `hotel-ocupacion-v1`). Vigente.**
  Tras la corrida completa, `reception.recepcion.v1.hotel-ocupacion-v1.dlt` tiene **13 mensajes**
  (`CheckInRegistrado` y `SalidaRegistrada` de las estadías de la matriz), `hotel_db.inbox` con
  consumidor `hotel-ocupacion-v1` tiene **0 filas** y `hotel_db.ocupacion_projection` está
  **vacía**. El consumidor (`OcupacionConsumer`) captura la excepción y publica en la DLT sin
  registrar el motivo en el log estructurado.
- **`IA-qa-runbooks-03` (`projection_checkpoint.degraded=true` persiste). Vigente.** Con
  `hotel.habitacion.v1` ya sin ningún topic `*.dlt` (`reporting-proyeccion-v1.dlt` y el `dlt` de la
  instancia de rebuild borrados, dejando el residuo como evidencia),
  `projection_checkpoint` sigue con `degraded=true` para `hotel.habitacion.v1` y el dashboard
  responde `meta.degraded=true`. `touchCheckpoint` (`ProjectionStore`) nunca vuelve a `false` el
  flag; solo `markDegraded` lo enciende.

Estos hallazgos no impiden cerrar la matriz: los casos que los rozan (MSG02, REP01–REP03) miden su
propio criterio y lo cumplen. Quedan para que el plan dueño de `hotel-service` (consumidor de
ocupación) y el de `reporting-service` (reset del flag) los corrijan.


## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Los 41 casos de §6 con evidencia (comando o endpoint, resultado y salida guardada).
- [ ] `python scripts/validar-planes.py` desde la raíz con 0 errores; avisos resueltos o justificados en el PR.
- [ ] Cambios solo en tests/, scripts/ y el propio plan; sin tocar docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md.
- [ ] Sin preguntas abiertas.
- [ ] El plan pasa a `cerrado` en el último PR; lo revisa una persona y la ejecutora no lo mergea.
