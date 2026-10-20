# IA-qa-runbooks-03 — Reporting degradado persiste tras descartar la DLT

- **Plan:** `qa-runbooks`
- **Paso:** 15 (revalidación de las nueve alertas)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

Tras el simulacro RB1-Kafka, el evento inválido también fue consumido por
`reporting-proyeccion-v1` y quedó en `hotel.habitacion.v1` con `degraded=true` en
`projection_checkpoint`. La tabla de alertas de [`runbooks.md`](../../especificacion/infraestructura/runbooks.md)
§ Alertas y umbrales dice que Reporting se marca degradado ante DLT y que el operador no declara
paridad hasta reparar.

En el paso 15 se descartaron todos los topics `*.dlt` y las colas `*.dlq` dejando el contenido como
evidencia; las otras ocho alertas volvieron a cero. `projection_checkpoint.degraded` siguió en `true`
para `hotel.habitacion.v1` sin que exista ya un DLT pendiente. El plan ordena registrar ese estado
«tal cual» y anotarlo como hallazgo si queda encendido sin condición vigente.

## Qué se hizo

- Se revalidaron las nueve alertas: ocho en cero y «Reporting degradado» en 1, con la evidencia en
  `tests/evidence/qa-runbooks/alertas/16-alertas-en-limpio.json` y `17-hallazgo.txt`.
- No se tocó `services/reporting-service/**` (fuera de la zona de `qa`). No se fuerza el flag por
  SQL: la decisión de reparar la proyección es del plan dueño de reporting.
- El estado queda documentado como hallazgo para ese plan; no impide cerrar los simulacros porque
  la spec lo contempla como degradación visible hasta reparación.

## Elemento existente usado como modelo

- `docs/especificacion/infraestructura/runbooks.md` § Alertas y umbrales (fila «Reporting degradado»).
- `services/reporting-service/src/main/java/pe/hotel/reporting/` (checkpoint y meta de la API).

## Alternativas descartadas

- Limpiar `projection_checkpoint` por SQL: el plan prohíbe tocar el estado técnico sin auditoría y
  la reparación corresponde al dueño.
- Declarar la alerta apagada: sería fabricar evidencia; la consulta devuelve `degraded=true`.
