# Tasks

## 1. Persistencia y procedimientos

- [x] 1.1 Migración V2 de `hotel_db` con limpieza y operacion_habitacion, sus checks e índices `uq_limpieza_pendiente`, `ix_limpieza_habitacion_estado`, `uq_operacion_vigente` e `ix_operacion_recuperacion`, tal como el DDL de referencia (paso 1 del plan). Verificación: la migración aplica sobre el esquema de F2 y pruebas SQL demuestran el rechazo de dos limpiezas PENDIENTE, de una segunda operación vigente y de un `blockId` repetido.
- [x] 1.2 Implementar `sp_limpieza_solicitar` y `sp_limpieza_completar` con la validación de tupla, la comparación con `last_cleaning_room_version`, la transición PENDIENTE → COMPLETADA y el orden de locks habitación → tarea (paso 2 del plan). Verificación: pruebas SQL de duplicado sin segunda tarea, ciclo viejo sin reemplazo, version de tarea incorrecta rechazada y limpieza completada con físico LISTA.
- [x] 1.3 Implementar `sp_habitacion_aplicar_bloqueo` y `sp_habitacion_habilitar` con el CAS PREPARANDO → APLICADA, la baja lógica, el rechazo sin reaplicación y la habilitación con estado=true y LISTA (paso 3 del plan). Verificación: pruebas SQL demuestran que LIBERADA/RECHAZADA no reaplican y que habilitar no permite reaplicar la baja.

## 2. Consumidor de limpieza

- [x] 2.1 Implementar el consumidor Rabbit de `limpieza.solicitada` con su cola durable, `limpieza.solicitada.retry.5s`, `.retry.30s`, `.retry.120s` y `limpieza.solicitada.dlq`, validación del envelope y del schema, inbox en la transacción del efecto y ack tras el commit (paso 4 del plan). Verificación: duplicado sin segunda tarea, contradicción de habitación o recepción en DLQ, schema inválido en DLQ y offset confirmado solo tras el commit.

## 3. Rutas de limpieza

- [x] 3.1 Implementar `GET /api/habitacion/limpiezas` (A/E, `idHotel` obligatorio, filtro por estado, paginación y orden estable, version de la tarea expuesta) (paso 5 del plan). Verificación: 200 con data y meta, filtro correcto, sede no asignada sin datos y orden estable.
- [x] 3.2 Implementar `PUT /api/habitacion/{id}/limpieza-completada` (A/E, V e If-Match de la tarea, Idempotency-Key, respuesta `{limpieza,habitacion}` con ETag de la tarea) (paso 6 del plan). Verificación: 200 con COMPLETADA y LISTA, 428 sin If-Match, 412 con versión distinta y misma respuesta al repetir la clave.

## 4. Rutas de operaciones

- [x] 4.1 Implementar `POST /api/habitacion/{id}/mantenimiento` con persistencia PREPARANDO, `request` JSONB inmutable, idempotencia y control de versión (paso 7 del plan). Verificación: una sola operación vigente ante concurrencia (409 `ROOM_OPERATION_IN_PROGRESS`), misma clave con la misma respuesta, 428/412 y 400 `VALIDATION_ERROR` por motivo fuera de rango.
- [x] 4.2 Implementar la llamada Feign a `POST /internal/habitaciones/{id}/bloqueos` con token máquina de scope `bloqueos:write` y la semántica 200/202/409 de mantenimiento (paso 8 del plan). Verificación: 200 con gate BLOQUEADA y CAS aplicado; 202 con `Location` y `Retry-After`; 409 con código del gate y operación RECHAZADA; reintento del mismo `blockId` idempotente.
- [x] 4.3 Implementar `DELETE /api/habitacion/eliminar/{id}` con tipo BAJA, la misma semántica, la baja lógica y la reutilización del número (paso 9 del plan). Verificación: 200 con estado=false y `blockId`, 202 con `Location`/`Retry-After`, 409 durable y número reutilizable en una habitación nueva de la sede.
- [x] 4.4 Implementar `PUT /api/habitacion/{id}/mantenimiento-completado` con validación del `blockId` de la operación MANTENIMIENTO aplicada y la emisión correlacionada (paso 10 del plan). Verificación: 200 con LISTA y operación LIBERADA, 409 con `blockId` incorrecto o no aplicado, 428/412.
- [x] 4.5 Implementar `PUT /api/habitacion/{id}/reactivar` con la operación BAJA APLICADA y padres activos (paso 11 del plan). Verificación: 200 con estado=true y version+1, 409 con padre inactivo sin habilitar, 409 con operación no aplicada.
- [x] 4.6 Implementar `GET /api/habitacion/operaciones/{blockId}` (solo A) y la prioridad de las rutas estáticas sobre la ruta de ID (paso 12 del plan). Verificación: 200 con `{blockId,idHabitacion,tipo,estado}` en cada estado, 404 con UUID inexistente y sin colisión de rutas.

## 5. Recuperación y rechazos durables

- [x] 5.1 Implementar el job de recuperación de operaciones PREPARANDO según RB3 (POST idempotente sin bloqueo, CAS con bloqueo ACTIVO, RECHAZADA con bloqueo rechazado), con `attempts`/`next_attempt_at` y sin liberar por timeout (paso 13 del plan). Verificación: con el fixture F5 el job aplica o rechaza según el bloqueo; la alerta de > 10 min es visible; no quedan mutaciones bloqueadas.
- [x] 5.2 Verificar el rechazo durable `OCC03`: mismo `blockId` rechazado sigue devolviendo 409 `GATE_NOT_AVAILABLE` tras liberarse la habitación y una nueva intención con otro `blockId` responde 200 (paso 14 del plan). Verificación: respuestas y evidencia de `bloqueo_habitacion` RECHAZADO vs ACTIVO con la operación en RECHAZADA.
- [x] 5.3 Implementar el 409 `ROOM_OPERATION_IN_PROGRESS` en las mutaciones físicas/catálogo de una habitación con operación vigente y el rechazo de cambios de categoría/piso que afecten a una habitación en PREPARANDO (paso 15 del plan). Verificación: `PUT /api/habitacion/actualizar/{id}` con operación vigente → 409; cambio de categoría no aplicado; tras liberar, las mutaciones vuelven a aplicarse.

## 6. Eventos y concurrencia

- [x] 6.1 Emitir por outbox `HabitacionLista`, `HabitacionHabilitada` y `HabitacionSnapshot` de baja con key `idHabitacion` y payload validado contra los schemas (paso 16 del plan). Verificación: una fila por evento en cada mutación, validación contra los JSON Schemas y correlación de ciclo/bloqueo conservada en el reenvío.
- [x] 6.2 Verificar la concurrencia `OCC02`: check-in y mantenimiento simultáneos sobre la habitación 102 dejan una sola admisión, gate final OCUPADA o BLOQUEADA y perdedor 409 sin efectos (paso 17 del plan). Verificación: respuestas + `room_gate` + `operacion_habitacion`.

## 7. Pruebas de los casos y cierre

- [x] 7.1 Verificar la limpieza de punta a punta `CLN01–CLN03` en su parte de hotel: ciclo correcto que completa y emite `HabitacionLista`, duplicado que no reabre, ciclo viejo que no reemplaza y correlación conservada antes/después de un check-in nuevo (paso 18 del plan). Verificación: consola Rabbit + `limpieza` + gate + proyecciones; la prueba con el productor real (F4) queda en `qa-matriz-aceptacion`.
- [x] 7.2 Cierre del plan: correr los comandos canónicos de la sección «Verificación» del `README.md` raíz y `python scripts/validar-planes.py`, y adjuntar la evidencia de `OCC02`, `OCC03` y `CLN01–CLN03` al PR (paso 19 del plan). Verificación: build y pruebas en verde, evidencia adjunta y 0 errores del validador.
