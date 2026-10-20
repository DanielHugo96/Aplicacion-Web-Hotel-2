# Proposal

## Why

La fase F4 cierra el primer incremento con los reportes al día: sin `reporting-service` no hay dashboard, ventas, ocupación ni cobros, ni una fuente de caja confiable para el día. Reporting es un read model alimentado solo por Kafka con bootstrap, watermarks y consistencia eventual; un error de proyección o de cálculo duplica ingresos (adelanto, cierre y venta del mismo dinero) o muestra datos de una sede ajena. Por eso este plan se ejecuta en modo `openspec` ([norma §3](../../../docs/plans/README.md)), y su propuesta acompaña al plan [`docs/plans/back-reporting-proyecciones.md`](../../../docs/plans/back-reporting-proyecciones.md).

## What Changes

- Alta de `services/reporting-service/` con su base `reporting_db`: read model de Kafka, sin outbox ni API de idempotencia, con la convención JWT, envelope y health ya mergeada de `back-plataforma-comunes`.
- Migración V1 con las tablas de proyección del [DDL de referencia](../../../docs/especificacion/microservicios/reporting/base-de-datos.md#ddl-de-referencia): personas, sedes, habitaciones, gates, recepciones, stock, ventas y detalle, limpieza completada, checkpoints, bootstrap e inbox.
- Consumidor `reporting-proyeccion-v1` sobre los 6 topics asignados (`identity.persona.v1`, `hotel.sede.v1`, `hotel.habitacion.v1`, `reception.recepcion.v1`, `inventory.stock.v1` y `sales.venta.v1`) con inbox en la misma transacción del efecto, skip-if-older por versión de agregado, reintentos del contrato y DLT `<topic>.reporting-proyeccion-v1.dlt`.
- Proyecciones de catálogo y de ocupación: `gate_projection` se alimenta de eventos físicos y de recepción, reconcilia ciclos de limpieza completados y bloqueos por correlación, funciona en ambos órdenes entre topics y nunca convierte un snapshot genérico en permiso de liberación.
- Bootstrap con manifiesto y watermarks: `projection_bootstrap` de CARGANDO a LISTO, checkpoints para todas las particiones suscritas (incluidas las vacías) y `bootstrapComplete` derivado del estado.
- Reportes `GET /api/reportes/productos-bajo-stock`, `/ventas`, `/ocupacion`, `/cobros` y `/dashboard` según [`reporting.yaml`](../../../docs/contracts/openapi/reporting.yaml), con meta `{updatedAt, consistency, degraded, bootstrapComplete}`, paginación y `data[]` sin filas.
- Caja del día `ingresosHoy` con los casos numéricos A, B y C del [cálculo de ingresosHoy](../../../docs/especificacion/microservicios/reporting/api.md#cálculo-de-ingresoshoy-casos-numéricos), y cobros con `medioPagoAdelanto`/`medioPagoCierre` (null = NO_REGISTRADO) sin volver a sumar ventas.
- Degradación explícita: 503 `REPORTING_NOT_READY` sin bootstrap LISTO; 200 con `degraded=true` ante DLT/error parcial; 503 `REPORTING_UNAVAILABLE` si la proyección no es utilizable (> 5 min sin conexión activa).

Sin cambios de ruptura: el monorepo no tiene código todavía y las rutas ya están declaradas en `contracts/openapi/reporting.yaml`.

## Capabilities

### New Capabilities

- `reporting/proyecciones`: consumo y proyección idempotente de los 6 topics de reporting, proyección de ocupación entre topics (gates y limpieza), bootstrap con manifiesto y watermarks, reportes REST con consistencia eventual, caja del día `ingresosHoy` y degradación (`REPORTING_NOT_READY`/`REPORTING_UNAVAILABLE`).

### Modified Capabilities

Ninguna: `openspec/specs/` está vacío y no hay requisitos previos que cambien.

## Impact

- **Código**: `services/reporting-service/**` (nuevo); `contracts/openapi/reporting.yaml` solo se corrige si al ejecutar aparece una discrepancia con la spec.
- **Datos**: `reporting_db` nueva (esquema V1 y manifiesto de bootstrap de dev/demo); sin seeds de ventas, cobros ni estadísticas.
- **Dependencias**: Kafka (6 topics, grupo `reporting-proyeccion-v1`); PostgreSQL. Sin Feign, sin RabbitMQ, sin rutas internas y sin scopes de máquina.
- **Casos**: SEC03 (parte de reporting) y REP01–REP03 de [`06-migracion-y-pruebas.md`](../../../docs/especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación-funcional), con su evidencia en el PR de ejecución.
- **Otros planes**: `front-admin-web-reportes` consume estos endpoints por el Gateway; `qa-runbooks` usa los síntomas de bootstrap/DLT de RB4; los productores de los 6 topics son planes ya cerrados o de F4 y no cambian aquí.
