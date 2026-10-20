# Simulacros de runbooks (RB1–RB4, alertas, backup y restauración)

Datos de inyección y notas de ejecución de los simulacros del plan
[`qa-runbooks`](../../../docs/plans/qa-runbooks.md). El runner vive en
[`scripts/qa/runbook.py`](../../../scripts/qa/runbook.py) y escribe la evidencia en
[`tests/evidence/qa-runbooks/`](../../evidence/qa-runbooks/).

## Prerequisitos

- `.env` completo en la raíz (credenciales locales; nunca se commitea).
- Compose con PostgreSQL 18.6, Kafka 4.3.1, RabbitMQ 4.3.6 y Mailpit sanos:
  `docker compose -f infra/compose.yaml --env-file .env up -d --wait`.
- Jars de los nueve servicios construidos:
  `bash scripts/mvn.sh -f services/pom.xml -DskipTests package`.
- `python scripts/qa/runbook.py preflight` en verde. Comprueba Compose, seeds/gates,
  `reporting LISTO` y `python scripts/validar-planes.py`.

## Reseteo de la demo

`python scripts/qa/runbook.py reset --build` detiene los servicios locales, recrea los
volúmenes del Compose (`down -v` + `up -d --wait`) y arranca los servicios en el orden de
[`configuracion.md`](../../../docs/especificacion/infraestructura/configuracion.md). Los seeds
de dev vuelven a dejar 3 sedes, 6 habitaciones, 6 personas, 9 productos-sede y 6 gates.

## Ejecución

```bash
python scripts/qa/runbook.py preflight
python scripts/qa/runbook.py alerts --clean
python scripts/qa/runbook.py rb1-kafka
python scripts/qa/runbook.py rb1-rabbit
python scripts/qa/runbook.py rb1-notification
python scripts/qa/runbook.py rb2
python scripts/qa/runbook.py rb3
python scripts/qa/runbook.py rb4
python scripts/qa/runbook.py ventas
python scripts/qa/runbook.py readiness
python scripts/qa/runbook.py backup
python scripts/qa/runbook.py restore
python scripts/qa/runbook.py evidencia
```

Los pasos se ejecutan en el orden del plan. La suite completa usa tiempos reales: RB1-Kafka
espera la secuencia de reintentos de 1s, 5s, 30s, 120s y 300s antes de la DLT; RB1-Notification
espera 1min, 5min y 30min hasta ERROR; RB2, RB3, RB4 y ventas esperan sus umbrales de 5, 10, 15
y 30 minutos. Duración orientativa: dos horas y media.

## Datos de inyección

- [`hotel.habitacion.v1.invalid.json`](hotel.habitacion.v1.invalid.json): envelope válido y
  payload que no cumple `HabitacionLista` (faltan `cleaningCycleId` y `cleaningRoomVersion`).
- [`hotel.habitacion.v1.valid.json`](hotel.habitacion.v1.valid.json): `HabitacionLista` válido
  con un `cleaningCycleId` sin ciclo asociado; sirve para comprobar que el inbox no duplica el
  efecto al reenviar el mismo `eventId` y la misma key.
- [`limpieza.solicitada.contradiccion.json`](limpieza.solicitada.contradiccion.json): comando
  con el `cleaningCycleId` de otra habitación (contradicción permanente).
- [`limpieza.solicitada.invalido.json`](limpieza.solicitada.invalido.json): payload que no
  cumple el schema (UUID inválido y falta `roomVersion`).

El runner sustituye los UUID por valores nuevos en cada corrida para que el inbox/DLQ no
confunda una ejecución con otra. Los ficheros son la referencia legible del simulacro.
