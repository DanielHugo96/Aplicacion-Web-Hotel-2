# IA-front-admin-web-reportes-01 — PDF sin compresión y verificación de su texto

- **Plan:** `front-admin-web-reportes`
- **Paso:** 11 de la sección 4. Pasos (exportación PDF/Excel)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

La exportación de PDF se compone en el frontend sin añadir dependencias: se arma un PDF 1.4 con
objetos a mano, fuentes base Helvetica/Helvetica-Bold y un stream de contenido **sin comprimir**
(no lleva `/Filter` ni `/FlateDecode`). Tres pruebas de
`reportes.export.spec.ts` buscaban el texto del archivo con `blob.text()` —que decodifica los bytes
como UTF-8— y esperaban encontrar literalmente `Página 1 de 1`.

Fallo reproducido:

```text
AssertionError: expected '%PDF-1.4\n1 0 obj\n<< /Type /Catalog …' to contain 'Página 1 de 1'
Expected: "Página 1 de 1"
Received: "%PDF-1.4..."
```

La causa no era compresión: `á` se escribe con el escape octal de WinAnsi (`\341`), que es lo
correcto en el stream, y además el pie no pasaba por `escaparPdf`, con lo que un paréntesis en el
texto del pie rompía la cadena del operador `Tj`. El stream estaba sin comprimir todo el tiempo.

## Qué se decidió

1. **Mantener el PDF sin comprimir** (sin `/Filter /FlateDecode`): es lo más simple, el archivo sigue
   siendo válido y cualquier lector lo abre; añadir Flate solo encarecería el bundle y la
   verificación.
2. **El pie de página pasa por `escaparPdf`**, igual que el título, el subtítulo, las cabeceras y
   las celdas, para que `á`, `(`, `)` y `\` se codifiquen bien. Era un defecto real del generador,
   no solo de la prueba.
3. **La prueba lee el PDF como lo que es**: bytes en latin-1, extrae las cadenas de los operadores
   `Tj` y **deshace el escape** (`\(`, `\)`, `\\` y octal `\341`) antes de comparar. No se busca
   texto literal en el binario, porque el binario no lo lleva así ni debe llevarlo.

La prueba no se debilita: sigue exigiendo encabezado, cabeceras de columna, todas las filas, el
número de página, la paginación en dos páginas y el caso de cero filas sin inventar datos.

## Por qué

La jerarquía de fuentes (`docs/implementacion/README.md` §1) no decide el formato interno de un PDF,
y la spec de reporting solo dice que el frontend exporta los resultados autorizados sin endpoints de
archivo. Entre comprimir el stream y dejarlo legible, dejarlo legible es la opción **menos
invasiva**: menos código, menos dependencias y verificable de forma determinista en cualquier TZ.

## Alternativas descartadas

- **Comprimir el stream y descomprimir en la prueba.** Obligaría a implementar inflate a mano (no
  hay dependencia de zip en el proyecto y no se pueden añadir), o a meter una dependencia solo para
  las pruebas. Mucho coste para cero beneficio visible.
- **Aflojar la prueba** (quitar la comprobación de `Página 1 de 1`, o buscar solo `Página`).
  Prohibido: el número de página es contenido que pide el plan y el criterio de terminado.
- **Buscar la cadena ya escapada en la prueba** (`P\341gina`). Funcionaría, pero deja la prueba
  atada al escape concreto y no comprueba que un lector pueda extraer el texto. La prueba ahora
  lee el archivo como lo haría un lector y lo verifica con `pypdf` en la evidencia.

## A quién afecta

Solo a `apps/admin-web/src/app/features/reportes/reportes.export.ts` y su `.spec.ts`. No toca
contratos, ni endpoints, ni otros planes. La exportación es de cliente: no hay endpoint de archivo
(`contracts/README.md` §3.6).
