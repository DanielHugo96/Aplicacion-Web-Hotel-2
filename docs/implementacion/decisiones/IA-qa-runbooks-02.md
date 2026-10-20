# IA-qa-runbooks-02 — Eventos de recepción derivados a la DLT de hotel

- **Plan:** `qa-runbooks`
- **Paso:** 5 (RB1-Notification) y 15 (revalidación)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

Durante el simulacro RB1-Notification, los seis eventos publicados por reception
(`CheckInRegistrado` y `SalidaRegistrada` de las dos estadías de prueba y de F8) fueron
derivados por el consumidor `hotel-ocupacion-v1` a
`reception.recepcion.v1.hotel-ocupacion-v1.dlt`. `hotel_db.ocupacion_projection` quedó vacía.
El consumidor captura la excepción y publica en DLT sin registrar el motivo en el log
estructurado, por lo que la causa raíz no es visible desde el runbook.

Además, el evento inválido de RB1-Kafka también fue consumido por `reporting-proyeccion-v1` y
dejó `projection_checkpoint.degraded=true` para `hotel.habitacion.v1`, que es exactamente lo que
la tabla de alertas describe («Reporting marca proyección degradada ante DLT»).

## Qué se hizo

- Se conservaron los seis mensajes como evidencia en
  `tests/evidence/qa-runbooks/rb1-notificacion/12-hallazgo-dlt-hotel-ocupacion.json` y se
  descartó el topic DLT con el registro del descarte.
- No se tocó `services/hotel-service/**` (fuera de la zona de `qa`). El hallazgo queda para el
  plan dueño del consumidor de ocupación.
- La alerta «Mensajes en DLT Kafka» se apaga al descartar el topic; la de reporting se registra
  en el paso 15 tal como el plan ordena.

## Elemento existente usado como modelo

- Consumidor y DLT: `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/OcupacionConsumer.java`.
- Contrato del evento: `docs/contracts/events/reception.recepcion.v1.CheckInRegistrado.schema.json`
  y `SalidaRegistrada.schema.json`.
- Detección de degradación: `docs/especificacion/infraestructura/runbooks.md` § Alertas y umbrales.

## Alternativas descartadas

- Corregir el consumidor o el esquema en este plan: la zona de `qa` no incluye
  `services/hotel-service/**`; además cambiaría un plan cerrado.
- Ocultar el DLT borrando el topic sin conservar los mensajes: el plan prohíbe borrar sin
  evidencia.
