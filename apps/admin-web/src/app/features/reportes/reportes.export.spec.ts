import { describe, expect, it } from 'vitest';
import {
  ColumnaReporte,
  FilaReporte,
  generarExcel,
  generarPdf,
  matrizReporte,
} from './reportes.export';

const columnas: ColumnaReporte[] = [
  { clave: 'nombreProducto', titulo: 'Producto', ancho: 220 },
  { clave: 'cantidadTotal', titulo: 'Cantidad', numerica: true, ancho: 90 },
  { clave: 'totalIngresado', titulo: 'Total ingresado', numerica: true, ancho: 130 },
];

const filas: FilaReporte[] = [
  { nombreProducto: 'Agua 500ml', cantidadTotal: 12, totalIngresado: 'S/ 42.00' },
  { nombreProducto: 'Snack', cantidadTotal: 2, totalIngresado: 'S/ 23.00' },
];

const opciones = {
  titulo: 'Ventas por producto',
  subtitulo: 'Hotel Demo Lima · del 2026-09-01 al 2026-10-20',
  columnas,
  filas,
};

/**
 * Bytes del PDF como cadena latin-1 (lo que hay en el archivo, sin que la
 * decodificación UTF-8 destruya los caracteres WinAnsi de una sola byte).
 */
async function crudoPdf(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let texto = '';
  for (const byte of bytes) texto += String.fromCharCode(byte);
  return texto;
}

/** Deshace el escape de cadenas PDF (`\(`, `\)`, `\\` y octal `\341`). */
function desescapar(cadena: string): string {
  return cadena.replace(/\\([0-7]{1,3}|[\s\S])/g, (_, grupo: string) =>
    /^[0-7]/.test(grupo) ? String.fromCharCode(parseInt(grupo, 8)) : grupo
  );
}

/**
 * Texto que el PDF dibuja de verdad: las cadenas de los operadores `Tj`.
 * El stream de contenido va sin comprimir, así que se lee directo; lo que hay
 * que deshacer es el escape de WinAnsi («á» → `\341`), no una compresión.
 */
function textoPdf(crudo: string): string {
  const cadenas: string[] = [];
  for (const coincidencia of crudo.matchAll(/\(((?:\\[\s\S]|[^()\\])*)\)\s*Tj/g)) {
    cadenas.push(desescapar(coincidencia[1]));
  }
  return cadenas.join('\n');
}

describe('exportación en el frontend (sin endpoints de archivo)', () => {
  it('la matriz tiene el encabezado y las mismas filas y columnas que la tabla', () => {
    const matriz = matrizReporte(opciones);
    expect(matriz[0]).toEqual(['Producto', 'Cantidad', 'Total ingresado']);
    expect(matriz).toHaveLength(filas.length + 1);
    expect(matriz[1]).toEqual(['Agua 500ml', '12', 'S/ 42.00']);
    expect(matriz[2]).toEqual(['Snack', '2', 'S/ 23.00']);
  });

  it('genera un Excel (.xls) que se abre y contiene las filas y columnas', async () => {
    const blob = generarExcel(opciones);
    expect(blob.type).toBe('application/vnd.ms-excel');
    const texto = await blob.text();
    expect(texto).toContain('<?mso-application progid="Excel.Sheet"?>');
    expect(texto).toContain('<Worksheet ss:Name="Reporte">');
    for (const cabecera of ['Producto', 'Cantidad', 'Total ingresado']) {
      expect(texto).toContain(cabecera);
    }
    for (const fila of filas) {
      expect(texto).toContain(String(fila['nombreProducto']));
      expect(texto).toContain(String(fila['totalIngresado']));
    }
    // Las celdas numéricas conservan el tipo numérico del backend.
    expect(texto).toContain('<Data ss:Type="Number">12</Data>');
    expect(texto).toContain('<Data ss:Type="Number">2</Data>');
  });

  it('genera un PDF con encabezado, filas y número de página', async () => {
    const blob = generarPdf(opciones);
    expect(blob.type).toBe('application/pdf');
    const crudo = await crudoPdf(blob);
    expect(crudo.startsWith('%PDF-1.4')).toBe(true);
    expect(crudo.trimEnd().endsWith('%%EOF')).toBe(true);
    expect(crudo).toContain('/Type /Catalog');
    expect(crudo).toContain('/BaseFont /Helvetica');
    expect(crudo).toContain('0.15 g');
    // El stream va sin comprimir: se leen los operadores de texto del archivo.
    const texto = textoPdf(crudo);
    expect(texto).toContain('Ventas por producto');
    for (const cabecera of ['Producto', 'Cantidad', 'Total ingresado']) {
      expect(texto).toContain(cabecera);
    }
    for (const fila of filas) {
      expect(texto).toContain(String(fila['nombreProducto']));
      expect(texto).toContain(String(fila['totalIngresado']));
    }
    expect(texto).toContain('Página 1 de 1');
  });

  it('pagina el PDF cuando hay más filas de las que caben en una página', async () => {
    const muchas: FilaReporte[] = Array.from({ length: 80 }, (_, i) => ({
      nombreProducto: `Producto ${i + 1}`,
      cantidadTotal: i + 1,
      totalIngresado: `S/ ${(i + 1) * 2}.00`,
    }));
    const blob = generarPdf({ ...opciones, filas: muchas });
    const texto = textoPdf(await crudoPdf(blob));
    expect(texto).toContain('Página 1 de 2');
    expect(texto).toContain('Página 2 de 2');
    expect(texto).toContain('Producto 80');
  });

  it('con cero filas genera un archivo válido sin inventar datos', async () => {
    const pdfCrudo = await crudoPdf(generarPdf({ ...opciones, filas: [] }));
    const pdf = textoPdf(pdfCrudo);
    const excel = await generarExcel({ ...opciones, filas: [] }).text();
    expect(pdfCrudo).toContain('%PDF-1.4');
    expect(pdf).toContain('Página 1 de 1');
    expect(excel).toContain('Producto');
    expect(pdf).not.toContain('Agua 500ml');
  });

  it('no recorta los nombres largos: el archivo lleva las mismas filas que la tabla', async () => {
    const largo = 'Producto integración 8943799E editado';
    const conLargo: FilaReporte[] = [
      { nombreProducto: largo, cantidadTotal: 4, totalIngresado: 'S/ 46.00' },
    ];
    const pdf = textoPdf(await crudoPdf(generarPdf({ ...opciones, filas: conLargo })));
    expect(pdf).toContain(largo);
    expect(pdf).not.toContain('...');
    const excel = await generarExcel({ ...opciones, filas: conLargo }).text();
    expect(excel).toContain(largo);
  });
});
