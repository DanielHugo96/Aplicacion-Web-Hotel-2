# Evidencia de qa-runbooks

Generado por `python scripts/qa/runbook.py evidencia`. Cada carpeta corresponde a un
simulacro de la sección 6 del plan. Los dumps de `backups/` y los logs de `_runtime/`
están excluidos por el `.gitignore` anidado y nunca se commitean.

| Simulacro | Archivos | Comando | Resultado |
|---|---|---|---|
| `alertas` | 11 | `python scripts/qa/runbook.py alerts --clean / readiness` | OK |
| `backup` | 2 | `python scripts/qa/runbook.py backup` | OK |
| `cierre` | 4 | `comandos canónicos del README raíz` | OK |
| `preflight` | 1 | `python scripts/qa/runbook.py preflight` | OK |
| `rb1-kafka` | 7 | `python scripts/qa/runbook.py rb1-kafka` | OK |
| `rb1-notificacion` | 14 | `python scripts/qa/runbook.py rb1-notification` | OK |
| `rb1-rabbit` | 7 | `python scripts/qa/runbook.py rb1-rabbit` | OK |
| `rb2-cierre` | 6 | `python scripts/qa/runbook.py rb2` | OK |
| `rb3-operacion` | 5 | `python scripts/qa/runbook.py rb3` | OK |
| `rb4-outbox` | 4 | `python scripts/qa/runbook.py rb4` | OK |
| `reset` | 1 | `python scripts/qa/runbook.py reset` | OK |
| `restauracion` | 2 | `python scripts/qa/runbook.py restore` | OK |
| `ventas-recuperacion` | 4 | `python scripts/qa/runbook.py ventas` | OK |

## Casos de la matriz

Este plan no reclama ningún caso de `06` §5; los ejecuta `qa-matriz-aceptacion`. Los fixtures compartidos se usan solo como precondición.

## Archivos por simulacro

### alertas
- `alertas/00-alertas-en-limpio-inicial.json`
- `alertas/00-alertas-en-limpio-inicial.txt`
- `alertas/15-limpieza-dlt-dlq.json`
- `alertas/16-alertas-en-limpio.json`
- `alertas/16-alertas-en-limpio.txt`
- `alertas/17-hallazgo.txt`
- `alertas/README.md`
- `alertas/alertas.json`
- `alertas/alertas.txt`
- `alertas/readiness-caida.json`
- `alertas/readiness-recuperada.json`

### backup
- `backup/01-dumps.json`
- `backup/02-git-status.txt`

### cierre
- `cierre/01-build.txt`
- `cierre/02-public-web.txt`
- `cierre/03-admin-web.txt`
- `cierre/04-compose-validadores.txt`

### preflight
- `preflight/preflight.json`

### rb1-kafka
- `rb1-kafka/01-antes.json`
- `rb1-kafka/02-mensaje-invalido.json`
- `rb1-kafka/03-dlt-despues.json`
- `rb1-kafka/04-log-consumidor.txt`
- `rb1-kafka/05-inbox-antes.json`
- `rb1-kafka/06-reenvio-mismo-eventid.json`
- `rb1-kafka/07-descarte-dlt.json`

### rb1-notificacion
- `rb1-notificacion/01-dos-estadias-cerradas.json`
- `rb1-notificacion/02-jobs-creados.json`
- `rb1-notificacion/03-timeline-reintentos.json`
- `rb1-notificacion/04-jobs-error.json`
- `rb1-notificacion/05-cli-purge-con-error.txt`
- `rb1-notificacion/06-estado-tras-purga.json`
- `rb1-notificacion/07-cli-list.txt`
- `rb1-notificacion/08-cli-retry.txt`
- `rb1-notificacion/08b-cli-retry-1ce74bf3.txt`
- `rb1-notificacion/08b-cli-retry-dfc34061.txt`
- `rb1-notificacion/09-cli-discard.txt`
- `rb1-notificacion/10-cli-purge.txt`
- `rb1-notificacion/11-recuperacion.json`
- `rb1-notificacion/12-hallazgo-dlt-hotel-ocupacion.json`

### rb1-rabbit
- `rb1-rabbit/01-fixture-f8.json`
- `rb1-rabbit/02-f8-cerrada.json`
- `rb1-rabbit/03-comando-contradiccion.json`
- `rb1-rabbit/04-comando-schema-invalido.json`
- `rb1-rabbit/05-dlq-y-sin-efecto.json`
- `rb1-rabbit/06-dlq-mensajes.json`
- `rb1-rabbit/07-descarte-dlq.json`

### rb2-cierre
- `rb2-cierre/01-fixture-f1-f2.json`
- `rb2-cierre/02-cierre-atascado.json`
- `rb2-cierre/03-alerta-cierre.json`
- `rb2-cierre/04-invariantes.json`
- `rb2-cierre/05-recuperacion.json`
- `rb2-cierre/06-rama-sales-receipt.json`

### rb3-operacion
- `rb3-operacion/01-preparando-sin-bloqueo.json`
- `rb3-operacion/02-bloqueo-persistido.json`
- `rb3-operacion/03-alerta-preparando.json`
- `rb3-operacion/04-recuperacion.json`
- `rb3-operacion/05-f6-rechazo-durable.json`

### rb4-outbox
- `rb4-outbox/01-checkin-kafka-caido.json`
- `rb4-outbox/02-outbox-por-destino.json`
- `rb4-outbox/03-alerta-schema.json`
- `rb4-outbox/04-reanudacion.json`

### reset
- `reset/reset.json`

### restauracion
- `restauracion/01-comparacion.json`
- `restauracion/02-secuencias.json`

### ventas-recuperacion
- `ventas-recuperacion/00-fixture-f7.json`
- `ventas-recuperacion/01-venta-202.json`
- `ventas-recuperacion/02-alerta-ventas.json`
- `ventas-recuperacion/03-recuperacion.json`

