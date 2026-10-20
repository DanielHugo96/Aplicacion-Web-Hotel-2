# IA-front-admin-web-recepcion-03 — `DESCONOCIDA` se muestra «Sin datos» y la admisión la decide el backend

- **Plan:** `front-admin-web-recepcion`
- **Paso:** 8 y 9 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

La spec y [`DESIGN.md` §2](../../DESIGN.md#2-paleta-y-roles) exigen que una ocupación
`DESCONOCIDA` se muestre «Sin datos» y **jamás** se traduzca a disponible o libre. El plan añade
que «la admisión se decide en el backend». En el entorno real, la proyección de ocupación de todas
las habitaciones de la sede 1 llegó `DESCONOCIDA` aunque los `room_gate` de reception existieran y
estuvieran `LIBRE`; bloquear el radio por `DESCONOCIDA` hacía imposible cualquier check-in desde la
pantalla y no permitía ejercer la decisión del backend. La spec no dice si el formulario debe
impedir seleccionar una habitación desconocida o dejar que el backend responda.

## Qué se decidió

La tabla muestra la ocupación `DESCONOCIDA` como píldora «Sin datos» y **no** la pinta como libre.
El radio sigue seleccionable. Solo se bloquean los estados conocidos como no disponibles: ocupación
`OCUPADA` o estado físico distinto de `LISTA`. Al registrar, el backend decide y su `409`
(`ROOM_NOT_SYNCED`, `ROOM_NOT_READY`, `ROOM_OCCUPIED`) se muestra junto al campo `idHabitacion` y
en el banner del catálogo.

## Por qué

Es la opción menos invasiva que respeta las dos reglas: no traduce el dato desconocido a
disponibilidad (la UI no dice «Libre») y deja la admisión donde la spec la pone, en el backend. La
alternativa de bloquear el radio convertía una carencia de proyección en un bloqueo funcional del
check-in, y además impedía comprobar el flujo real por el Gateway.

## Alternativas descartadas

- **Bloquear el radio con `DESCONOCIDA`.** La pantalla no permitía ningún check-in en el entorno
  real y el 409 del backend nunca se podía ejercer desde el panel.
- **Pintar `DESCONOCIDA` como libre.** Prohibido por la spec y por `DESIGN.md`; la etiqueta sería
  una mentira sobre el estado del gate.
- **Ocultar las habitaciones desconocidas del selector.** Equivale a inventar disponibilidad por
  omisión y el operador no podría elegir una habitación que el gate sí admite.

## A quién afecta

- `apps/admin-web`: `check-in.component.ts`, su plantilla y su spec.
- No cambia contratos ni servicios; el backend sigue siendo la autoridad de la admisión.
- Queda como precedente para futuras pantallas que lean `ocupacion.estadoEstadia`.
