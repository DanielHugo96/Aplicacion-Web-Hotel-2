# IA-back-reporting-proyecciones-01 — la caja del día cuenta el adelanto cobrado de toda estadía con entrada hoy

- **Plan:** `back-reporting-proyecciones`
- **Paso:** 7 y 14 de la sección 4. Pasos (proyección de estadías y dashboard)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

`fn_dashboard` suma tres términos: adelantos de check-ins de hoy, cobro final de alojamiento de
cierres de hoy y ventas `PAGADO` con `paid_at` de hoy. El primer término se calculaba filtrando
`estado_estadia <> 'CERRADA' OR fecha_cierre IS NOT NULL`, es decir, dejando fuera toda estadía
CERRADA sin fecha de cierre.

Ese filtro alcanzaba a la anulación de check-in, que el plan §3 y el paso 7 describen así:
«una anulación queda CERRADA con importes 0.00 y sin fecha de cierre, **fuera del reporte de
cobros y del cobro final de alojamiento de la caja del día**». La enumeración es explícita: la fila
anulada queda fuera de dos cosas, y de ninguna más. El adelanto de la estadía anulada (20.00
`EFECTIVO`, cobrado en el check-in) es dinero recibido que el dominio no revierte: la anulación de
[§04-flujos.md](../../especificacion/04-flujos.md) hace «CAS ACTIVA → CERRADA con archivada=true,
gate a LIBRE, evento RecepcionActualizada y auditoría, **sin receipt, cobro ni limpieza**» y
[§reception/api.md](../../especificacion/microservicios/reception/api.md) recuerda que «no se
inventa un reembolso». No existe ningún evento que devuelva ese adelanto, así que excluirlo de la
caja del día lo haría desaparecer del día en que se cobró.

## Qué se decidió

1. **El adelanto cuenta por su fecha de entrada.** `fn_dashboard` suma el adelanto de toda estadía
   con `fecha_entrada = p_dia`, sin mirar el estado de la estadía. La anulación aporta su adelanto
   cobrado y nada más.
2. **El cobro final de alojamiento sigue exigiendo fecha de cierre.** Ese término se calcula con
   `total_alojamiento - adelanto` solo sobre estadías CERRADAS **con** `fecha_cierre`, así que la
   fila anulada (que se proyecta con `total_alojamiento = 0.00` y `adelanto = 20.00`) no resta ni
   inventa un cobro: sin fecha de cierre no entra. Por eso el plan la excluye de ese término.
3. **La fila anulada sigue fuera del reporte de cobros.** `fn_reporte_cobros` filtra por
   `fecha_cierre IS NOT NULL`, así que no aparece con un recibo fabricado.

Con esta regla, el caso C de la [API de reporting](../../especificacion/microservicios/reporting/api.md#cálculo-de-ingresoshoy-casos-numéricos)
no cambia: check-in de hoy con adelanto 20.00, venta pagada de 7.00 y cierre de alojamiento de
70.00 dan `20.00 + (70.00 - 20.00) + 7.00 = 77.00`. Lo que cambia es el tratamiento de una
anulación, que antes desaparece de la caja y ahora aparece por el adelanto que sí se cobró.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 da prioridad a la norma y a la spec. La spec
define `ingresosHoy` como «adelantos cobrados hoy + cobro final de alojamiento de cierres de hoy +
ventas `PAGADO` con `paid_at` de hoy», y el modelo de eventos no tiene ningún hecho de devolución
de adelantos. El plan cierra el caso de la anulación enumerando exactamente las dos exclusiones
que la anulación cumple; excluir también el adelanto iría más allá de lo que el plan dice y
haría que un cobro real no apareciera en la caja del día.

La opción elegida es la menos invasiva que no rompe ningún plan cerrado: cambia una condición de
una función SQL propia de reporting, no toca el contrato, ni los esquemas de eventos, ni otro
servicio, ni los casos numéricos de la spec.

## Alternativas descartadas

- **Dejar el filtro `estado_estadia <> 'CERRADA' OR fecha_cierre IS NOT NULL` (implementación
  anterior).** Hace desaparecer del día el adelanto de una estadía anulada. Contradice la fórmula de
  la spec («adelantos cobrados hoy») y añade una tercera exclusión que el plan no enuncia.
- **Contar también el cobro final de alojamiento de la anulación.** Sería negativo (`0.00 - 20.00`)
  y fabricaría un cobro que el dominio no produce. Descartado por el mismo motivo que ya da la
  fecha de cierre como criterio de ese término.
- **Inventar un evento de devolución del adelanto.** Prohibited: el read model no produce eventos y
  el dominio no modela el reembolso («no se inventa un reembolso»).
- **Cambiar el `adelanto` de la fila anulada a 0.00.** La fila anulada conserva el adelanto real
  (`importes del recibo` en 0.00, adelanto intacto): fabricarlo en cero sería perder el dato del
  hecho económico y contradice «archivarla no borra importes».
