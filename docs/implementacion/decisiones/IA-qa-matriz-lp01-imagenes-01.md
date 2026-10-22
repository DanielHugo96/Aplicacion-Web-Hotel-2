# IA-qa-matriz-lp01-imagenes-01 — fila `LP01` del informe y alcance de la evidencia regenerada

- **Plan:** `qa-matriz-lp01-imagenes`
- **Paso:** 5 y 6 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-22

## Qué se encontró

Dos detalles que el plan no fija y que chocan con lo ya cerrado:

1. El paso 6 pide «la fecha de la corrida» en la fila `LP01` de
   `tests/evidencia/qa-matriz-aceptacion/INFORME.md`. Ese informe **no se escribe a mano**: lo
   genera `python scripts/qa/matriz.py informe` a partir de los JSON de evidencia
   (`matriz.py:2454`), y `qa-matriz-aceptacion` §3 fija su formato como «tabla ID →
   comando/endpoint → resultado → archivo de salida», con las columnas
   `Caso | Grupo | Comando o endpoint | Invocación | Resultado | Evidencia`. Editar la fila a
   mano se pierde en la siguiente regeneración y añadir una columna rompe un formato que fija un
   plan cerrado.

2. El paso 5 manda ejecutar `python scripts/qa/matriz.py lp-crud`, y ese comando escribe la
   evidencia de **tres** casos (`LP01`, `LP02`, `CRUD01`). §2. Alcance solo cita `LP01.json`, así
   que la corrida deja cambios en dos archivos que el plan no nombra.

## Qué se decidió

1. La fecha viaja **dentro de la celda `Resultado`**, como `✅ 2026-10-22`, y la produce el
   generador del informe: `FECHA_EN_FILA = {"LP01"}` en `scripts/qa/matriz.py`. Las columnas y
   el orden del informe no cambian.
2. Se conservan `LP02.json` y `CRUD01.json` regenerados por esa misma corrida. Ambos siguen en
   `cumple: true` y su cambio es el esperado tras los seeds de presentación real (nombres e
   imágenes de sede). No se toca ninguna aserción suya.

## Por qué

Jerarquía de fuentes de [`../README.md`](../README.md) §1: norma > plan > catálogo > registro.
La norma (`docs/plans/README.md` §6.5 y §6.11) exige que el progreso y la evidencia vivan en los
artefactos que el repositorio ya tiene, y el formato del informe es decisión de un plan ya
cerrado (`qa-matriz-aceptacion`). Ante el conflicto entre «cita la fecha» y «no cambies el
formato», la opción menos invasiva es la que cumple las dos: mismo número de columnas, fecha en
la celda de resultado, y generación reproducible.

En el segundo punto, revertir `LP02.json`/`CRUD01.json` a su versión anterior dejaría evidencia
de una corrida que ya no existe y contradiría el propio comando canónico del plan. Mantener lo
que el comando produce es lo que menos se aparta de lo escrito.

## Alternativas descartadas

- **Editar `INFORME.md` a mano.** Se pierde en la siguiente `matriz.py informe`; el informe
  quedaría con una fila distinta según quién lo regenere.
- **Añadir una columna `Fecha`.** Rompe el formato que fija `qa-matriz-aceptacion` §3 y obliga a
  tocar las 41 filas de un plan cerrado.
- **Poner la fecha a los 41 casos.** El plan solo amplía `LP01`; el resto de filas no cambian y
  una fecha nueva en todas sugeriría una corrida completa que este plan no hizo.
- **Revertir `LP02.json` y `CRUD01.json` con `git checkout`.** Deja evidencia desfasada respecto
  al código actual, que es justo lo que §6.8 prohíbe («nada se marca como ejecutado por estar
  escrito»).
- **Hacer que `lp-crud` guarde solo `LP01`.** Cambia el contrato de un comando cerrado y rompe el
  informe de los otros dos casos.

## A quién afecta

- `qa-matriz-lp01-imagenes`: es su ejecución.
- `qa-matriz-aceptacion`: su informe gana la fecha en la celda de `LP01`; el formato de la tabla
  y el resto de filas quedan igual.
- Contratos: ninguno. `validar-contratos.py --base origin/main` termina sin retiradas.
