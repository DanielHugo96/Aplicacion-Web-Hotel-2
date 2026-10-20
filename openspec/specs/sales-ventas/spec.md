# sales-ventas Specification

## Purpose
Cubre el carrito, el catálogo privado, el ciclo de venta con pago y anulación, las operaciones con recuperación y compensación, y el cierre de consumos de sales-service para estadías activas.

## Requirements

### Requirement: Carrito por estadía sin reserva de stock

El sistema SHALL mantener un carrito por estadía con upsert por producto, validar recepción ACTIVA y propiedad, y exponer el catálogo privado de la sede sin descontar ni reservar stock.

#### Scenario: Agregar suma a la línea existente

- **WHEN** se agrega un producto con cantidad válida a una estadía ACTIVA propia
- **THEN** el sistema suma la cantidad a la línea actual con upsert e incrementa su versión

#### Scenario: Carrito no descuenta stock

- **WHEN** se agrega o lista el carrito con inventory disponible o caído
- **THEN** el stock de inventory queda intacto y el precio mostrado es informativo con fallo 503 sin precio inventado

#### Scenario: Estadía cerrada solo lectura

- **WHEN** se lista el carrito de una estadía CERRADA tras validar propiedad
- **THEN** el sistema devuelve una lista vacía sin permitir edición ni venta desde esa línea residual

### Requirement: Alta de venta con admisión y débito idempotente

El sistema SHALL crear la venta técnica PENDIENTE con operación VENTA, admitirla en reception solo si la estadía está ACTIVA, persistir la admisión con CAS antes del débito, y confirmar con los precios autoritativos de inventory o rechazar sin descuento parcial.

#### Scenario: Alta confirmada descuenta todo o nada

- **WHEN** la admisión es aceptada y el débito descuenta todos los ítems
- **THEN** el sistema guarda detalles con snapshot de precios, marca CONFIRMADA, emite snapshot y finaliza la operación con 201

#### Scenario: Reintento con misma clave no duplica

- **WHEN** se repite el alta con la misma clave de idempotencia y el mismo cuerpo
- **THEN** el sistema devuelve la misma venta con el mismo identificador y un solo movimiento de stock

#### Scenario: Stock insuficiente rechaza sin descuento

- **WHEN** algún ítem no tiene stock o el producto está inactivo en la sede
- **THEN** el sistema marca RECHAZADA sin descontar, libera la operación y responde 409

### Requirement: Pago manual y anulación impagada

El sistema SHALL registrar el pago solo en ventas CONFIRMADAS con operación PAGO admitida y medio de pago obligatorio, y anular solo impagadas con comando durable de restitución sin borrar historial.

#### Scenario: Pago registra medio y fecha sin tocar líneas

- **WHEN** se paga una venta CONFIRMADA con versión vigente y medio válido
- **THEN** el sistema marca PAGADO con fecha de pago y medio, emite snapshot y finaliza la operación de pago

#### Scenario: Anulación impagada emite restitución

- **WHEN** se anula una venta PENDIENTE o CONFIRMADA impagada con motivo válido
- **THEN** el sistema pasa a CANCELACION_PENDIENTE con comando durable de restitución y responde 202 con polling hasta CANCELADA

#### Scenario: Venta pagada no se anula

- **WHEN** se intenta anular una venta en estado PAGADO
- **THEN** el sistema responde 409 y conserva líneas, recibo y fecha de pago intactos

### Requirement: Operaciones observables con recuperación cada 30 s

El sistema SHALL exponer cada operación VENTA, PAGO y ANULACION para polling, mantener una sola operación no terminal por venta, y recuperar PENDIENTE con más de 120 s y CANCELACION_PENDIENTE con el mismo CAS que la respuesta HTTP.

#### Scenario: Operación en curso responde 202 con polling

- **WHEN** el resultado remoto es incierto o la operación sigue admitida
- **THEN** el sistema responde 202 con ubicación de la venta y reintento, y la respuesta terminal queda persistida para reintentos

#### Scenario: Recuperador compensa sin confirmar tras compensar

- **WHEN** el trabajo cada 30 s encuentra PENDIENTE con débito solicitado y edad mayor a 120 s
- **THEN** el sistema la pasa a CANCELACION_PENDIENTE con restitución y nunca la confirma después por antigüedad

### Requirement: Emisión de snapshots y comandos por outbox

El sistema SHALL emitir `sales.venta.v1` con clave de venta y versión ante cada alta confirmada, pago desde cierre o cancelación, y los comandos `stock.restituir` y `recepcion.operacion-finalizar` con clave estable tras persistir el estado terminal.

#### Scenario: Snapshot válido con clave de venta

- **WHEN** una venta se confirma, se paga o se cancela
- **THEN** el sistema encola un snapshot con clave igual al identificador de venta y versión de venta válido contra su esquema

#### Scenario: Finalizar incluye tipo y resultado compatibles

- **WHEN** una operación VENTA, PAGO o ANULACION alcanza su estado terminal
- **THEN** el sistema encola finalizar con tipo y resultado compatibles y con la tupla de identificadores verificada

### Requirement: Compensación idempotente del grupo asignado

El consumidor del grupo `sales-compensacion-v1` SHALL procesar `inventory.movimiento.v1` solo para finalizar CANCELACION_PENDIENTE ante RESTITUIDO y SHALL limpiar el carrito ante `reception.recepcion.v1` de salida, con inbox y sin confirmar por DESCONTADO tardío.

#### Scenario: Restituido finaliza la cancelación

- **WHEN** llega RESTITUIDO para una venta en CANCELACION_PENDIENTE
- **THEN** el sistema la marca CANCELADA, emite snapshot y finaliza la operación de anulación

#### Scenario: Descontado tardío no confirma

- **WHEN** llega DESCONTADO para una venta ya compensada o confirmada
- **THEN** el sistema no cambia su estado terminal y registra la inconsistencia con alerta

#### Scenario: Salida limpia el carrito una sola vez

- **WHEN** llega la salida registrada de una estadía con líneas residuales y se reentrega duplicada
- **THEN** el sistema deja el carrito vacío una sola vez por inbox sin marcar ventas como pagadas

### Requirement: Cierre de consumos con recibo único

El sistema SHALL exponer el resumen de consumos CONFIRMADOS por estadía y confirmar el cierre con recibo único por cierre, buscando el recibo antes de recalcular y serializando por recepción.

#### Scenario: Resumen excluye no admitidas y detecta abierta

- **WHEN** reception pide el resumen de una estadía con ventas técnicas sin admisión o con operación admitida no terminal
- **THEN** el sistema excluye las no admitidas del total y responde 409 ante la operación abierta

#### Scenario: Confirmación idempotente por cierre

- **WHEN** se confirma con el mismo cierre e idéntico cuerpo tras un timeout
- **THEN** el sistema devuelve el mismo recibo sin recalcular ni pagar dos veces, y otro cierre para la misma recepción responde 409
