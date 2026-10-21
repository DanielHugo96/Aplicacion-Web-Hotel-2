# IA-back-arreglo-sales-pago-202-01 — la recuperación separa por tipo sin romper la API del repositorio

- **Plan:** `back-arreglo-sales-pago-202`
- **Pasos:** 5 y 6 de la sección 4. Pasos (selección de operaciones PREPARANDO y guards del recuperador)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

El plan separa en `SalesRepository` la selección de `PAGO` y `ANULACION` en `PREPARANDO` de la de
`VENTA`, y en `RecuperadorVentas` enruta las dos primeras a `reintentarAdmision` con los guards de
venta ya pagada, ya cancelada y en `CANCELACION_PENDIENTE`.

Al escribir las pruebas de regresión del paso 2 aparece una contradicción práctica: si la
separación se hace con métodos públicos nuevos (`preparandoPagos()`, `preparandoAnulaciones()`),
las pruebas nuevas no compilan contra `main`, porque `main` no tiene esos métodos. El paso 2 pide
justamente lo contrario: que `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test`
*falle en esas pruebas* antes del cambio, no que falle por compilación. Una prueba que no compila
no demuestra el defecto.

Además, los guards de `reintentarAdmision` son, por construcción, difficult de alcanzar desde las
consultas: `PAGO` se selecciona con `v.estado = 'PENDIENTE'` (una venta ya `PAGADO` no se
selecciona) y `ANULACION` con `v.estado_operacion IN ('PENDIENTE','CONFIRMADA')` (ni
`CANCELACION_PENDIENTE` ni `CANCELADA` se seleccionan). Los guards solo se alcanzan en la carrera
en la que el estado de la venta cambia entre que la recuperación elige la operación y la reintenta.

## Qué se decidió

1. **Una sola entrada pública, tres consultas separadas.** `SalesRepository.preparandoVencidas(int)`
   sigue siendo el punto de entrada y devuelve la unión de tres consultas con su propio criterio:
   `preparandoVentasVencidas(int)` (VENTA por edad, 120 s, `v.estado_operacion='PENDIENTE'`),
   `preparandoPagos()` (`PAGO` por `next_attempt_at`, venta impaga) y `preparandoAnulaciones()`
   (`ANULACION` por `next_attempt_at`). No se añade ni se cambia ninguna firma pública: el
   repositorio conserva su API y las pruebas de regresión compilan contra `main` y fallan por
   aserción, que es la evidencia que pide el paso 2.
2. **Los guards se implementan y se prueban como están.** `reintentarAdmision` termina la
   operación y asegura el `recepcion.operacion-finalizar` si la venta ya está `PAGADO` (PAGO) o
   `CANCELADA` (ANULACION), y no vuelve a admitir si la anulación está en `CANCELACION_PENDIENTE`.
   La prueba los alcanza simulando la carrera: el doble de prueba cambia el estado de la venta
   entre la selección y el reintento.
3. **`PAGO` no selecciona ventas ya `PAGADO`.** Es lo que fija el paso 5 del plan y evita volver a
   cobrar. La venta ya pagada por el cierre no puede coexistir con una operación `PAGO` no
   terminal: `confirmarCierre` exige `hayOperacionAbierta` falso, así que el cierre ni siquiera
   puede empezar con esa operación viva.
4. **El `finalizar` del guard se emite una sola vez.** No hace falta una consulta de existencia
   nueva: el comando `recepcion.operacion-finalizar` se emite siempre junto con el terminal de la
   operación, así que una operación que sigue no terminal todavía no tiene comando. El guard
   termina la operación y emite el comando en la misma transacción.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 da prioridad a la norma y a la spec. La
[`04-flujos.md` §2](../../especificacion/04-flujos.md#2-venta-y-stock) fija el comportamiento
(«una operación PAGO en PREPARANDO/ADMITIDA reintenta la misma admisión y pago local; si ya pagó,
solo asegura el outbox de finalizar») y el plan fija el detalle de la consulta. Ninguno de los dos
obliga a cambiar la API pública del repositorio, y mantenerla es la opción menos invasiva: no
rompe ningún plan cerrado ni ningún contrato.

La alternativa descartada —tres métodos públicos— no aporta nada al comportamiento y rompe la
evidencia del paso 2. La otra alternativa descartada —ampliar la consulta de `PAGO` a las ventas ya
pagadas— contradice el paso 5 del plan y el sentido de la spec (no se vuelve a cobrar lo cobrado).

## Alternativas descartadas

- **Métodos públicos nuevos (`preparandoPagos`, `preparandoAnulaciones`) con el recuperador
  llamándolos.** Obliga a que las pruebas de regresión no compilen contra `main`: el paso 2
  quedaría sin evidencia y lasuite quedaría roja por compilación, no por el defecto.
- **Una sola consulta con `OR` para los tres tipos.** Mezcla la regla de la venta alta (edad,
  120 s) con la de las operaciones de pago (intento pendiente) en un predicado difícil de leer y
  de explicar; el criterio por tipo queda mejor en tres consultas.
- **Seleccionar también las ventas ya `PAGADO` y `CANCELADA` para que los guards sean
  alcanzables.** Contradice el paso 5 del plan y abre la puerta a volver a admitir una operación
  cuyo efecto ya está aplicado.

## A quién afecta

- `SalesRepository` (tres consultas de recuperación en vez de una) y `RecuperadorVentas` (guards y
  nombre del método).
- `back-sales-ventas`: sus pasos de pago y anulación quedan alineados con la spec.
- `front-admin-web-ventas`: sus pasos 19 y 20 (integración real del 202 de pago) se desbloquean.
- No afecta a `docs/contracts/**`, a `docs/especificacion/**`, a las rutas, a los estados, a los
  códigos de error ni al esquema de `sales_db`.
