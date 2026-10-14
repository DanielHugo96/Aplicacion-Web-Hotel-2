# Spec Delta

## Purpose

Contrato de comportamiento del read model de reporting: consumo idempotente de los 6 topics del grupo `reporting-proyeccion-v1`, proyecciones de catálogo, ocupación, estadías, stock y ventas, bootstrap con manifiesto y watermarks, reportes REST con consistencia eventual, caja del día `ingresosHoy` y degradación. Los detalles de contrato viven en [`reporting/api.md`](../../../../../../docs/especificacion/microservicios/reporting/api.md) y [`reporting/base-de-datos.md`](../../../../../../docs/especificacion/microservicios/reporting/base-de-datos.md); esta capacidad describe el comportamiento exigible.

## ADDED Requirements

### Requirement: Consumo Kafka idempotente de los seis topics

reporting SHALL consumir `identity.persona.v1`, `hotel.sede.v1`, `hotel.habitacion.v1`, `reception.recepcion.v1`, `inventory.stock.v1` y `sales.venta.v1` con el grupo `reporting-proyeccion-v1`, aplicando cada mensaje una sola vez (inbox en la misma transacción que el efecto) y confirmando el offset solo después del commit local. SHALL aplicar solo versiones mayores del mismo agregado (skip-if-older) y SHALL NOT consumir `inventory.movimiento.v1`. Un mensaje no procesable tras los reintentos del contrato SHALL terminar en `<topic>.reporting-proyeccion-v1.dlt` conservando envelope, key y error no sensible, y SHALL marcar la proyección como degradada. reporting SHALL NOT producir eventos ni comandos ni usar RabbitMQ.

#### Scenario: Entrega duplicada

- **WHEN** el mismo mensaje se entrega dos veces
- **THEN** el efecto se aplica una sola vez (fila de `inbox`) y ningún reporte cambia

#### Scenario: Versión vieja del agregado

- **WHEN** llega un snapshot con versión menor o igual a la ya proyectada para su agregado
- **THEN** el snapshot vigente no se rebaja

#### Scenario: Mensaje no procesable

- **WHEN** un payload no valida o su efecto falla de forma permanente tras los reintentos
- **THEN** queda en la DLT del topic con su `eventId` y key, y la proyección pasa a `degraded`

### Requirement: Proyección de personas, sedes y habitaciones

reporting SHALL proyectar `persona_projection` y `hotel_projection` con upsert por versión, conservando el snapshot completo con `estado=false` en una baja; SHALL proyectar `habitacion_projection` desde HabitacionCreada y HabitacionSnapshot con `numero`, `categoria_nombre`, `precio`, `estado`, `estado_fisico` y el seguimiento de la versión de habitación. Un snapshot genérico de habitación SHALL NOT alterar la ocupación ni liberar gates. Los reportes SHALL usar nombre y apellido, nunca documento ni correo.

#### Scenario: Baja de catálogo

- **WHEN** llega el snapshot de baja (`estado=false`) de una persona o sede
- **THEN** la fila conserva el resto de campos y queda inactiva; un evento posterior de versión menor no la reactiva

#### Scenario: Snapshot genérico de habitación

- **WHEN** llega HabitacionSnapshot de una habitación activa
- **THEN** `habitacion_projection` actualiza el catálogo y la ocupación no cambia

### Requirement: Proyección de gates y limpieza entre topics

reporting SHALL mantener `gate_projection` alimentada por eventos físicos de `hotel.habitacion.v1` y por snapshots de `reception.recepcion.v1`, creada aunque el catálogo de habitación no haya llegado y sin FK con `habitacion_projection`. Hotel SHALL inicializar solo la parte física y SHALL NOT reemplazar un gate ya proyectado desde reception; HabitacionLista SHALL registrar `limpieza_completada_projection` y SHALL liberar solo un gate LIMPIEZA_PENDIENTE con `cleaningCycleId` coincidente, sin cambiar un gate OCUPADA posterior; HabitacionHabilitada SHALL liberar solo el bloqueo con `blockId` coincidente. Cada snapshot de reception SHALL aplicarse por `roomVersion` y `ocupacionActual` y luego reconciliarse contra los ciclos completados, de modo que una SalidaRegistrada posterior a su HabitacionLista no deje un LIMPIEZA_PENDIENTE eterno. Un LISTA genérico SHALL NOT ser permiso de liberación.

#### Scenario: Gate sin catálogo

- **WHEN** llega un evento de habitación antes que su fila de catálogo
- **THEN** existe `gate_projection` para esa habitación y el reporte no falla por la ausencia

#### Scenario: HabitacionLista del ciclo correcto

- **WHEN** HabitacionLista llega antes que la SalidaRegistrada de su ciclo
- **THEN** el ciclo queda completado y la salida posterior se concilia sin dejar LIMPIEZA_PENDIENTE eterno

#### Scenario: Ciclo o bloqueo ajeno

- **WHEN** llega HabitacionLista de un ciclo anterior o HabitacionHabilitada de otro `blockId`
- **THEN** el gate vigente no se libera

### Requirement: Proyección de estadías y cierres

reporting SHALL proyectar `recepcion_projection` con upsert por `version` de la estadía: CheckInRegistrado y RecepcionActualizada fijan snapshots, importes y estado; SalidaRegistrada fija el desglose del cierre (`total_alojamiento`, `total_consumos`, `total_general`, `cobro_ahora`, `closure_id` y medio de pago del cierre) y la fecha de cierre del recibo. Una anulación de check-in (RecepcionActualizada con CERRADA, archivada y `cierre=null`) SHALL proyectarse con los importes del recibo en 0.00 y sin `closure_id`, `fecha_cierre` ni `medio_pago_cierre`, cumpliendo el CHECK de CERRADA sin fabricar un recibo. La proyección SHALL conservar importes y snapshots de una estadía archivada, sin borrarlos; el reporte de cobros SHALL usar ese snapshot y SHALL NOT volver a sumar ventas.

#### Scenario: Cierre proyectado

- **WHEN** llega SalidaRegistrada de un cierre COMPLETADO
- **THEN** la estadía queda CERRADA con el desglose del recibo y su fecha de cierre

#### Scenario: Archivo conserva cobro

- **WHEN** una estadía CERRADA se archiva
- **THEN** sus importes y snapshots permanecen disponibles para los reportes

#### Scenario: Anulación de check-in

- **WHEN** llega la RecepcionActualizada de una anulación (CERRADA, archivada y sin cierre)
- **THEN** la fila queda CERRADA con importes de recibo en 0.00 y sin fecha de cierre, y no aparece en cobros ni suma cobro final de alojamiento en la caja del día

### Requirement: Proyección de stock y ventas

reporting SHALL proyectar `stock_projection` por `(id_hotel, id_producto)` con precio, cantidad, umbral y estado efectivo, y `venta_projection` con estado, estadoOperacion, total, medioPago, createdAt y paidAt, reemplazando `venta_detalle_projection` en la misma transacción del snapshot. Las anulaciones SHALL ser snapshots (nunca DELETE físico) que conservan el historial, y un pago SHALL contarse una sola vez por su `paid_at`.

#### Scenario: Detalle reemplazado

- **WHEN** llega una versión nueva de una venta con sus líneas
- **THEN** `venta_detalle_projection` queda con las líneas exactas del snapshot

#### Scenario: Venta anulada

- **WHEN** llega el snapshot de anulación de una venta
- **THEN** la fila conserva su historial con el estado de la anulación y no se borra

### Requirement: Bootstrap con manifiesto y watermarks

reporting SHALL ejecutar el bootstrap con `projection_bootstrap` en CARGANDO y un `manifest` con conteos esperados por dominio y watermarks `{topic,partition,lastOffset}` de una exportación o seed terminado. El job SHALL crear checkpoint para todas las particiones suscritas, incluidas las vacías (`last_offset=-1`, `last_event_at=null`, `updated_at` de la hora de bootstrap), y SHALL marcar LISTO en una sola transacción solo tras cargar los snapshots de los cinco dominios y alcanzar los watermarks; un topic de ventas o estadías sin tráfico SHALL NOT impedir el LISTO. `bootstrapComplete` SHALL salir de `projection_bootstrap` (estado LISTO), no de la presencia de ventas. Antes de LISTO, la API SHALL responder 503 `REPORTING_NOT_READY`; con LISTO, 200.

#### Scenario: Demo sembrada sin ventas ni estadías (REP03)

- **WHEN** arranca reporting sobre una demo recién sembrada con topics de ventas y estadías vacíos
- **THEN** `projection_bootstrap` llega a LISTO al cumplir manifiesto y watermarks, sin esperar mensajes inexistentes, y la API responde 200 con `bootstrapComplete=true`

#### Scenario: API antes del LISTO

- **WHEN** se consulta cualquier reporte con el bootstrap en CARGANDO
- **THEN** responde 503 `REPORTING_NOT_READY`

### Requirement: Reportes REST con meta de consistencia eventual

reporting SHALL exponer `GET /api/reportes/productos-bajo-stock` (activos con cantidad ≤ `limite`, default 5), `GET /api/reportes/ventas` (solo PAGADO por `paid_at` en `[inicio, fin)`, agrupado por producto), `GET /api/reportes/ocupacion` (check-ins por fecha de entrada local, con `inicio`/`fin` únicamente en medianoche America/Lima) y `GET /api/reportes/cobros` (CERRADAS por fecha de cierre, con `medioPagoAdelanto`/`medioPagoCierre`, null = NO_REGISTRADO), todos con `idHotel` obligatorio, rango ISO-8601 con offset, `inicio<fin` y máximo 366 días, orden estable (reportes por clave de agrupación; cobros por fechaCierre, idRecepcion), paginación `L` y `data[]` sin filas (nunca 404). Cada respuesta SHALL incluir meta `{updatedAt, consistency:"EVENTUAL", degraded, bootstrapComplete}`, con `updatedAt` = mínimo checkpoint con timestamp de ingestión de los topics requeridos, sin prometer frescura absoluta. Un rango fuera de límites, `idHotel` ausente o una hora distinta de medianoche en ocupación SHALL responder 400 `VALIDATION_ERROR`.

#### Scenario: Sin filas

- **WHEN** un reporte no tiene datos en el rango
- **THEN** responde 200 con `data=[]` y su meta

#### Scenario: Rango inválido

- **WHEN** `inicio ≥ fin` o el intervalo supera 366 días
- **THEN** responde 400 `VALIDATION_ERROR`

#### Scenario: Ocupación con hora distinta de medianoche

- **WHEN** `inicio` o `fin` de ocupación no caen en medianoche America/Lima
- **THEN** responde 400 `VALIDATION_ERROR`

### Requirement: Dashboard e ingresosHoy sin doble conteo

reporting SHALL exponer `GET /api/reportes/dashboard` con `idHotel` opcional solo para ADMIN (consolidado) y obligatorio para EMPLEADO (sede asignada), devolviendo `{habitacionesOcupadas, habitacionesDisponibles, productosBajoStock, ingresosHoy, moneda:"PEN"}`; `habitacionesDisponibles` SHALL contar activas, de física LISTA, sin ocupación y sin limpieza pendiente. `ingresosHoy` SHALL ser adelantos de check-ins de hoy + cobro final de ALOJAMIENTO de cierres de hoy (derivado `total_alojamiento − adelanto`) + ventas PAGADO con `paid_at` de hoy, cada pago una sola vez, y SHALL NOT sumar el `totalGeneral` del cierre otra vez ni las ventas CONFIRMADA impagas. Los casos numéricos A, B y C de la spec SHALL cumplirse.

#### Scenario: Caso A canónico

- **WHEN** adelanto 20.00, cierre de alojamiento 70.00 y ventas pagadas hoy 7.00
- **THEN** `ingresosHoy = 77.00` (20.00 + 50.00 + 7.00)

#### Scenario: Caso B adelanto igual al alojamiento

- **WHEN** adelanto 70.00 y cierre sin consumos con cobro final 0.00
- **THEN** `ingresosHoy = 70.00` y el cierre completa normalmente con `cobroAhora 0.00`

#### Scenario: Caso C venta pagada antes del cierre

- **WHEN** una venta de 7.00 se paga hoy antes de un cierre de alojamiento de 70.00 con adelanto 20.00
- **THEN** `ingresosHoy = 77.00` y la venta se cuenta por su `paid_at` una sola vez

### Requirement: Identidad y aislamiento por JWT

reporting SHALL validar el JWT en cada ruta (algoritmo RS256, issuer, audiencia y exp) y SHALL derivar de él el rol y las sedes; nunca SHALL autorizar con cabeceras `X-User-Id`, `X-Role` ni `X-Hotel-Id`. ADMIN SHALL tener alcance global; EMPLEADO solo sus sedes asignadas; CLIENTE SHALL recibir 403 `FORBIDDEN`. Una sede ajena al alcance del token o inexistente SHALL responder 404 `RESOURCE_NOT_FOUND` (anti-enumeración). reporting SHALL NOT exponer rutas internas ni scopes de máquina, y el Gateway SHALL NOT exponer `/actuator/**`.

#### Scenario: Empleado de otra sede (SEC03)

- **WHEN** un token E de la sede 1 consulta `GET /api/reportes/dashboard?idHotel=2`
- **THEN** responde 404 `RESOURCE_NOT_FOUND` sin datos de la sede 2

#### Scenario: Cliente consultando reportes

- **WHEN** un token C consulta cualquier reporte
- **THEN** responde 403 `FORBIDDEN`

#### Scenario: Sin token o token inválido

- **WHEN** la petición no lleva `Authorization` o el token no valida firma, issuer, audiencia o exp
- **THEN** responde 401 `UNAUTHENTICATED` sin datos

### Requirement: Salud, degradación y modo no utilizable

reporting SHALL exponer `GET /actuator/health/liveness` (200 con el proceso vivo, sin consultar dependencias) y `GET /actuator/health/readiness` (200 solo con PostgreSQL arriba); Kafka SHALL reflejarse como degradación visible sin bloquear las consultas que aún sean utilizables. Con DLT o error conocido y proyección utilizable SHALL responder 200 con `degraded=true` y advertencia visible; con más de 5 minutos sin conexión activa SHALL responder 503 `REPORTING_UNAVAILABLE` con `Retry-After`. Un topic sin tráfico reciente SHALL NOT probar atraso por sí solo.

#### Scenario: DLT sin reparar

- **WHEN** existe un mensaje en DLT dentro del umbral y la proyección sigue siendo utilizable
- **THEN** el reporte responde 200 con `degraded=true` y advertencia visible

#### Scenario: Kafka caído más de 5 minutos

- **WHEN** el consumidor lleva más de 5 minutos sin conexión activa
- **THEN** los reportes responden 503 `REPORTING_UNAVAILABLE` con `Retry-After`
