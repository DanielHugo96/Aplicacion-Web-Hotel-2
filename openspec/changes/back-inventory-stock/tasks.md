# Tasks

## 1. Base del servicio

- [ ] 1.1 Dar de alta `services/inventory-service/` con las versiones fijadas, registro en Eureka y convenciones de `back-plataforma-*`; verificar con build del módulo y `GET /actuator/health/liveness` y `GET /actuator/health/readiness` en 200 con PostgreSQL arriba
- [ ] 1.2 Configurar seguridad de usuario (JWT RS256, A global y E por sede, deny-by-default) y de máquina (scopes `stock:read` y `stock:debit`); verificar con 401 sin token, 403 de token humano en `/internal/**` y 200 con token máquina y scope correcto

## 2. Persistencia y seeds

- [ ] 2.1 Crear la migración V1 de `inventory_db` con `producto`, `producto_sede`, `movimiento_venta`, `movimiento_detalle` y `ajuste_stock` según el DDL de referencia; verificar aplicando sobre base vacía y provocando fallos de check (precio, cantidad, delta) y de unicidad (sku, id_venta, par venta-producto)
- [ ] 2.2 Sembrar 3 productos en 3 sedes con precios, cantidades y umbral de la spec y publicar 9 `ProductoStockSnapshot` de bootstrap idempotentes; verificar con consultas a `inventory_db` y reejecución sin duplicados ni reposición de ventas

## 3. Catálogo REST e interno

- [ ] 3.1 Implementar idempotencia HTTP (`Idempotency-Key`, 409 `IDEMPOTENCY_CONFLICT` ante distinto cuerpo) y versión (`If-Match`, 428 sin header y 412 ante versión distinta, ETag en lecturas); verificar repitiendo un POST con la misma clave y un PUT sin `If-Match` y con versión vieja
- [ ] 3.2 Implementar el CRUD REST de productos (rutas LEGACY más `POST /api/producto/{id}/sedes`) con baja lógica por sede que conserva cantidad y movimientos; verificar con 201/200, `DUPLICATE_RESOURCE` ante sku repetido y `PRODUCT_INACTIVE` al vender desde sede dada de baja
- [ ] 3.3 Implementar `POST /api/producto/{id}/ajustes` con lock, motivo y registro en `ajuste_stock`, y `GET /api/producto/{id}/movimientos` con unión VENTA/AJUSTE, filtros e intervalo; verificar con 201 de ajuste, rechazo sin saldo negativo y listado paginado
- [ ] 3.4 Implementar el Feign entrante (`GET /internal/productos`, `GET /internal/productos/{id}`, `POST /internal/stock/descontar`, `GET /internal/stock/movimientos/{idVenta}`) y el saliente (`GET /internal/hoteles/{id}` con scope `hoteles:read`); verificar con token máquina, 404 sin registro de movimiento y validación de sede contra hotel

## 4. Descuento y restitución

- [ ] 4.1 Implementar `fn_stock_descontar` (lock de movimiento, locks de stock en orden de `idProducto`, todo o nada, hash, precios autoritativos, 400 ante ítems duplicados); verificar con débito válido 200 y con venta multítem con una línea insuficiente que deja todo intacto
- [ ] 4.2 Implementar `sp_stock_restituir` (devolución única, lápida `RESTITUIDO` previa al débito, resultado conservado, 409 `DEBIT_CANCELLED` ante débito tardío, mismatch de sede a DLQ); verificar con doble restitución sin duplicar y restitución previa que bloquea el débito

## 5. Mensajería

- [ ] 5.1 Emitir `inventory.stock.v1` (key `idHotel:idProducto`, versión de `producto_sede`, estado efectivo) e `inventory.movimiento.v1` (key `idVenta`, versión del movimiento) por outbox en la misma transacción; verificar con una fila de outbox por mutación y payloads válidos contra sus JSON Schemas
- [ ] 5.2 Consumir `stock.restituir` con inbox, efecto y evento en la misma transacción, reintentos con TTL y DLQ final; verificar con comando duplicado aplicado una vez y mensaje inválido permanente en `stock.restituir.dlq`

## 6. Concurrencia, casos y cierre

- [ ] 6.1 Verificar serialización bajo concurrencia (dos débitos de 15 sobre stock 20, baja y cambio de precio concurrentes con débito); verificar que solo un débito gana, el stock final es 5 y la respuesta conserva el precio usado
- [ ] 6.2 Ejecutar los casos `SEC05`, `CRUD01`, `STK01`, `STK02` y `STK04` con PostgreSQL y brokers reales y guardar respuestas y consultas como evidencia; verificar cada resultado exigido en la matriz
- [ ] 6.3 Correr los comandos canónicos de la sección «Verificación» del `README.md` raíz y `python scripts/validar-planes.py`; verificar build y pruebas en verde y 0 errores del validador
