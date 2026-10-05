# Flujos, estados y recuperación

Estas reglas prevalecen sobre una implementación que simplemente encadene llamadas HTTP. No hay transacción distribuida ni garantía de que dos brokers conserven un orden entre sí.

## 1. Check-in inmediato

1. Personal autentica, presenta idCliente, idHabitacion, idHotel, fechaSalida, adelanto e Idempotency-Key.
2. Reception consulta por Feign cliente activo/tipo Cliente y habitación activa, sede activa, estado físico LISTA. Guarda snapshot validado de cliente, habitación y tarifa; no crea personas silenciosamente.
3. Transacción local: lock `room_gate`; exige LIBRE y catálogo sincronizado; inserta recepción ACTIVA, ocupa gate, incrementa roomVersion, guarda outbox Kafka CheckInRegistrado y comando de notificación.
4. Índice único parcial en idHabitacion para ACTIVA/CERRANDO es segunda defensa. Un conflicto devuelve409; jamás cierra otra estadía automáticamente.
5. Commit y201. Feign caído antes del paso3 →503, sin estadía. Reintento tras commit responde la misma estadía.

Dos solicitudes a la misma habitación: solo una puede ganar. Hotel.OCUPADA es proyección para mostrar, nunca condición de admisión.

## 2. Venta y stock

Estados técnicos: PENDIENTE → CONFIRMADA | RECHAZADA | CANCELACION_PENDIENTE → CANCELADA.
Estado de pago separado: PENDIENTE | PAGADO. No se permite cancelar una venta PAGADA.

1. Sales crea venta técnica PENDIENTE y operationId UUID con clave idempotente; las líneas contienen idProducto/cantidad, sin precio autorizado por el cliente.
2. Feign a reception registra operación en esa estadía. Su transacción bloquea la recepción y solo admite si ACTIVA; valida propietario/sede. Operación única por UUID y venta. Un timeout aquí no permite descontar: se cancela y se finaliza la operación mediante comando, incluso si llega tarde el registro.
3. Feign a inventory `POST /internal/stock/descontar` con idVenta,idHotel,items. Inventory crea/lock registro único del movimiento, compara hash y bloquea filas de stock ordenadas por idProducto. En una transacción descuenta todos los ítems o ninguno, calcula precios vigentes y persiste resultado inmutable.
4. Sales aplica CAS `WHERE estado_operacion='PENDIENTE'`: si gana, guarda detalles/precios devueltos, total y CONFIRMADA, emite VentaSnapshot y comando recepcion.operacion-finalizar. Responde201. Nunca vuelve a descontar por generar una respuesta.
5. Si inventory rechaza por insuficiencia/inactividad: RECHAZADA sin descuento, libera operación y409. Una misma venta rechazada no se reintenta con nuevo cuerpo; el usuario inicia nueva venta con nueva clave.
6. Si resultado remoto es incierto: CAS PENDIENTE→CANCELACION_PENDIENTE + outbox stock.restituir en la misma transacción; responde202. Si llega luego un éxito HTTP, no puede confirmar porque perdió CAS.
7. Inventory procesa restitución idempotente. Si ya descontó, devuelve exactamente sus cantidades originales. Si no descontó, crea lápida RESTITUIDO y prohíbe descuento tardío. La decisión y stock se escriben en una sola transacción por idVenta.
8. Inventory publica MovimientoStockResuelto; sales confirma CANCELADA y libera operación. Si ya era CONFIRMADA/PAGADA, el consumidor no la cancela por un evento atrasado: registra inconsistencia y alerta.

El estado inicial solicitado PAGADO solo lo pueden enviar A/E: es declaración de cobro manual una vez confirmada, no autorización bancaria. CLIENTE siempre inicia estado=PENDIENTE.

Job cada30s: PENDIENTE con edad>120s compite con la confirmación mediante el mismo CAS, nunca “restituye todas las viejas” sin transición atómica. CANCELACION_PENDIENTE consulta `GET /internal/stock/movimientos/{idVenta}` o espera Kafka; republíca el mismo comando cuando corresponda. No compensa una CONFIRMADA por antigüedad.

La recuperación revisa también operacion_venta, no solo Venta.estadoOperacion. Una operación PAGO en PREPARANDO/ADMITIDA reintenta la misma admisión y pago local; si ya pagó, solo asegura el outbox de finalizar. No compensa inventario por un timeout de pago. ANULACION reanuda su CAS/restitución; rechazo de admisión termina la operación sin tocar dinero/stock. Una restricción local permite solo una operación no terminal por venta.

El registro de operación en reception no caduca solo por tiempo: un timeout no prueba que no hubo venta. Se libera por resultado terminal de sales, con comando durable. Si finalizar llega antes que registrar, deja lápida FINALIZADA y el registro tardío devuelve409.

## 3. Carrito

Un carrito por estadía; upsert por producto. Precio y stock al listar son informativos. No descuenta ni reserva; ningún fallo del carrito requiere compensación de stock. Para vender, backend toma items recibidos o seleccionados, valida y aplica flujo2.

La edición de carrito solo se permite con recepción ACTIVA. Una carrera de edición contra cierre puede dejar una línea residual, nunca permitir una venta: la admisión de venta es atómica en reception y el evento de salida limpia carrito idempotentemente. Listar carrito de estadía cerrada devuelve[].

## 4. Cierre seguro de estadía

Una operación de venta incluye cualquier alta, pago o anulación que pueda cambiar lo facturable. Sales toma admisión en reception también para pago/anulación; usa operationId único. Esto evita modificar la cuenta mientras se cierra.

1. A/E obtiene cotización informativa. Front muestra alojamiento, adelanto, penalidad, consumos pagados/pendientes y monto de hoy.
2. POST salida con closureId derivado de Idempotency-Key, idRecepcion,idHabitacion,costoPenalidad,totalPagado (=cobro de hoy).
3. Transacción reception lock recepción. Otro closureId sobre una recepción CERRANDO devuelve409 CLOSURE_IN_PROGRESS con referencia al cierre vigente; el mismo closureId reanuda. Si hay operaciones ABIERTAS, devuelve409 OPERATIONS_IN_PROGRESS y NO cambia estado. Si no, ACTIVA→CERRANDO y persiste cierre/importe declarado. Desde ese commit, nuevas operaciones de venta son409.
4. Feign `GET /internal/cierres/{idRecepcion}/resumen` obtiene ventas estables. Calcula saldo con tarifa/snapshot local. Si monto distinto: vuelve ACTIVA y devuelve409 AMOUNT_MISMATCH con cotización. Aún no se ha registrado pago.
5. Feign `POST /internal/cierres/confirmar` con closureId,idRecepcion,expectedPendingTotal. Sales, en transacción, verifica total y ausencia de ventas técnicas pendientes, marca ventas impagadas PAGADO, registra receipt único por closureId/recepción y publica snapshots. Nunca toca otras bases.
6. Reception guarda receipt e importe; CERRANDO→CERRADA, timestamp de salida, room_gate→LIMPIEZA_PENDIENTE con cleaningCycleId nuevo. Mismo commit produce SalidaRegistrada, limpieza.solicitada y notificación. Responde200.
7. Timeout tras paso3 o5:202, conserva CERRANDO/gate OCUPADA; GET recepción muestra estado. Job reintenta MISMO closureId y payload, consulta receipt en sales. Nunca vuelve ACTIVA si puede haberse confirmado pago.
8. Sales no disponible: cerrar falla de forma segura o queda202, sin reabrir la habitación. Recovery termina el mismo cierre; no hay segundo cobro.

El registro de cobro es administrativo. Reintentar la API jamás vuelve a solicitar efectivo ni ordena un cargo externo. El panel muestra “cierre pendiente, no volver a cobrar” en202.

## 5. Limpieza sin ventana de doble ingreso

El gate LIMPIEZA_PENDIENTE del paso6 ya rechaza check-in aunque RabbitMQ esté detenido y hotel siga mostrando LISTA.

Hotel consume limpieza.solicitada con cleaningCycleId, idRecepcion y roomVersion. Crea tarea, pone EN_LIMPIEZA. Duplicados retornan éxito sin reabrir una tarea terminada. Una tarea de ciclo anterior no reemplaza otra más nueva.

Personal completa exactamente ese cleaningCycleId: hotel→LISTA + evento HabitacionLista. Reception solo libera gate si estaba LIMPIEZA_PENDIENTE y coincide ciclo/habitación. Un evento viejo jamás libera un ciclo posterior. Desfase puede demorar admisión, no admitir una habitación sucia.

## 6. Mantenimiento y baja de habitación

Para evitar la carrera “leí LISTA y alguien puso MANTENIMIENTO”:

1. Hotel persiste operación administrativa PREPARANDO con blockId e idempotencia.
2. Feign reception crea bloqueo durable del room_gate con ese blockId; solo LIBRE→BLOQUEADA. Si OCUPADA o LIMPIEZA_PENDIENTE:409. La misma llave devuelve mismo bloqueo.
3. Hotel cambia físico a MANTENIMIENTO o estado=false. Si falla entre2 y3, el bloqueo se conserva; job reintenta la operación administrativa persistida. No libera por timeout.
4. Finalizar mantenimiento/reactivar habitación: hotel físico LISTA/activo y emite HabitacionHabilitada con blockId. Reception libera solo el bloqueo coincidente. Habitación ocupada no puede entrar a mantenimiento vía este flujo.

Baja de una sede solo después de dar de baja todas sus habitaciones; rechaza409 si quedan activas. Categoría/piso se dan de baja solo sin habitaciones activas. El número/idHotel de una habitación nunca cambia después de usada.

## 7. Entrega, duplicados y orden

Outbox se escribe junto con cada mutación. Poller envía una fila por destino y espera confirmación; si cae luego de publicar, puede duplicar. Inbox del consumidor y efecto van en una transacción antes del ack.

Kafka: clave y versión por agregado, payload snapshot completo para proyecciones. Eventos de limpieza/habilitación requieren correlación de ciclo/bloqueo aunque su versión sea antigua respecto de otro cambio de catálogo. No descartar un efecto de liberación válido solo porque llegó un snapshot posterior: inbox + estado/ciclo decide; la proyección sí filtra por versión.

Versiones independientes: hotel.version es de datos físicos; reception.roomVersion es de ocupación. No se comparan entre ellas. Reportes conservan ambas.

Para publicar por agregado, no adelantar una versión si hay otra anterior pendiente en ese destino. Procesar secuencial por clave. Ningún consumidor presupone orden Kafka↔Rabbit.

## 8. Errores no resueltos por magia

No existe disponibilidad infinita ni rollback de dos DB con @Transactional. Ante caída se conserva estado técnico visible y se reintenta; DLQ se inspecciona, no se borra. Datos inválidos permanentes requieren intervención de operador, siempre auditada.

Autorización de cliente se valida al inicio del check-in; una baja posterior no reescribe snapshots históricos. Cambio de permisos puede tardar el TTL del JWT, decisión explícita del MVP.
