# Implementación, migración y doble validación

## 1. Base inspeccionada y cambios intencionales

Referencia: commit `c10af777cc166319a7dff26eb9efd8cd09f60096`. No se utilizó el plan antiguo.

| Hallazgo en monolito | Decisión objetivo |
|---|---|
| Boot 3.5.0/Java 21, Angular 21, PostgreSQL | Migrar familias a Boot 4.1.1/Java 25, Spring Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6; al implementar fijar patches compatibles y lockfiles |
| Seguridad permite /api/persona/** y producto sin roles suficientes | Default deny, DTO separados, roles/sede/propietario por servicio |
| Registrar persona acepta idTipoPersona del request | Registro público específico solo CLIENTE; creación de personal solo ADMIN |
| DTO Persona tiene clave, mapper de salida inspeccionado no la asigna | No afirmar fuga de hash observada; retirar campo del DTO de salida y probarlo |
| sp_RegistrarRecepcion desactiva estadía anterior de misma habitación | Reemplazar por 409 + índice único; nunca cierre implícito |
| Recepción puede crear persona implícitamente | Crear cliente antes; recepción requiere idCliente validado |
| Fecha de entrada real se fija CURRENT_DATE aunque DTO tenga fechaEntrada | MVP de check-in inmediato, no sistema de reservas futuras |
| sp_RegistrarVenta descuenta PRODUCTO | Descuento pasa solo a inventory; sales no conserva escritura a PRODUCTO |
| fn_agregaralcarrito se invoca desde repositorio pero no aparece en scripts | Implementar función de carrito local; no atribuirle un comportamiento no verificado |
| sp_RegistrarSalida modifica recepción, ventas y habitación | Separar cierre idempotente, gate local, comando de limpieza |
| Venta.estado es PENDIENTE/PAGADO y reportes filtran PAGADO | Mantener estado de pago, añadir estadoOperacion |
| DELETE venta puede borrar filas/historial | Anulación impagada con restitución, sin borrado físico |
| Estado habitación 2 significa OCUPADO | Sacar ocupación del catálogo físico; migración semántica explícita |
| Carrito devuelve texto/array en vez de ApiResponse | Homogeneizar; adaptar cliente Angular en mismo PR |
| No DetalleVentaController real | No inventar microservicio ni CRUD separado; líneas dentro de venta |

## 2. Orden simple elegido

Corte coordinado con ventana de mantenimiento para este proyecto académico, no migración en vivo con dos escritores.

1. Base: Maven/contratos comunes, seguridad, Gateway/Eureka, Compose infra y CI.
2. Identity y Hotel: BD/seeds, permisos, catálogo público y pantallas base.
3. Reception: gates, snapshots, check-in, admisión de operaciones.
4. Inventory + Sales juntos como módulo de integración: débito, compensación, carrito, pagos; nunca activar inventory separado mientras el monolito todavía escribe PRODUCTO.
5. Cierre reception↔sales y limpieza hotel↔reception; operar ambos juntos. No mitad del flujo viejo con mitad nuevo.
6. Notification y Reporting consumiendo productores nuevos; bootstrap.
7. Adaptar admin Angular a contrato nuevo; public-web solo catálogo. Ejecutar flujos completos.
8. Corte final de rutas por grupos, con frontend compatible desplegado junto al Gateway.

Reporting no va “primero” sin datos. No introducir Debezium para evitar mapear eventos: outbox explícito en nuevos servicios y bootstrap controlado bastan.

## 3. Importación de datos existentes

Para demo desde cero: Flyway + seeds limpios. Para conservar datos reales del monolito: procedimiento distinto, NO ejecutar seeds encima.

1. Backup verificado restaurable de BD original. Fijar commit de apps y congelar escrituras (incluidas SP, procesos y usuarios SQL).
2. Auditar duplicados de correo/documento, múltiples estadías activas, stock negativo, ventas sin recepción/detalles y pagos inconsistentes. Generar reporte; no fusionar clientes ni corregir importes silenciosamente.
3. Crear nuevas DB vacías. Migrar IDs y snapshots; todos los datos originales son sede 1 salvo mapeo explícito aprobado. Sedes 2/3 son demo, no se inventa reparto de historia.
4. Importar identity; validar hashes BCrypt, requerir reset seguro para credenciales incompatibles. No transformar claves desconocidas en cuentas con password común.
5. Importar hotel y catálogo. Habitaciones OCUPADO necesitan recepción activa consistente; gate OCUPADA se importa en reception. Físico LISTA no equivale a libre. Estado legacy MANTENIMIENTO se conserva bloqueado hasta revisión.
6. Importar inventory, sales y reception preservando referencias por ID sin FKs entre DB. Ventas previas válidas se marcan CONFIRMADA, estado de pago original; no generar descuentos retrospectivos (stock ya los refleja). Ventas PAGADAS exigen paid_at verificable: si el origen no tiene fecha de pago, no sustituirla por fecha de creación. Esa historia queda en el legado de solo lectura hasta conciliación aprobada; no se importa a reportes de ingreso por día con fechas inventadas.
7. Para venta heredada no existe movimiento inventario demostrable: no permitir anulación automática; marcar origen=LEGACY y bloquear restitución hasta conciliación. El schema de sales ya define origen=NUEVA/LEGACY; importar con LEGACY. No improvisar movimientos.
8. Copiar estados de limpieza/bloqueos de manera conservadora y correlacionada. Crear ciclos/tareas iniciales para limpieza pendiente, no desbloquear por importar LISTA.
9. Bootstrap snapshots y watermarks con tráfico congelado. Cargar reporting, publicar estado inicial y establecer checkpoints/inbox; procesar proyecciones receptoras.
10. Verificar counts, checksums de claves, saldos, ingresos por rangos y permisos. Secuencias ajustadas después del mayor ID.
11. Cambiar rutas/front y abrir tráfico nuevo; revocar credenciales de escritura del monolito.

Rollback ANTES de nuevas escrituras: restaurar rutas al monolito preservado. DESPUÉS de escrituras nuevas: congelar, exportar y reconciliar; no devolver tráfico a una BD vieja perdiendo ventas. No se promete rollback instantáneo.

## 4. Cambios frontend obligatorios

- URL base del Gateway; nunca puertos individuales ni broker.
- Selección de sede para personal; guardar idHotel por flujo, no como autorización.
- JWT en memoria, guards y manejo 401/403/404. Vista privada cliente separada de menú administrativo.
- Nuevo catálogo de cliente vía sales; LP solo /api/public.
- No enviar campos calculados del DTO viejo en POST/PUT. Conservar adaptadores de nombres donde sea seguro.
- EstadoOperacion separado del pago en tablas; 202 muestra “pendiente”, no éxito definitivo.
- Idempotency-Key creada una vez por intención; conservar en reintento. Después de 409 validación corregida, nueva intención/nueva clave.
- ETag → If-Match en ediciones; 412 pide recargar, no sobrescribir.
- Cierre muestra desglose y “no volver a cobrar” si está en recuperación.
- Quitar social login, reserva futura y CRUD de códigos fijos del MVP.
- DELETE de maestros baja lógica y ventas anulación; texto de UI preciso.

## 5. Matriz de aceptación funcional

Columnas: **ID | Precondición/datos de entrada | Acción exacta (endpoint o evento) | Resultado exigido (criterio cuantitativo) | Evidencia**. Se ejecuta con PostgreSQL y brokers reales sobre DB efímera; los IDs devueltos por la API se guardan como variables (no se asume el `1001` de los ejemplos). Cada fila referencia su fixture; los valores concretos viven una sola vez en la tabla de fixtures.

### Fixtures compartidos

| Fixture | Contenido | Creación |
|---|---|---|
| F1 | Estadía ACTIVA: cliente 100 en habitación 101 (sede 1, tarifa 70.00), adelanto 20.00 `EFECTIVO`, salida mañana; gate OCUPADA | seeds dev + `POST /api/recepcion/registrar`; guardar idRecepcion |
| F2 | Venta PENDIENTE sobre F1: producto 1 (Agua 500ml) ×2, total 7.00; stock 20 → 18 | `POST /api/venta` {PENDIENTE}; guardar idVenta y operationId |
| F3 | Venta PAGADA: F2 pagada con `EFECTIVO` | `PUT /api/venta/{idVenta}` {PAGADO, EFECTIVO} |
| F4 | Cierre de F1 en CONFIRMANDO (cálculo congelado, Sales no respondió) | `POST /api/recepcion/registrar-salida` con Sales detenido tras aceptar; guardar closureId |
| F5 | Habitación 102 con operación MANTENIMIENTO `PREPARANDO` y bloqueo `ACTIVO` | `POST /api/habitacion/102/mantenimiento` con reception detenido a mitad de admisión; guardar blockId |
| F6 | Rechazo durable: habitación 201 (sede 2) ocupada por cliente 101 e intento de mantenimiento rechazado | check-in en 201 + `POST /api/habitacion/201/mantenimiento`; guardar blockId R |
| F7 | Venta en CANCELACION_PENDIENTE: F2 con resultado de débito incierto | simular timeout de inventory durante el débito; no liberar operación |
| F8 | F1 CERRADA: cierre 200 y gate LIMPIEZA_PENDIENTE | `POST /api/recepcion/registrar-salida` (200); guardar cleaningCycleId C1 |
| F9 | Venta LEGACY sin movimiento conciliado (`origen=LEGACY`) | fila de importación/migración en DB efímera |

### Casos

| ID | Precondición/datos de entrada | Acción exacta (endpoint o evento) | Resultado exigido (criterio cuantitativo) | Evidencia |
|---|---|---|---|---|
| SEC01 | Sin token | `GET /api/persona/listar` | 401 `UNAUTHENTICATED`; sin datos ni enumeración | Respuesta HTTP + log del Gateway |
| SEC02 | Sin token | `POST /api/auth/registro` con `"idTipoPersona":1` en el body | 400 `VALIDATION_ERROR`; persona no creada (búsqueda posterior 404) | Respuesta + `identity_db.persona` sin fila |
| SEC03 | Token E sede 1 | `GET /api/habitacion/buscar/{id 201}` y `GET /api/reportes/dashboard?idHotel=2` | 404 `RESOURCE_NOT_FOUND` en ambos; sin datos de sede 2 | Respuestas |
| SEC04 | Token C del cliente 100; estadía ajena de cliente 101 (F1) | `GET /api/carrito/listar/{idRecepcion ajena}` y `POST /api/venta` con esa recepción | 404; sin filas de carrito ni venta creadas | Respuestas + counts en sales_db |
| SEC05 | Token humano A | Llamar `GET/POST /internal/stock/descontar` directo al puerto interno | 403 `FORBIDDEN` (o 404 sin ruta por Gateway); stock 20 intacto | Respuesta + `producto_sede.cantidad` |
| SEC06 | JWT con firma ajena o `aud` incorrecta | `GET /api/auth/me` y `GET /api/hotel/listar` | 401 en cada servicio | Respuestas por servicio |
| SEC07 | Dos ADMIN activos (1 y 2) | PUT `actualizar/2` {estado:false} y DELETE `eliminar/1` simultáneos | Exactamente uno 200 y uno 409 `LAST_ADMIN_PROTECTED`; queda ≥ 1 ADMIN activo con clave | Respuestas + `persona` (estado, clave_hash) |
| SEC08 | Origen permitido http://localhost:4200 | Preflight con `If-Match`/`Idempotency-Key`; respuesta con ETag/Location/Retry-After; origen http://evil.test | Preflight permitido; headers legibles; origen ajeno sin `Access-Control-Allow-Origin` | Respuestas preflight/reales |
| LP01 | Sin token | Los 4 GET públicos (hoteles, detalle, categorías, detalle categoría) | 200 en los 4; solo campos de HotelPublico/CategoriaPublica (assert de claves) | Respuestas + JSON Schema |
| LP02 | Bundle de public-web | Buscar `?fecha=` u ocupación; revisar bundle | Parámetro ignorado o 400; sin dato privado alguno; sin endpoint nuevo | Respuestas + inspección de bundle |
| CRUD01 | Token A | Alta y edición de sede, piso, categoría, habitación y producto; GET y DELETE después | Filas persistidas; `version` incrementa en cada edición; DELETE deja `estado=false`; PUT sin `If-Match` 428 | GET posteriores + DB |
| OCC01 | Habitación 101 libre; clientes 100 y 101 | Dos `POST /api/recepcion/registrar` simultáneos a 101 | Uno 201; el otro 409 `ROOM_OCCUPIED`; una sola fila ACTIVA por habitación | Respuestas + índice único + `recepcion` |
| OCC02 | Habitación 102 libre; tokens A | Check-in y `POST /api/habitacion/102/mantenimiento` concurrentes sobre la misma habitación | Solo una admisión válida: gate final OCUPADA o BLOQUEADA, nunca ambas; el perdedor 409 | Respuestas + `room_gate`/`operacion_habitacion` |
| CLN01 | F8 con Rabbit pausado | Reintentar check-in; reanudar Rabbit; `PUT /api/habitacion/101/limpieza-completada {cleaningCycleId}` | 409 `ROOM_NOT_READY` hasta completar el ciclo correcto; después 201 | Respuestas + `limpieza` COMPLETADA + gate LIBRE |
| CLN02 | F8 con ciclo C1 completado | Reenviar `limpieza.solicitada` C1; enviar `HabitacionLista` viejo tras nuevo check-in | No reabre la tarea C1 ni libera el ciclo actual; un solo registro por ciclo | Consola Rabbit + `limpieza` + gate |
| CLN03 | Cierre de F1 genera C1; variante: nuevo check-in antes de procesar `SalidaRegistrada` | Entregar `HabitacionLista` antes de la salida o después del check-in nuevo | C1 se concilia sin liberar la ocupación posterior; `gate_projection` no queda LIMPIEZA_PENDIENTE eterno | `gate_projection` + `limpieza_completada_projection` + gate |
| OCC03 | F6 (bloqueo R rechazado) | Cerrar la estadía de 201 y reintentar el mismo `blockId R`; luego nueva intención con otro blockId | R sigue 409 `GATE_NOT_AVAILABLE` (rechazo durable); el nuevo 200 | Respuestas + `bloqueo_habitacion` RECHAZADO vs ACTIVO |
| OCC04 | Piso 11 o categoría con habitación activa | DELETE del padre y alta/reactivación de habitación concurrentes | Uno 409 `ACTIVE_DEPENDENCIES`; nunca habitación activa bajo padre inactivo | Respuestas + counts |
| STK01 | Stock producto 1 = 20 | Dos `POST /api/venta` ×15 simultáneas (claves distintas) | Una 201; otra 409 `STOCK_INSUFFICIENT`; stock final 5; un solo movimiento DESCONTADO | Respuestas + `producto_sede` + `movimiento_venta` |
| STK02 | Stock producto 1 = 20, producto 3 = 5 | Venta multítem p1 ×2 y p3 ×10 | 409 `STOCK_INSUFFICIENT`; stocks 20 y 5 intactos (sin descuento parcial) | Respuesta + cantidades |
| STK03 | Clave y payload de F2 | Repetir `POST /api/venta` con la misma `Idempotency-Key` | Mismo idVenta; un solo débito (stock 18) y un solo movimiento | Respuesta + counts |
| STK04 | F7 con débito en vuelo | Solicitar anulación (emite restitución) y entregar después el débito tardío | Lápida RESTITUIDO; el débito tardío 409 `DEBIT_CANCELLED`; stock intacto | Respuestas + `movimiento_venta` |
| STK05 | F7 (timeout después de débito) | Ejecutar el job de recuperación cada 30s | Estado terminal consistente (CONFIRMADA o CANCELADA); nunca confirmar tras compensar; un movimiento por venta | Estado de `venta` + `movimiento_venta` |
| STK06 | Admisión con timeout; `recepcion.operacion-finalizar` con RECHAZADA llega antes de la respuesta de admitir | Publicar el comando y luego la respuesta tardía | Lápida FINALIZADA con tipo completo; el admitir tardío 409; sin débito | `operacion_consumo` + ausencia de movimiento |
| STK07 | Worker caído tras `DEBITO_SOLICITADO` (simular antes y después del HTTP) | Reiniciar el worker y esperar la recuperación | Ambos casos terminan en la misma compensación segura; la operación no se libera antes de RESTITUIDO | Estado de operación + `movimiento_venta` |
| PAY01 | Token C; F2 existente | `POST /api/venta` {estado:PAGADO}; variante con `precioUnitario` en `detalles` | 403 (rol) o 400 (campo calculado); venta PENDIENTE y precio autoritativo intacto | Respuestas + fila intacta |
| PAY02 | F3 | `DELETE /api/venta/{idVenta}?motivo=...` | 409 `SALE_ALREADY_PAID`; líneas, receipt y paid_at intactos | Respuesta + filas |
| OUT01 | F1 con una venta admitida en vuelo | Iniciar cierre mientras la admisión no es terminal | 409 `OPERATIONS_IN_PROGRESS`; ninguna venta admitida después del cierre | Respuestas + estados |
| OUT02 | F4 (Sales confirmó; reception cayó antes de completar) | Reintentar con el mismo closureId | Un receipt persistido y una salida; ventas PAGADO una sola vez | Respuestas + `cierre_consumos` + `cierre` |
| OUT03 | F1 (alojamiento 70, adelanto 20) | `POST registrar-salida` con `montoRecibido:56.99`; variante `costoPenalidad:71` | 409 `AMOUNT_MISMATCH` con cotización y cierre RECHAZADO conservado; penalidad > precioInicial rechazada sin cierre | Respuestas + `cierre` RECHAZADO |
| OUT04 | F1 sin ventas | `POST registrar-salida` con montoRecibido 50.00 | Receipt con totalConsumos 0.00 y sede 1; cobroAhora 50.00; salida normal | Respuesta + `cierre_consumos` |
| OUT05 | F4 | Matar el proceso en VALIDANDO, en CONFIRMANDO y después del receipt; reiniciar | VALIDANDO recalcula; CONFIRMANDO reenvía payload congelado; un cobro y una salida; COMPLETADO no recalcula | Receipts/estados por fase |
| OUT06 | F1 | `PUT /api/recepcion/actualizar/{id}` con fechaSalida que deja precioInicial < adelanto | 409 `ADVANCE_EXCEEDS_NEW_TOTAL`; precioInicial y adelanto sin cambios | Respuesta + fila |
| MSG01 | F2 commiteada con el broker detenido | Levantar el broker y dejar el poller | Outbox publica una vez (`published_at`); el efecto se procesa una vez (inbox) | `outbox` + estado del consumidor |
| MSG02 | Duplicar cada mensaje Kafka/Rabbit; invertir Limpieza/Salida; versión vieja | Reenviar en órdenes invertidos | Inbox/version/ciclo mantienen invariantes: sin doble stock, pago ni limpieza; contradicción → DLQ | Inbox/DLT + estados |
| REP01 | F3 (7.00 pagada hoy) + F8 (adelanto 20 + final 50 hoy) | Consultar dashboard y reporte de ventas | `ingresosHoy = 77.00` (20 + 50 + 7, sin duplicar); ventas solo PAGADO por `paid_at` | Respuesta API + caso A/C del doc |
| REP02 | Snapshot + stream de la corrida completa | Rebuild de reporting en DB nueva | Mismos totales y claves (checksums por reporte) | Comparación de resultados |
| REP03 | Demo recién sembrada sin ventas/estadías; topics vacíos | Arrancar reporting y esperar bootstrap | `LISTO` al cumplir manifiesto/watermarks, sin esperar mensajes; API 200 con bootstrapComplete=true | `projection_bootstrap` + meta |
| NOT01 | SMTP (Mailpit) caído; comando repetido | Cerrar F1 y repetir `notificacion.checkout` | Negocio no bloqueado (salida 200); un job por commandId; reintentos 1min, 5min, 30min; ERROR tras agotar | `notification_job` + Mailpit |
| NOT02 | Job ENVIANDO con lease vencido | Responder tarde desde el worker anterior | Su `lease_token` no modifica el job nuevo; duplicado SMTP sigue siendo limitación explícita | Fila del job + log |
| NOT03 | Job ENVIADA con destinatario/variables | `purge --before` sobre > 30 días; intentar purgar PENDIENTE/ERROR | Purga permitida en ENVIADA (conserva hash/estado); PENDIENTE/ERROR intactos; acción en `audit_log` | Fila + `audit_log` |

## 6. Doble validación antes de implementar y antes de entregar

Validación documental: dueños únicos, routes sin colisiones, bodies/headers/types, sedes, productores/consumidores emparejados, seeds compatibles, todos los estados con salida/recuperación.

Validación ejecutable futura: unitarias de reglas y JWT; tests PostgreSQL reales (no sustituir locks por H2); integración con brokers reales/Testcontainers; colección Postman por flujo; Angular build + pruebas de roles/pantallas. CI ejecuta mvn verify y npm ci/build/test de cada app. Comandos concretos se fijarán al crear módulos.

No marcar tests de esta tabla como pasados solo por existir el documento. El código actual sigue siendo monolito y no implementa todavía estas garantías.

## 7. Entregables de implementación

OpenAPI por servicio y JSON Schemas de mensajes; migraciones/seed scripts separados; Dockerfiles/Compose con healthchecks; colecciones Postman; evidencia de matriz; README de arranque; informe/presentación exigidos por la guía académica. Sin secretos, dumps personales, target/node_modules ni metadatos de IDE.
