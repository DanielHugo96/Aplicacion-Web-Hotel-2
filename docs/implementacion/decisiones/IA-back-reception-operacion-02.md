# IA-back-reception-operacion-02 — Fixtures de las mitades de OCC02, OCC03 y CLN01

- **Plan:** `back-reception-operacion`
- **Pasos:** 11, 14 y 16 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

OCC02 y OCC03 necesitan una mitad de hotel (`POST /api/habitacion/{id}/mantenimiento` y el cierre
que libera el gate) y CLN01 necesita el ciclo de limpieza de hotel. Esas rutas y el cierre están
fuera de este plan: pertenecen a `back-hotel-operaciones` y `back-reception-cierre`, que todavía no
están implementados. La especificación sí fija que la autoridad de admisión es `room_gate`, no la
proyección de hotel.

## Qué se decidió

La mitad de reception se prueba con las rutas que sí le pertenecen y con fixtures controlados:

- OCC02: el competidor del check-in es `POST /internal/habitaciones/{id}/bloqueos`, que es
  exactamente la llamada que hotel hará en su flujo de mantenimiento.
- OCC03: el rechazo durable se prueba en el gate OCUPADO; para liberar el gate se usa una fixture
  SQL que deja la estadía CERRADA y el gate LIBRE, equivalente al efecto de `back-reception-cierre`.
- CLN01: el gate se deja en `LIMPIEZA_PENDIENTE` con un `cleaningCycleId` conocido y se publican
  `HabitacionLista` reales por Kafka (ciclo viejo y ciclo vigente).

## Por qué

No se inventa comportamiento de negocio ni se implementan rutas de otro plan. Las fixtures solo
preparan el estado que produciría el plan vecino, y el comportamiento que se verifica (lock del
gate, decisión durable, correlación de ciclo) es el que el contrato de reception ya fija.

## Alternativas descartadas

- **Implementar mantenimiento o cierre dentro de este plan.** Rompe la zona de escritura y duplica
  planes ya catalogados.
- **No probar OCC02/OCC03/CLN01.** El criterio de terminado exige su evidencia y la mitad de
  reception es verificable sin las rutas de hotel.

## A quién afecta

Solo a la evidencia de este plan. `back-hotel-operaciones` y `back-reception-cierre` probarán sus
mitades con sus propias rutas; `qa-matriz-aceptacion` ejecutará los casos completos.
