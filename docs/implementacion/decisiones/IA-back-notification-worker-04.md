# IA-back-notification-worker-04 — El motivo del descarte vive en `last_error_code` y en `audit_log`

- **Plan:** `back-notification-worker`
- **Paso:** 9 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

La spec pide que `discard` deje el job «en estado terminal **con motivo**»
([api.md](../../especificacion/microservicios/notification/api.md) § Herramienta del operador) y que
«toda acción queda en `audit_log` (`NOTIFICATION_DISCARD`) con actor, `commandId` y resultado»
([base-de-datos.md](../../especificacion/microservicios/notification/base-de-datos.md) § Procedimientos).
El DDL de referencia de `notification_job` **no tiene** columna de motivo, y `audit_log` es la tabla
común, con `changes jsonb` cuyo esquema admite `campos`/`antes`/`despues` — sin campo libre de texto.

## Qué se decidió

El motivo se guarda en `last_error_code` con el prefijo `DESCARTADO: ` (la columna es `varchar(80)`,
así que se trunca con `left(...)`, igual que hace `sp_notificacion_resultado`), y la transición queda
en `audit_log` con `action = NOTIFICATION_DISCARD`, `resource_id = commandId` y
`changes = {"campos":["estado"],"antes":{"estado":"ERROR"},"despues":{"estado":"DESCARTADO"}}`. El
motivo completo se imprime por la CLI, que es quien lo recibe del operador.

No se añade ninguna columna al DDL de referencia: la migración sigue derivándose de él tal cual.

## Por qué

Es la opción menos invasiva que cumple las dos frases de la spec sin desviarse del DDL fijado. El
histórico se conserva (no se borra ninguna fila) y el listado del operador
(`list --estado ERROR`) ya muestra «comando, intentos y último código de error», que es lo que la
spec pide ver.

## Alternativas descartadas

- **Añadir `discard_reason varchar(200)` a `notification_job`**: el DDL de referencia es fuente única
  y configuracion.md ordena derivar los scripts de ahí; cualquier columna extra es un cambio de
  esquema que otro plan podría no esperar.
- **Meter el motivo en `audit_log.changes.despues.motivo`**: `despues` debe contener «presentes solo
  para los campos modificados» (`03-contrato-comun.md`), y `motivo` no es un campo del job.
- **Guardar el motivo solo en `audit_log.actor`**: no es un campo de texto libre y perdería el motivo
  en el listado del operador.

## A quién afecta

Solo a `notification-service` y a la CLI local. `qa-runbooks` documenta la operación de descarte; su
runbook no cambia porque la acción y su `commandId` quedan igual en `audit_log`.