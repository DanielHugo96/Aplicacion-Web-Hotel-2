# IA-front-admin-web-reportes-02 — la exportación lleva las mismas filas, sin recortar

- **Plan:** `front-admin-web-reportes`
- **Paso:** 11 de la sección 4. Pasos (exportación PDF/Excel)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

Al verificar el paso contra el Gateway real (no con el fixture corto de la prueba), la tabla de
ventas trae nombres largos como `Producto integración 8943799E editado` (38 caracteres). El
generador repartía el ancho de página según el `ancho` declarado de cada columna y luego
**recortaba** con `...` el texto que no cabía. El PDF descargado no contenía esas filas: la columna
«Producto» decía `Producto integración 8943799E…`.

Eso incumple la verificación del paso 11 —«contiene las mismas filas y columnas de la tabla»— y el
formato obligatorio de `DESIGN.md` §9: una tabla exportada no puede perder datos.

## Qué se decidió

`medidasTabla` calcula el ancho de cada columna con el texto **más largo que va a llevar** (título y
celdas), no solo con el `ancho` declarado. Si con eso el contenido no cabe a 8 pt, baja la fuente
hasta 4 pt antes que recortar. `recortar` sigue existiendo como red de seguridad para un texto
absurdamente largo, pero con los datos reales ya no se activa.

La prueba nueva usa un nombre largo y exige que el PDF y el Excel lo contengan **completo** y que el
PDF no lleve `...`.

## Por qué

La verificación del paso manda sobre la estética del reparto de columnas: el archivo exportado es
una copia de los datos autorizados que la persona se lleva, y truncarlos silenciosamente es una
pérdida de información. Reducir la fuente es la opción menos invasiva que conserva el contenido y
no cambia el número de páginas por fila.

## Alternativas descartadas

- **Repartir todas las columnas por igual.** Arreglaba el síntoma con los datos de hoy, pero volvía
  a recortar en cuanto una columna tuviera más texto; es lo que hacía el código anterior en el caso
  extremo.
- **Partir el texto en varias líneas dentro de la celda.** Obliga a cambiar el alto de fila y la
  paginación; más código y más superficie de fallo para el mismo resultado.
- **Dejar el recorte y ajustar la prueba al texto truncado.** Debilita la prueba y contradice el
  paso: el archivo dejaría de contener las filas de la tabla.

## A quién afecta

Solo a `apps/admin-web/src/app/features/reportes/reportes.export.ts` y su `.spec.ts`. No toca
contratos ni endpoints (la exportación se genera en el navegador).
