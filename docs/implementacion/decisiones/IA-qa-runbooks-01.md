# IA-qa-runbooks-01 — DLT de Kafka para payload inválido y bootstrap de gates

- **Plan:** `qa-runbooks`
- **Paso:** 3 (RB1-Kafka) y 1 (reset/preflight)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué faltaba

El paso 3 de `qa-runbooks` pide verificar que un mensaje con envelope válido y payload que no
cumple el schema «agota los reintentos (1s, 5s, 30s, 120s y 300s)» antes de llegar a
`hotel.habitacion.v1.reception-gates-v1.dlt`. La implementación cerrada de reception
(`KafkaMessagingConfig`) declara `MessageInvalidException` como no reintentable: un payload
inválido va directo a la DLT, que es el mismo criterio que la spec fija para los errores
permanentes de Rabbit (`05-mensajeria.md` § Reintentos y DLQ Rabbit). El mensaje igualmente
conserva envelope, key y el error de validación, y no produce efecto.

Además, la spec de seeds de hotel (`hotel/base-de-datos.md` § Seeds) dice que el arranque encola
una `HabitacionCreada` por cada habitación sembrada para que reception cree los gates. Esa emisión
no existe en el servicio cerrado: con volúmenes nuevos, `room_gate` queda vacío y no se puede
hacer check-in.

## Qué se hizo

- El runner verifica el comportamiento real: la DLT de reception pasa de 0 a 1 con el envelope
  original, `room_gate` no cambia, el conteo de la alerta dispara y el topic se descarta dejando
  el mensaje como evidencia. Se anota en la evidencia y en este documento que no hubo la
  secuencia de cinco reintentos porque el servicio clasifica el error como permanente.
- `scripts/qa/common.py::bootstrap_gates` publica el mismo `HabitacionCreada` que la spec manda
  emitir al arrancar (mismo schema, `eventId` estable, key = `idHabitacion`) cuando detecta que
  falta el gate. No escribe `room_gate` por SQL ni modifica el servicio: usa el consumidor real
  `reception-gates-v1`. La alternativa (INSERT directo) está prohibida por el plan.

## Elemento existente usado como modelo

- DLT: `services/reception-service/src/main/java/pe/hotel/reception/messaging/KafkaMessagingConfig.java`
  (`DeadLetterPublishingRecoverer`, `addNotRetryableExceptions`) y el contrato
  `docs/contracts/events/hotel.habitacion.v1.HabitacionLista.schema.json`.
- Bootstrap: `docs/contracts/events/hotel.habitacion.v1.HabitacionCreada.schema.json` y
  `sp_gate_inicializar` de `services/reception-service/src/main/resources/db/migration/V4__reception_funciones.sql`.

## Opciones descartadas

- Cambiar `KafkaMessagingConfig` para reintentar cinco veces: fuera de la zona de `qa` y cambiaría
  el comportamiento de un plan cerrado.
- Dejar el paso 3 sin marcar y bloquear el plan: el resto de la verificación (DLT, envelope, sin
  efecto, alerta, inbox) sí se ejecutó; el desvío queda registrado como hallazgo para el plan dueño
  de reception.
- Insertar gates por SQL en el reset: el plan prohíbe crear fixtures por INSERT.

## Consecuencia

`Preflight` exige seis gates y el reset los obtiene publicando los eventos de bootstrap. El
hallazgo de que hotel no los emite queda anotado para su plan dueño; no se corrige aquí.
