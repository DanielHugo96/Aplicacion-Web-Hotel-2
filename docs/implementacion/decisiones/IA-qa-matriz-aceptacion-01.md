# IA-qa-matriz-aceptacion-01 — Cómo se inducen F4, F5, F7, OUT01, OUT05, NOT02 y NOT03

- **Plan:** `qa-matriz-aceptacion`
- **Paso:** 4, 10, 11 y 14 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

La matriz de `06` §5 exige estados que no se alcanzan con una secuencia normal de la API:

- **F4**: cierre en `CONFIRMANDO` «con Sales detenido tras aceptar».
- **F5**: operación `MANTENIMIENTO` en `PREPARANDO` «con reception detenido a mitad de admisión».
- **F7 / STK04 / STK05 / STK07**: venta con «débito incierto» o «worker caído tras
  DEBITO_SOLICITADO».
- **OUT01 / OUT05**: cierre con una admisión en vuelo y procesos matados por fase.
- **NOT02**: job `ENVIANDO` con lease vencido.
- **NOT03**: job `ENVIADA` de más de 30 días.

La spec no dice cómo producir esos estados en un entorno de demo, y el plan prohíbe crear
fixtures por `INSERT` salvo F9. Una parte de ellos **no** admite una ruta de la API: no existe
endpoint para «vencer un lease» ni para «fabricar un job de hace 40 días».

## Qué se decidió

Se inducen con mecanismos que **no escriben resultados de negocio** y que se declaran en el
campo `inducido` de cada archivo de evidencia:

1. **F4 y OUT05**: se retiene el `pg_advisory_lock(7101, idRecepcion)` que usa Sales para
   serializar la confirmación del cierre (mismo recurso que documenta `qa-runbooks` en RB2). La
   llamada va al puerto interno de reception para observar el `202` real de la fase, no el
   timeout de 5 s del Gateway.
2. **F5**: se detiene `reception-service` antes de pedir el mantenimiento. La operación queda
   `PREPARANDO` en hotel, que es exactamente el estado que el caso pide observar.
3. **F7 y STK04/STK05/STK07**: se levanta un proxy TCP delante de inventory
   (`scripts/qa/ventas.py::SelectiveProxy`) que acepta la conexión y no responde al débito. No se
   toca inventory ni se fuerza el estado de la venta; el `CANCELACION_PENDIENTE` lo produce el
   read timeout real de Feign.
4. **OUT05**: se mata `reception-service` con `SIGTERM`/`taskkill` en la fase que toca y se
   reinicia; el recuperador de 30 s completa.
5. **NOT02**: se pone el job en `ENVIANDO` con un `lease_token` propio y `lease_until` en el
   pasado, y se invoca el procedimiento real `sp_notificacion_resultado`. La fila y el CAS son
   los reales; lo inducido es la fecha del lease.
6. **NOT03**: se inserta un job `ENVIADA` con `sent_at` de hace 40 días para poder purgar sin
   esperar. Lo inducido es la antigüedad, no el resultado.

## Por qué

La norma §6.11.1 (nivel 2) manda elegir **la opción menos invasiva que no rompa ningún plan
cerrado**. Inducir el estado con el mismo recurso que ya usa el servicio (lock, timeout, proceso
detenido) es menos invasivo que escribir el estado por SQL, y deja el comportamiento de negocio
en manos del código real. Donde no existe ruta ni recurso externo (lease vencido, job antiguo) se
usa SQL **solo sobre la fila de control**, nunca sobre el resultado: el desenlace lo sigue
decidiendo el procedimiento o el worker.

## Alternativas descartadas

- **Escribir el estado final por SQL** (p. ej. `update cierre set estado='CONFIRMANDO'`):
  fabricaría el resultado que el caso quiere comprobar y la evidencia dejaría de valer.
- **Añadir endpoints o banderas de prueba a los servicios**: fuera de la zona de `qa` y
  cambiaría planes ya cerrados.
- **Esperar 30 minutos a que el lease venza solo**: alargaría la matriz sin aportar nada; el
  `lease_until` vencido produce el mismo camino de código.
- **Esperar 30 días para NOT03**: inviable.

## A quién afecta

`tests/evidencia/qa-matriz-aceptacion/` (campo `inducido` de F4, F5, STK04, STK05, STK07,
OUT01, OUT05, NOT02 y NOT03) y `scripts/qa/matriz.py`. No toca contratos ni servicios.
