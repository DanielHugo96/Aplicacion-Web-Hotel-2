# Tasks

## 1. Base del servicio y seguridad

- [ ] 1.1 Dar de alta `services/sales-service/` con el stack fijado y las convenciones de plataforma y verificar que compila, se registra en Eureka y liveness y readiness responden 200 con la base arriba.
- [ ] 1.2 Aplicar resource server JWT con deny-by-default y autorización por rol y sede más token máquina con scopes hacia reception e inventory y verificar 401 sin token, 403 con rol o sede insuficientes y 403 con token humano en rutas internas.

## 2. Persistencia e idempotencia

- [ ] 2.1 Crear las migraciones Flyway de `sales_db` con carrito, venta, detalle_venta, operacion_venta y cierre_consumos más índices y clave diferida y verificar que aplican en vacío y que los checks e índices rechazan filas inválidas.
- [ ] 2.2 Aplicar idempotencia por actor, método, ruta y clave con hash canónico y versión con ETag e If-Match y verificar misma clave mismo cuerpo devuelve lo mismo, distinta responde 409, sin If-Match responde 428 y versión vieja responde 412.

## 3. Carrito y catálogo privado

- [ ] 3.1 Implementar `POST /api/carrito/agregar`, `GET /api/carrito/listar/{idRecepcion}` y `DELETE /api/carrito/eliminar` más `GET /api/venta/catalogo` con Feign a inventory y verificar upsert con versión, 204 idempotente, lista vacía en CERRADA y 503 sin precio inventado con inventory caído.
- [ ] 3.2 Implementar `POST /api/venta` con JSON cerrado, hasta 50 líneas, C solo PENDIENTE y A/E con PAGADO y medio obligatorio y verificar 400 ante campo calculado, 403 ante C con PAGADO y 201 o 202 con Location y Retry-After 2.

## 4. Venta, pago y anulación

- [ ] 4.1 Implementar admisión en reception y débito en inventory con CAS antes del HTTP y verificar que sin CAS ganador no hay llamada, el timeout de admisión deja RECHAZADA con finalizar y el débito debita todo o nada con precios autoritativos.
- [ ] 4.2 Implementar confirmación y rechazo con CAS, snapshot y finalizar y verificar 201 CONFIRMADA, 409 ante insuficiencia o inactividad sin descuento y 202 ante resultado incierto con CANCELACION_PENDIENTE y restitución.
- [ ] 4.3 Implementar `PUT /api/venta/{id}` de pago con operación PAGO admitida y `DELETE /api/venta/{id}` de anulación con motivo y comando durable y verificar pago idempotente 200 o 202 sin tocar líneas, anulación 202 hasta CANCELADA, 409 ante pagada y 409 ante LEGACY sin movimiento.
- [ ] 4.4 Implementar `GET /api/venta/operaciones/{operationId}`, `GET /api/venta`, `GET /api/venta/buscar/{id}` y `GET /api/venta/recepcion/{idRecepcion}` y verificar polling sin payload interno, listados paginados y 404 ante sede o propietario ajenos.

## 5. Mensajería y compensación

- [ ] 5.1 Producir por outbox `sales.venta.v1` con clave de venta y versión y los comandos `stock.restituir` y `recepcion.operacion-finalizar` con clave estable y verificar una fila por destino, payloads válidos contra sus esquemas y combinaciones tipo y resultado compatibles.
- [ ] 5.2 Implementar el consumidor `sales-compensacion-v1` de `inventory.movimiento.v1` y `reception.recepcion.v1` con inbox y verificar CANCELADA solo ante RESTITUIDO, DESCONTADO tardío sin efecto con alerta, limpieza de carrito idempotente ante salida y DLT ante contradicción.

## 6. Recuperación y cierres

- [ ] 6.1 Implementar el recuperador cada 30 s sobre venta y operacion_venta con CAS y verificar PENDIENTE mayor a 120 s en PREPARANDO a RECHAZADA sin stock, en DEBITO_SOLICITADO a CANCELACION_PENDIENTE con restitución, y reintento de PAGO y ANULACION sin compensar inventario por pago.
- [ ] 6.2 Implementar `GET /internal/cierres/{idRecepcion}/resumen` y `POST /internal/cierres/confirmar` con bloqueo por recepción y recibo único y verificar totales CONFIRMADOS, 409 ante operación abierta, totales 0 sin ventas, mismo recibo ante mismo cierre y 409 ante otro cierre o total distinto.

## 7. Validación y evidencia

- [ ] 7.1 Mantener propuesta, specs delta, diseño y tareas alineados con el plan y verificar `openspec validate back-sales-ventas --strict` sin errores y sin divergencia entre pasos y tareas.
- [ ] 7.2 Ejecutar la evidencia de `SEC04`, `STK03`, `STK04`, `STK05`, `STK06`, `STK07`, `PAY01` y `PAY02` con brokers y base reales y verificar cada resultado exigido con respuestas y consultas a `sales_db` adjuntas al PR.
- [ ] 7.3 Correr `python scripts/validar-planes.py` desde la raíz y los comandos canónicos de build y pruebas y verificar 0 errores del validador y build y pruebas en verde con salidas adjuntas.
