# Design

## Context

Ver [`proposal.md`](proposal.md) para la motivación. El cambio se implementa en un monorepo sin código todavía: `services/reporting-service/` nace en este plan y aplica la convención ya mergeada de `back-plataforma-comunes` (JWT, envelope, correlationId, health y logs). Reporting es un read model puro: no consulta bases ajenas, no hace Feign y no muta estado de negocio. Las constraints que no se pueden relajar: entrega al-menos-una-vez con efectos idempotentes (inbox), orden por agregado con skip-if-older, reconstrucción por snapshot + stream (no se depende de los 7 días de Kafka), dinero `numeric(12,2)` con HALF_UP y un solo conteo por pago, y consistencia eventual declarada en el meta (nunca frescura absoluta). La autoridad de admisión sigue siendo `room_gate` de reception; las proyecciones de reporting solo informan.

## Goals / Non-Goals

**Goals:**

- Proyectar los 6 topics del grupo `reporting-proyeccion-v1` con efecto único y versión por agregado.
- Resolver `gate_projection` y `limpieza_completada_projection` en ambos órdenes entre `hotel.habitacion.v1` y `reception.recepcion.v1`, con correlación por ciclo y bloqueo.
- Arrancar con bootstrap determinista (manifiesto + watermarks + checkpoints de particiones vacías) y exponer `bootstrapComplete`.
- Entregar los 5 reportes con validaciones de rango, aislamiento por sede y meta de consistencia.
- Calcular la caja del día (`ingresosHoy`) sin doble conteo y los cobros con desglose por medio de pago.
- Degradar de forma explícita y observable (`REPORTING_NOT_READY`, `degraded`, `REPORTING_UNAVAILABLE`).

**Non-Goals:**

- Cualquier ruta interna, scope de máquina, Feign o publicación de eventos/comandos.
- Reconstrucción o truncado de proyecciones por API pública (herramienta offline).
- Exportación de archivos PDF/Excel (la hace el frontend con librerías existentes).
- Reglas de admisión, cobro o cierre (planes de reception/sales); reporting solo las proyecta.

## Decisions

1. **Esquema mínimo y read model puro.** La migración V1 de `reporting_db` se deriva del [DDL de referencia](../../../docs/especificacion/microservicios/reporting/base-de-datos.md#ddl-de-referencia) con las once tablas de proyección más `inbox`; sin `outbox` ni `api_idempotency`. Alternativa descartada: replicar las tablas técnicas de mutación por simetría con otros servicios; el contrato dice que reporting solo consulta y que su replay es operacional offline.

2. **Ingesta: un grupo, seis listeners, inbox y versión por agregado.** Cada topic tiene su listener dentro del grupo `reporting-proyeccion-v1`; el efecto y la fila de `inbox` se escriben en una transacción y el offset se confirma después del commit local. El upsert filtra por versión (`version`, `roomVersion`, `aggregateVersion` según el productor) salvo la liberación de gates, que además decide por estado y correlación de ciclo/bloqueo. Alternativa descartada: filtrar liberaciones solo por versión; el contrato prohíbe descartar un efecto de liberación válido porque llegó un snapshot posterior ([`04-flujos.md` §7](../../../docs/especificacion/04-flujos.md#7-entrega-duplicados-y-orden)).

3. **Gate unificado con reconciliación en ambos órdenes.** `gate_projection` se crea aunque falte el catálogo: hotel inicializa solo la parte física sin reemplazar un gate ya proyectado desde reception; los snapshots de reception se aplican por `roomVersion` y `ocupacionActual`; `limpieza_completada_projection` recuerda el ciclo completado para conciliar una SalidaRegistrada tardía sin dejar LIMPIEZA_PENDIENTE eterno; HabitacionLista libera solo su ciclo y HabitacionHabilitada solo su `blockId`. Alternativa descartada: alimentar el gate solo desde `hotel.habitacion.v1`; perdería los estados de limpieza y bloqueo que solo conoce reception.

4. **Bootstrap determinista con manifiesto por entorno.** El `manifest` es un fixture de dev/demo derivado del seed terminado: conteos esperados por dominio y watermarks topic/partición; los valores exactos se fijan al ejecutar y se anotan en el PR. El job crea checkpoint de todas las particiones suscritas —incluidas las vacías, con `last_offset=-1`— y marca LISTO en una transacción tras cargar los snapshots de los cinco dominios y alcanzar los watermarks. Alternativa descartada: esperar el primer evento de ventas/estadías; el contrato lo prohíbe y la demo arrancaría en 503 para siempre.

5. **Meta de consistencia y degradación derivados del checkpoint.** `updatedAt` es el mínimo checkpoint con timestamp de ingestión de los topics requeridos y puede ser `null` mientras todas las particiones estén vacías (contrato de [`reporting.yaml`](../../../docs/contracts/openapi/reporting.yaml)); `degraded` se persiste desde DLT/errores conocidos y desde el watchdog de conexión con `hotel.reporting.degraded-timeout-minutes` = 5, propiedad fijada en [`configuracion.md` §Por servicio](../../../docs/especificacion/infraestructura/configuracion.md#por-servicio) (env `REPORTING_DEGRADED_TIMEOUT_MINUTES`). Dentro del umbral: 200 con advertencia; fuera: 503 `REPORTING_UNAVAILABLE`.

6. **Un solo conteo de dinero.** Los importes son `numeric(12,2)` con HALF_UP en SQL y BigDecimal en Java. `ingresosHoy` se deriva del recibo del cierre (`total_alojamiento − adelanto`), nunca del `totalGeneral`; las ventas entran solo por `paid_at` y una vez; los cobros usan el snapshot del recibo sin re-sumar ventas. Alternativa descartada: sumar pagos por venta más el total del cierre; duplica consumos y viola los casos A y C.

7. **Reconstrucción offline (REP02).** El rebuild se prueba exportando el snapshot de las bases propietarias con watermark/version en ventana de mantenimiento, importándolo a una base `reporting_db` nueva y reproduciendo el stream posterior; no hay API pública para reconstruir ni truncar. Alternativa descartada: endpoint de rebuild; el contrato lo prohíbe y sería peligroso en operación.

8. **Errores y formatos del catálogo único.** Solo se usan códigos de [`errors.md`](../../../docs/contracts/errors.md): 400 `VALIDATION_ERROR` (rango, `idHotel`, medianoche de ocupación), 401 `UNAUTHENTICATED`, 403 `FORBIDDEN` (CLIENTE), 404 `RESOURCE_NOT_FOUND` (sede ajena), 503 `REPORTING_NOT_READY` y `REPORTING_UNAVAILABLE`. No se inventan códigos ni mensajes; el envelope y `meta` salen de la convención común y del OpenAPI.

9. **Pruebas con dependencias reales.** PostgreSQL y Kafka reales (Testcontainers o Compose), sin sustituir consumidores ni constraints por mocks; los fixtures de eventos validan contra los JSON Schemas de [`contracts/events/`](../../../docs/contracts/events/README.md) y los invariantes del [§Validación](../../../docs/especificacion/microservicios/reporting/base-de-datos.md#validación) se prueban con entregas duplicadas, versiones viejas y reordenamientos.

10. **Anulación de check-in sin recibo.** La anulación llega como `RecepcionActualizada` con CERRADA, archivada y `cierre=null`, y el CHECK de `recepcion_projection` exige importes en CERRADA. Decisión: proyectarla con `total_alojamiento`, `total_consumos`, `total_general` y `cobro_ahora` en 0.00 y `closure_id`, `fecha_cierre` y `medio_pago_cierre` en null; el CHECK queda intacto, la fila es distinguible por la ausencia de fecha de cierre y no entra en cobros ni en el cierre de la caja del día. Alternativas descartadas: guardar nulls relajando el CHECK (exigía tocar la spec en un PR aparte) y llenar el alojamiento devengado (fabricaba un desglose de recibo inexistente).

## Risks / Trade-offs

- [DLT con proyección parcial] → `degraded` visible al operador y aviso en la UI; no se declara paridad hasta reparar; el reenvío manual conserva `eventId` y key (runbook RB4/DLT).
- [Topics sin tráfico que simulan frescura] → los checkpoints se crean en el bootstrap y `updatedAt` no promete frescura absoluta; un topic sin tráfico no prueba atraso.
- [Kafka caído] → antes del umbral se responde con `degraded=true`; superados los 5 minutos, 503 `REPORTING_UNAVAILABLE` con `Retry-After`, sin habilitar datos incompletos como completos.
- [Reordenación entre hotel y reception] → correlación por ciclo/`blockId`, `limpieza_completada_projection` y pruebas de ambos órdenes; un LISTA genérico nunca libera.
- [Anulación de check-in en la proyección] → decidida en la decisión 10 (importes 0.00 sin recibo); el riesgo residual es leer esos 0.00 como un cobro, mitigado porque la fila no tiene fecha de cierre ni `closure_id` y las pruebas cubren su exclusión de cobros y de la caja del día.
- [Histórico LEGACY sin medio de pago] → `null` se conserva como NO_REGISTRADO; los reportes no lo reemplazan ni lo inventan.
- [Zona horaria] → fechas de negocio en America/Lima calculadas en servidor (medianoche para ocupación y día de caja para `ingresosHoy`); nunca horas inventadas.

## Migration Plan

`reporting_db` nace vacía con Flyway V1 y el manifiesto de dev/demo; no hay datos que migrar del monolito. En Compose, reporting arranca después de sales y responde 503 hasta que su bootstrap queda LISTO. El rollback en demo es recrear la base y repetir el bootstrap; la reconstrucción real es la operación offline de la decisión 7.
