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

Transiciones técnicas permitidas: PENDIENTE→CONFIRMADA/RECHAZADA/CANCELACION_PENDIENTE; CONFIRMADA impagada→CANCELACION_PENDIENTE; CANCELACION_PENDIENTE→CANCELADA. No existe transición de CANCELADA o RECHAZADA a CONFIRMADA.
Estado de pago separado: PENDIENTE | PAGADO. No se permite cancelar una venta PAGADA.

1. Sales crea venta técnica PENDIENTE y operationId UUID con clave idempotente; las líneas contienen idProducto/cantidad, sin precio autorizado por el cliente.
2. Feign a reception registra operación en esa estadía. Su transacción bloquea la recepción y solo admite si ACTIVA; valida propietario/sede. Operación única por UUID y venta. Un timeout aquí deja la venta RECHAZADA y emite finalizar, sin invocar inventory. El CAS terminal impide que una respuesta de admisión tardía continúe al débito. Si algún worker ya había persistido autorización para iniciar débito, se usa CANCELACION_PENDIENTE y restitución, como en el paso6.
3. Sales persiste, mediante CAS PENDIENTE y operación PREPARANDO→DEBITO_SOLICITADO, que obtuvo admisión y va a iniciar débito. No hace llamada si la transición perdió frente al reconciliador. Feign a inventory `POST /internal/stock/descontar` con idVenta,idHotel,items. Inventory crea/lock registro único del movimiento, compara hash y bloquea filas de stock ordenadas por idProducto. En una transacción descuenta todos los ítems o ninguno, calcula precios vigentes y persiste resultado inmutable.
4. Sales aplica CAS `WHERE estado_operacion='PENDIENTE'`: si gana, guarda detalles/precios devueltos, total y CONFIRMADA, emite VentaSnapshot y comando recepcion.operacion-finalizar. Responde201. Nunca vuelve a descontar por generar una respuesta.
5. Si inventory rechaza por insuficiencia/inactividad: RECHAZADA sin descuento, libera operación y409. Una misma venta rechazada no se reintenta con nuevo cuerpo; el usuario inicia nueva venta con nueva clave.
6. Si resultado remoto es incierto: CAS PENDIENTE→CANCELACION_PENDIENTE + outbox stock.restituir en la misma transacción; responde202. Si llega luego un éxito HTTP, no puede confirmar porque perdió CAS.
7. Inventory procesa restitución idempotente. Si ya descontó, devuelve exactamente sus cantidades originales. Si no descontó, crea lápida RESTITUIDO y prohíbe descuento tardío. La decisión y stock se escriben en una sola transacción por idVenta.
8. Inventory publica MovimientoStockResuelto; sales confirma CANCELADA y libera operación. Si ya era CONFIRMADA/PAGADA, el consumidor no la cancela por un evento atrasado: registra inconsistencia y alerta.

El estado inicial solicitado PAGADO solo lo pueden enviar A/E: es declaración de cobro manual una vez confirmada, no autorización bancaria. CLIENTE siempre inicia estado=PENDIENTE.

Job cada30s: venta PENDIENTE con edad>120s y operación PREPARANDO se rechaza mediante CAS y emite finalizar, sin stock; con DEBITO_SOLICITADO pasa por CAS a CANCELACION_PENDIENTE y emite restitución. Compite con la confirmación bajo el mismo lock/CAS, nunca “restituye todas las viejas” sin transición atómica. CANCELACION_PENDIENTE consulta `GET /internal/stock/movimientos/{idVenta}` o espera Kafka; republíca el mismo comando cuando corresponda. No compensa una CONFIRMADA por antigüedad.

La recuperación revisa también operacion_venta, no solo Venta.estadoOperacion. Una operación PAGO en PREPARANDO/ADMITIDA reintenta la misma admisión y pago local; si ya pagó, solo asegura el outbox de finalizar. No compensa inventario por un timeout de pago. ANULACION reanuda su CAS/restitución; rechazo de admisión termina la operación sin tocar dinero/stock. Una restricción local permite solo una operación no terminal por venta.

El registro de operación en reception no caduca solo por tiempo: un timeout no prueba que no hubo venta. Se libera por resultado terminal de sales, con comando durable. Si finalizar llega antes que registrar, deja lápida FINALIZADA con operationId,idRecepcion,idVenta,tipo y resultado; el registro tardío devuelve409. Finalizar y admitir usan el mismo orden de locks (recepción, después operación), verifican que la tupla coincida y nunca convierten FINALIZADA en ABIERTA. El rechazo de una admisión válida pero tardía también queda terminal; no puede aceptarse más tarde con el mismo operationId.

## 3. Carrito

Un carrito por estadía; upsert por producto. Precio y stock al listar son informativos. No descuenta ni reserva; ningún fallo del carrito requiere compensación de stock. Para vender, backend toma items recibidos o seleccionados, valida y aplica flujo2.

La edición de carrito solo se permite con recepción ACTIVA. Una carrera de edición contra cierre puede dejar una línea residual, nunca permitir una venta: la admisión de venta es atómica en reception y el evento de salida limpia carrito idempotentemente. Listar carrito de estadía cerrada devuelve[].

## 4. Cierre seguro de estadía

Altas, pagos y anulaciones de ventas requieren una operación admitida en reception. Una estadía con operaciones ABIERTAS no inicia cierre.

Estados persistidos de cierre: VALIDANDO → CONFIRMANDO → COMPLETADO, o VALIDANDO → RECHAZADO. Solo RECHAZADO permite devolver la estadía a ACTIVA. CONFIRMANDO significa que el pago remoto puede haberse aplicado.

1. A/E obtiene cotización informativa; presenta idRecepcion,idHabitacion,costoPenalidad,totalPagado (=cobro de hoy) e Idempotency-Key. closureId se fija a esa clave.
2. Transacción: lock recepción. Otra clave sobre CERRANDO devuelve409 CLOSURE_IN_PROGRESS; la misma reanuda. Si hay operaciones ABIERTAS,409 sin cambiar la estadía. De lo contrario ACTIVA→CERRANDO e inserta cierre VALIDANDO con solicitud inmutable.
3. Feign GET /internal/cierres/{idRecepcion}/resumen?idHotel=... obtiene consumos estables, incluso si no existe ninguna venta: devuelve totales0.
4. Calcular y validar monto. Si discrepa, en transacción VALIDANDO→RECHAZADO y CERRANDO→ACTIVA, conservar registro y devolver409 AMOUNT_MISMATCH. Una nueva intención requiere nueva clave. Ningún pago remoto se invocó.
5. Si coincide, persistir cálculo completo y expectedPendingTotal; CAS VALIDANDO→CONFIRMANDO y commit ANTES de llamar a Sales. Este es el punto que impide recalcular un cobro ya posiblemente aplicado.
6. Feign POST /internal/cierres/confirmar con {closureId,idRecepcion,idHotel,expectedPendingTotal}, siempre idéntico. Sales busca primero su recibo por clave/recepción: si existe y coincide devuelve ese recibo, sin volver a comparar contra ventas ya pagadas. Si no existe, serializa por idRecepcion, valida total y marca las ventas impagadas como PAGADO junto con recibo y outbox.
7. Reception valida recibo contra cierre/idHotel/importe congelado. En una transacción: CONFIRMANDO→COMPLETADO, CERRANDO→CERRADA, guarda fecha/desglose y room_gate→LIMPIEZA_PENDIENTE con cleaningCycleId. Produce SalidaRegistrada, limpieza.solicitada y notificación. Devuelve200.
8. Timeout después de aceptar cierre:202 con Location=GET recepción, Retry-After y fase. Recuperación cada30s: VALIDANDO repite lectura/cálculo; CONFIRMANDO repite únicamente la confirmación con payload congelado; COMPLETADO devuelve resultado guardado; RECHAZADO devuelve su409 original.

No se borra un cierre rechazado. Índice único parcial permite varios intentos rechazados y un único intento VALIDANDO/CONFIRMANDO/COMPLETADO por estadía. Cada fase avanza mediante CAS bajo lock; dos workers no pueden simultáneamente rechazar e iniciar confirmación. No mantener la transacción SQL durante Feign.

Errores permanentes en CONFIRMANDO conservan estadía CERRANDO y lastErrorCode para revisión; no abren otra cuenta ni revierten a ACTIVA. Un retry posterior a confirmar en Sales no vuelve al cálculo de saldo pendiente, que ahora sería0.

El registro de cobro es administrativo. Reintentar la API no vuelve a solicitar efectivo ni ordena un cargo externo. En202, el panel muestra “cierre pendiente, no volver a cobrar”.

## 5. Limpieza sin ventana de doble ingreso

El gate LIMPIEZA_PENDIENTE del paso7 ya rechaza check-in aunque RabbitMQ esté detenido y hotel siga mostrando LISTA.

Hotel consume limpieza.solicitada con cleaningCycleId, idRecepcion y roomVersion. Crea tarea, pone EN_LIMPIEZA. Duplicados retornan éxito sin reabrir una tarea terminada. Hotel compara roomVersion de la solicitud con last_cleaning_room_version guardado: un ciclo antiguo o ya completado no reemplaza al actual. El mismo ciclo con otra habitación/recepción es error permanente y se envía a DLQ.

Personal completa exactamente ese cleaningCycleId y su habitación/sede; If-Match corresponde a version de la tarea obtenida en listado. La operación actualiza tarea y habitación bajo lock, y devuelve ETag/version de la tarea terminada. En ese commit hotel pasa a LISTA y emite HabitacionLista. Reception solo libera gate si estaba LIMPIEZA_PENDIENTE y coincide ciclo/habitación. Un evento viejo jamás libera un ciclo posterior. Desfase puede demorar admisión, no admitir una habitación sucia.

## 6. Mantenimiento y baja de habitación

Para evitar la carrera “leí LISTA y alguien puso MANTENIMIENTO”:

1. Hotel persiste operación administrativa PREPARANDO con blockId e idempotencia.
2. Feign reception crea bloqueo durable del room_gate con ese blockId; solo LIBRE→BLOQUEADA. Si OCUPADA o LIMPIEZA_PENDIENTE:409. La decisión se persiste por blockId con hash de request: ACTIVO o RECHAZADO. Una llave rechazada sigue rechazándose aunque la habitación quede libre después; otra intención necesita otra llave.
3. Hotel cambia físico a MANTENIMIENTO o estado=false. Si falla entre2 y3, el bloqueo se conserva; job reintenta la operación administrativa persistida. El cambio local usa CAS PREPARANDO→APLICADA; una operación LIBERADA/RECHAZADA nunca vuelve a aplicar mantenimiento/baja. No libera por timeout.
4. Finalizar mantenimiento/reactivar habitación: hotel físico LISTA/activo y emite HabitacionHabilitada con blockId. Reception libera solo el bloqueo coincidente. Habitación ocupada no puede entrar a mantenimiento vía este flujo.

Baja de una sede solo después de dar de baja todas sus habitaciones; rechaza409 si quedan activas. Categoría/piso se dan de baja solo sin habitaciones activas. El número/idHotel de una habitación es inmutable desde su creación.

## 7. Entrega, duplicados y orden

Outbox se escribe junto con cada mutación. Poller envía una fila por destino y espera confirmación; si cae luego de publicar, puede duplicar. Inbox del consumidor y efecto van en una transacción antes del ack.

Kafka: clave y versión por agregado, payload snapshot completo para proyecciones. Eventos de limpieza/habilitación requieren correlación de ciclo/bloqueo aunque su versión sea antigua respecto de otro cambio de catálogo. No descartar un efecto de liberación válido solo porque llegó un snapshot posterior: inbox + estado/ciclo decide; la proyección sí filtra por versión.

Versiones independientes: hotel.version es de datos físicos; reception.roomVersion es de ocupación. No se comparan entre ellas. Reportes conservan ambas.

Para publicar por agregado, no adelantar una versión si hay otra anterior pendiente en ese destino. Procesar secuencial por clave. Ningún consumidor presupone orden Kafka↔Rabbit.

## 8. Errores no resueltos por magia

No existe disponibilidad infinita ni rollback de dos DB con @Transactional. Ante caída se conserva estado técnico visible y se reintenta; DLQ se inspecciona, no se borra. Datos inválidos permanentes requieren intervención de operador, siempre auditada.

Autorización de cliente se valida al inicio del check-in; una baja posterior no reescribe snapshots históricos. Cambio de permisos puede tardar el TTL del JWT, decisión explícita del MVP.
