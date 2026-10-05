# Reporting — contrato API

[Comunes](../../03-contrato-comun.md). Sin endpoints públicos ni C. A puede todas las sedes; E solo asignadas. Todas son consultas HTTP; Kafka actualiza sus datos, no transporta el request del frontend.

## REST legacy

| Método/ruta | Query | data 200 |
|---|---|---|
| GET /api/reportes/productos-bajo-stock | idHotel,limite:int>=0 default5,L | {idProducto,nombreProducto,cantidad,precio,estado}[] |
| GET /api/reportes/ventas | idHotel,inicio,fin,L | {idProducto,nombreProducto,cantidadTotal,totalIngresado}[] |
| GET /api/reportes/ocupacion | idHotel,inicio,fin,L | {idHabitacion,numeroHabitacion,descripcionCategoria,vecesAlquilada}[] |
| GET /api/reportes/cobros | idHotel,inicio,fin,L | {idRecepcion,numeroHabitacion,nombreCliente,totalAlojamiento,totalConsumos,totalGeneral,fechaCierre}[] |
| GET /api/reportes/dashboard | idHotel? | {habitacionesOcupadas,habitacionesDisponibles,productosBajoStock,ingresosHoy,moneda:"PEN"} |

inicio/fin ISO-8601 con offset, inicio<fin, intervalo semiabierto [inicio,fin), máximo366 días. Ocupación acepta únicamente inicio/fin en medianoche de America/Lima y compara fechas locales de check-in, para no inventar horas que el monolito no guarda. Otros reportes usan timestamps exactos.

idHotel requerido en los primeros4; en dashboard opcional solo A para consolidado; E debe escoger sede asignada. Filtro omitido no convierte a E en lector global.

meta adicional `{updatedAt,consistency:"EVENTUAL",degraded,bootstrapComplete}`; updatedAt=min(checkpoints de topics requeridos con timestamp de ingestión), no “hora exacta del dato origen”. Un topic sin tráfico reciente no prueba atraso; degraded refleja errores/DLT/desconexión conocidos. Nunca prometer frescura absoluta.

Sin bootstrap completo:503 REPORTING_NOT_READY. Con DLT/error:200 con degraded=true y advertencia visible, o503 si proyección no utilizable; umbral5min sin conexión activa declara no utilizable. Sin filas, data[]; no404.

## Ejemplo

```json
{"success":true,"message":"OK","data":{"habitacionesOcupadas":0,"habitacionesDisponibles":6,"productosBajoStock":3,"ingresosHoy":0.00,"moneda":"PEN"},"meta":{"consistency":"EVENTUAL","bootstrapComplete":true,"degraded":false,"updatedAt":"2026-10-04T15:00:00Z"}}
```

Antes de emitir el ejemplo, deben estar proyectados todos los seeds: los3 kits con cantidad5 alcanzan umbral5. No hardcodear métricas.

## Feign, Kafka y Rabbit

- Feign: ninguno en requests de reporte. No fan-out a todos los servicios.
- Kafka consume identity.persona.v1, hotel.sede.v1, hotel.habitacion.v1, reception.recepcion.v1, inventory.stock.v1 y sales.venta.v1. Payloads definidos en sus productores; inbox y source_version por agregado.
- No consume inventory.movimiento.v1 (para recuperación de ventas).
- No produce eventos ni usa RabbitMQ.
- No API pública para reconstruir/truncar proyecciones. Herramienta de operación offline, con backup y DB destino nueva.

## Alcance

El frontend puede exportar los resultados autorizados a PDF/Excel con librerías existentes; no añade endpoints de archivo ni rutas ficticias al backend. Los cálculos SQL preservan independencia de BD y evitan doble conteo.

Pruebas: cliente403, empleado sede ajena404, límites de fecha400, ventas PENDIENTE no ingreso, mismo pago antes/después de checkout se cuenta una sola vez, replay completo produce iguales totales.
